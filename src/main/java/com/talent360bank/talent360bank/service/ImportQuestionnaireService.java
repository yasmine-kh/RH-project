package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.ReponseQuestionnaire;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.excel.Cellules;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.ReponseQuestionnaireRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatImportQuestionnaire;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Import des reponses brutes au questionnaire d'engagement : le fichier de
 * reponses du formulaire (une ligne par collaborateur, une colonne par
 * question), comme docs/data/"TALENT 360 BANK — Questionnaire d'engagement
 * collaborateur (réponses)". Chaque reponse est enregistree telle quelle
 * ({@link ReponseQuestionnaire}) pour le trimestre choisi. Aucun score n'en est
 * calcule, et l'import du classeur (score d'engagement de 12_VIGILANCE) ne
 * change pas.
 *
 * <p>Format lu (premiere feuille) :
 * <ul>
 *   <li>en-tete : la premiere des 10 premieres lignes qui a une colonne "Matricule" ;</li>
 *   <li>"Horodateur" (facultatif) : date de la reponse ;</li>
 *   <li>toute autre colonne d'en-tete non vide est une question, codee Q01, Q02...
 *   dans l'ordre des colonnes, son texte garde tel qu'ecrit (espaces de debut et de
 *   fin retires). Aucun theme n'est deduit : la colonne theme reste vide tant que le
 *   fichier ne la fournit pas ;</li>
 *   <li>seules les cellules remplies sont enregistrees (les branches du formulaire
 *   laissent des colonnes vides).</li>
 * </ul>
 * Une ligne sans matricule ou d'un matricule inconnu est ecartee. Deux lignes du
 * meme matricule : la plus recente (Horodateur, sinon la plus basse) est gardee.
 * Reimporter un fichier remplace les reponses de ses collaborateurs pour ce trimestre.
 */
@Service
public class ImportQuestionnaireService {

    /** Valeur de ImportExcel.source pour un fichier de reponses au questionnaire. */
    public static final String SOURCE = "QUESTIONNAIRE_ENGAGEMENT";

    private static final int LIGNES_MAX_AVANT_ENTETE = 10;
    private static final int LONGUEUR_MAX_QUESTION = 500;
    private static final int LONGUEUR_MAX_REPONSE = 2000;
    private static final int LONGUEUR_MAX_NOM_FICHIER = 255;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<DateTimeFormatter> FORMATS_HORODATEUR = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private final CollaborateurRepository collaborateurRepository;
    private final ReponseQuestionnaireRepository reponseRepository;
    private final ImportExcelRepository importExcelRepository;

    private final com.talent360bank.talent360bank.securite.UtilisateurCourant utilisateurCourant;

    public ImportQuestionnaireService(CollaborateurRepository collaborateurRepository,
                                      ReponseQuestionnaireRepository reponseRepository,
                                      ImportExcelRepository importExcelRepository,
                                      com.talent360bank.talent360bank.securite.UtilisateurCourant utilisateurCourant) {
        this.utilisateurCourant = utilisateurCourant;
        this.collaborateurRepository = collaborateurRepository;
        this.reponseRepository = reponseRepository;
        this.importExcelRepository = importExcelRepository;
    }

    /** Une question du fichier : sa colonne, son code et son texte. */
    record Question(int colonne, String code, int ordre, String texte) {
    }

    /** Une ligne du fichier, deja lue. */
    record Ligne(int numero, String matricule, LocalDateTime dateReponse, Map<Question, String> reponses) {
    }

    /** Le fichier lu, avant toute ecriture. */
    record Lecture(List<Question> questions, List<Ligne> lignes) {
    }

    /** Fichier sans en-tete reconnaissable. */
    static class FormatInvalideException extends RuntimeException {
        FormatInvalideException(String message) {
            super(message);
        }
    }

    /**
     * Lit le fichier et enregistre les reponses pour le trimestre ; le bilan est
     * journalise (ImportExcel, source {@link #SOURCE}).
     */
    @Transactional
    public ResultatImportQuestionnaire importer(InputStream contenu, String nomFichier, Trimestre trimestre) {
        String nom = nomFichier(nomFichier);
        Lecture lecture;
        try (Workbook classeur = WorkbookFactory.create(contenu)) {
            lecture = lire(classeur);
        } catch (FormatInvalideException e) {
            return echec(nom, trimestre, e.getMessage());
        } catch (IOException | RuntimeException e) {
            return echec(nom, trimestre, "Fichier illisible : ce n'est pas un classeur Excel .xlsx valide.");
        }

        List<String> erreurs = new ArrayList<>();
        Map<String, Ligne> parMatricule = new LinkedHashMap<>();
        for (Ligne ligne : lecture.lignes()) {
            if (ligne.matricule() == null) {
                erreurs.add("Ligne " + ligne.numero() + " : matricule absent, ligne écartée.");
                continue;
            }
            Ligne precedente = parMatricule.get(ligne.matricule());
            if (precedente == null) {
                parMatricule.put(ligne.matricule(), ligne);
                continue;
            }
            Ligne gardee = plusRecente(precedente, ligne);
            Ligne ecartee = gardee == ligne ? precedente : ligne;
            erreurs.add("Ligne " + ecartee.numero() + " : " + ligne.matricule() + " a aussi répondu ligne "
                    + gardee.numero() + ", seule la réponse la plus récente est gardée.");
            parMatricule.put(ligne.matricule(), gardee);
        }

        Map<String, Collaborateur> collaborateurs = collaborateurRepository.findAllById(parMatricule.keySet())
                .stream().collect(Collectors.toMap(Collaborateur::getIdCollaborateur, Function.identity()));
        List<ReponseQuestionnaire> reponses = new ArrayList<>();
        List<String> importes = new ArrayList<>();
        for (Ligne ligne : parMatricule.values()) {
            Collaborateur collaborateur = collaborateurs.get(ligne.matricule());
            if (collaborateur == null) {
                erreurs.add("Ligne " + ligne.numero() + " : aucun collaborateur " + ligne.matricule()
                        + ", ligne écartée.");
                continue;
            }
            importes.add(collaborateur.getIdCollaborateur());
            ligne.reponses().forEach((question, reponse) -> reponses.add(new ReponseQuestionnaire(collaborateur,
                    trimestre, question.code(), question.ordre(), null, question.texte(), reponse,
                    ligne.dateReponse())));
        }

        if (!importes.isEmpty()) {
            reponseRepository.supprimerPour(trimestre, importes);
            reponseRepository.flush();
            reponseRepository.saveAll(reponses);
        }
        StatutImport statut = importes.isEmpty() ? StatutImport.ECHEC
                : erreurs.isEmpty() ? StatutImport.SUCCES : StatutImport.PARTIEL;
        String message = importes.isEmpty()
                ? "Aucune réponse enregistrée : aucune ligne n'a un matricule connu." : null;
        journaliser(nom, trimestre, statut, reponses.size(), erreurs.size(), message);
        return new ResultatImportQuestionnaire(nom, libelle(trimestre), valeur(trimestre), statut.name(),
                importes.size(), reponses.size(), lecture.questions().size(), List.copyOf(erreurs), message);
    }

    // --- lecture du fichier ---------------------------------------------------------------

    static Lecture lire(Workbook classeur) {
        if (classeur.getNumberOfSheets() == 0) {
            throw new FormatInvalideException("Le fichier ne contient aucune feuille.");
        }
        Sheet feuille = classeur.getSheetAt(0);
        int entete = -1;
        int colonneMatricule = -1;
        int derniere = Math.min(feuille.getLastRowNum(), LIGNES_MAX_AVANT_ENTETE);
        for (int i = Math.max(0, feuille.getFirstRowNum()); i <= derniere && entete < 0; i++) {
            Row ligne = feuille.getRow(i);
            if (ligne == null) {
                continue;
            }
            for (Cell cellule : ligne) {
                if ("matricule".equals(normaliser(Cellules.texte(ligne, cellule.getColumnIndex())))) {
                    entete = i;
                    colonneMatricule = cellule.getColumnIndex();
                    break;
                }
            }
        }
        if (entete < 0) {
            throw new FormatInvalideException("Colonne « Matricule » introuvable dans les "
                    + LIGNES_MAX_AVANT_ENTETE + " premières lignes de la feuille « " + feuille.getSheetName()
                    + " » : ce n'est pas un fichier de réponses au questionnaire.");
        }

        Row enTete = feuille.getRow(entete);
        int colonneHorodateur = -1;
        List<Question> questions = new ArrayList<>();
        for (Cell cellule : enTete) {
            int colonne = cellule.getColumnIndex();
            String texte = Cellules.texte(enTete, colonne);
            if (colonne == colonneMatricule || texte == null) {
                continue;
            }
            String normalise = normaliser(texte);
            if (colonneHorodateur < 0 && (normalise.equals("horodateur") || normalise.equals("horodatage")
                    || normalise.equals("timestamp"))) {
                colonneHorodateur = colonne;
                continue;
            }
            String libelle = tronquer(libelleQuestion(texte), LONGUEUR_MAX_QUESTION);
            int ordre = questions.size() + 1;
            questions.add(new Question(colonne, String.format(Locale.ROOT, "Q%02d", ordre), ordre, libelle));
        }

        List<Ligne> lignes = new ArrayList<>();
        for (int i = entete + 1; i <= feuille.getLastRowNum(); i++) {
            Row ligne = feuille.getRow(i);
            if (ligne == null) {
                continue;
            }
            Map<Question, String> reponses = new LinkedHashMap<>();
            for (Question question : questions) {
                String reponse = valeur(ligne.getCell(question.colonne()));
                if (reponse != null) {
                    reponses.put(question, tronquer(reponse, LONGUEUR_MAX_REPONSE));
                }
            }
            String matricule = Cellules.texte(ligne, colonneMatricule);
            if (matricule == null && reponses.isEmpty()) {
                continue;
            }
            lignes.add(new Ligne(i + 1, matricule == null ? null : matricule.toUpperCase(Locale.ROOT),
                    colonneHorodateur < 0 ? null : horodateur(ligne.getCell(colonneHorodateur)), reponses));
        }
        return new Lecture(List.copyOf(questions), lignes);
    }

    /** Libelle de la question tel qu'ecrit dans l'en-tete, sans les espaces de debut et de fin. */
    static String libelleQuestion(String entete) {
        return entete.strip();
    }

    private static Ligne plusRecente(Ligne a, Ligne b) {
        if (a.dateReponse() != null && b.dateReponse() != null) {
            return b.dateReponse().isBefore(a.dateReponse()) ? a : b;
        }
        return b;
    }

    /** Texte de la reponse ; une date est rendue jj/mm/aaaa. */
    private static String valeur(Cell cellule) {
        if (cellule == null) {
            return null;
        }
        CellType type = cellule.getCellType() == CellType.FORMULA ? cellule.getCachedFormulaResultType()
                : cellule.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cellule)) {
            return DateUtil.getLocalDateTime(cellule.getNumericCellValue()).toLocalDate().format(DATE);
        }
        return Cellules.texte(cellule.getRow(), cellule.getColumnIndex());
    }

    private static LocalDateTime horodateur(Cell cellule) {
        if (cellule == null) {
            return null;
        }
        if (cellule.getCellType() == CellType.NUMERIC) {
            return DateUtil.getLocalDateTime(cellule.getNumericCellValue());
        }
        String texte = Cellules.texte(cellule.getRow(), cellule.getColumnIndex());
        if (texte == null) {
            return null;
        }
        for (DateTimeFormatter format : FORMATS_HORODATEUR) {
            try {
                return LocalDateTime.parse(texte, format);
            } catch (DateTimeParseException e) {
                // format suivant
            }
        }
        try {
            return LocalDate.parse(texte, DATE).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('’', '\'').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    // --- bilan ------------------------------------------------------------------------------

    private ResultatImportQuestionnaire echec(String nom, Trimestre trimestre, String message) {
        journaliser(nom, trimestre, StatutImport.ECHEC, 0, 0, message);
        return new ResultatImportQuestionnaire(nom, libelle(trimestre), valeur(trimestre),
                StatutImport.ECHEC.name(), 0, 0, 0, List.of(), message);
    }

    private void journaliser(String nom, Trimestre trimestre, StatutImport statut, int nbLignes, int nbErreurs,
                             String message) {
        ImportExcel journal = new ImportExcel();
        journal.setSource(SOURCE);
        journal.setUtilisateur(utilisateurCourant.utilisateur().orElse(null));
        journal.setNomFichier(nom);
        journal.setDateImport(LocalDate.now());
        journal.setStatut(statut.name());
        journal.setTrimestre(trimestre);
        journal.setNbLignes(nbLignes);
        journal.setNbErreurs(nbErreurs);
        journal.setMessage(message);
        importExcelRepository.save(journal);
    }

    /** Nom sans chemin : certains navigateurs envoient le chemin complet du poste. */
    private static String nomFichier(String nom) {
        if (nom == null || nom.isBlank()) {
            return "questionnaire.xlsx";
        }
        String sansChemin = nom.substring(Math.max(nom.lastIndexOf('/'), nom.lastIndexOf('\\')) + 1);
        return tronquer(sansChemin.isBlank() ? "questionnaire.xlsx" : sansChemin, LONGUEUR_MAX_NOM_FICHIER);
    }

    private static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }

    private static String valeur(Trimestre trimestre) {
        return trimestre.getAnnee() + "-" + trimestre.getNumero();
    }

    private static String tronquer(String texte, int longueurMax) {
        return texte.length() <= longueurMax ? texte : texte.substring(0, longueurMax);
    }
}
