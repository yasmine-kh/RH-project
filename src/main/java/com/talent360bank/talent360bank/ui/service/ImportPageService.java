package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RecalculEnCoursException;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CampagneService;
import com.talent360bank.talent360bank.service.ImportQuestionnaireService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import com.talent360bank.talent360bank.service.resultat.ResultatImportQuestionnaire;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import com.talent360bank.talent360bank.ui.model.ImportPageView;
import com.talent360bank.talent360bank.ui.model.ImportPageView.Calcul;
import com.talent360bank.talent360bank.ui.model.ImportPageView.Formulaire;
import com.talent360bank.talent360bank.ui.model.ImportPageView.LigneJournal;
import com.talent360bank.talent360bank.ui.model.ImportPageView.Rapport;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Page d'import du classeur (/import).
 *
 * <p>REGLE : aucune logique d'import ici. L'envoi appelle
 * {@link CampagneService#importer} exactement comme POST /api/imports (import,
 * puis calcul complet si demande) ; la page ne fait que controler le
 * formulaire avant, et mettre le bilan en forme apres.
 *
 * <p>Ajouts propres a la page :
 * <ul>
 *   <li>seuls les fichiers .xlsx sont envoyes a l'import ;</li>
 *   <li>tout l'envoi (import et calcul) tient le verrou du trimestre
 *   ({@link VerrouCalculTrimestre}) : un second envoi, ou un recalcul lance
 *   ailleurs pendant ce temps, est refuse avec un message au lieu de tourner
 *   en parallele ;</li>
 *   <li>la date de reference saisie est posee sur le trimestre apres un import
 *   reussi ({@link TrimestreService#modifierDateReference}, qui ne demande
 *   aucun recalcul).</li>
 * </ul>
 */
@Service
public class ImportPageService {

    /** Entrees du journal affichees sous le formulaire. */
    public static final int JOURNAL_AFFICHE = 20;

    private static final String EXTENSION = ".xlsx";

    private final CampagneService campagneService;
    private final ImportService importService;
    private final TrimestreService trimestreService;
    private final TrimestreRepository trimestreRepository;
    private final VerrouCalculTrimestre verrou;
    private final ImportQuestionnaireService importQuestionnaireService;
    private final TrimestreCourantService trimestreCourant;
    private final DataSize tailleMax;

    public ImportPageService(CampagneService campagneService, ImportService importService,
                             TrimestreService trimestreService, TrimestreRepository trimestreRepository,
                             VerrouCalculTrimestre verrou, ImportQuestionnaireService importQuestionnaireService,
                             TrimestreCourantService trimestreCourant, MultipartProperties multipart) {
        this.importQuestionnaireService = importQuestionnaireService;
        this.trimestreCourant = trimestreCourant;
        this.campagneService = campagneService;
        this.importService = importService;
        this.trimestreService = trimestreService;
        this.trimestreRepository = trimestreRepository;
        this.verrou = verrou;
        this.tailleMax = multipart.getMaxFileSize();
    }

    /** Page vide : formulaire prerempli, journal. */
    public ImportPageView page() {
        return vue(formulaireParDefaut(), null, null);
    }

    /** Page apres un envoi refuse avant l'import (fichier trop volumineux...). */
    public ImportPageView pageAvecErreur(String erreur) {
        return vue(formulaireParDefaut(), null, erreur);
    }

    /**
     * Le trimestre qui suit le plus recent existant (T4 → T1 de l'annee
     * suivante) ; sans aucun trimestre, celui d'aujourd'hui. Simulation
     * decochee, calcul coche, date vide (= fin du trimestre).
     */
    public Formulaire formulaireParDefaut() {
        Optional<Trimestre> dernier = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        int annee;
        int numero;
        if (dernier.isPresent()) {
            annee = dernier.get().getNumero() == 4 ? dernier.get().getAnnee() + 1 : dernier.get().getAnnee();
            numero = dernier.get().getNumero() == 4 ? 1 : dernier.get().getNumero() + 1;
        } else {
            LocalDate aujourdhui = LocalDate.now();
            annee = aujourdhui.getYear();
            numero = (aujourdhui.getMonthValue() - 1) / 3 + 1;
        }
        return new Formulaire(String.valueOf(annee), String.valueOf(numero), false, true, "");
    }

    /**
     * Envoi du formulaire. Les refus avant l'import (pas de fichier, pas un
     * .xlsx, trimestre ou date invalides, calcul deja en cours) reviennent dans
     * {@link ImportPageView#erreur()} sans rien appeler ; sinon le bilan de
     * l'import, quel que soit son statut, est dans {@link ImportPageView#rapport()}.
     */
    public ImportPageView importer(MultipartFile fichier, Formulaire formulaire) {
        if (fichier == null || fichier.isEmpty()) {
            return vue(formulaire, null, "Choisir le classeur à importer (fichier .xlsx).");
        }
        String nom = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename();
        if (!nom.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return vue(formulaire, null, "Seuls les classeurs Excel .xlsx sont acceptés : « " + nom
                    + " » n'a pas été importé.");
        }
        if (tailleMax.toBytes() >= 0 && fichier.getSize() > tailleMax.toBytes()) {
            return vue(formulaire, null, messageTailleMax());
        }
        int annee;
        int numero;
        try {
            annee = Integer.parseInt(formulaire.annee().trim());
            numero = Integer.parseInt(formulaire.numero().trim());
            TrimestreService.verifier(annee, numero);
        } catch (RuntimeException e) {
            return vue(formulaire, null, "Trimestre invalide : année entre " + TrimestreService.ANNEE_MIN + " et "
                    + TrimestreService.ANNEE_MAX + ", trimestre de 1 à 4.");
        }
        LocalDate dateReference;
        try {
            dateReference = formulaire.dateReference() == null || formulaire.dateReference().isBlank()
                    ? null : LocalDate.parse(formulaire.dateReference().trim());
        } catch (DateTimeParseException e) {
            return vue(formulaire, null, "Date de référence invalide : choisir une date dans le calendrier, "
                    + "ou laisser vide pour la fin du trimestre.");
        }

        boolean existait = trimestreRepository.findByNumeroAndAnnee(numero, annee).isPresent();
        Trimestre cle = new Trimestre();
        cle.setAnnee(annee);
        cle.setNumero(numero);
        ResultatCampagne campagne;
        try {
            campagne = verrou.executer(cle, () -> {
                ResultatCampagne resultat = campagneService.importer(fichier, annee, numero,
                        formulaire.simulation(), formulaire.calcul());
                if (dateReference != null && !formulaire.simulation()
                        && resultat.importation().statut() != StatutImport.ECHEC) {
                    trimestreService.modifierDateReference(
                            trimestreRepository.findByNumeroAndAnnee(numero, annee).orElseThrow(), dateReference);
                }
                return resultat;
            });
        } catch (RecalculEnCoursException e) {
            return vue(formulaire, null, "Un calcul est déjà en cours pour T" + numero + " " + annee
                    + " : rien n'a été importé. Réessayer une fois qu'il est terminé.");
        }
        boolean trimestreVide = !existait && !formulaire.simulation()
                && campagne.importation().statut() == StatutImport.ECHEC
                && trimestreRepository.findByNumeroAndAnnee(numero, annee).isPresent();
        return vue(formulaire, rapport(campagne, dateReference, trimestreVide), null);
    }

    /**
     * Envoi du formulaire "Reponses au questionnaire d'engagement".
     *
     * @param rapport  bilan de l'import, null si l'envoi a ete refuse avant
     * @param erreur   envoi refuse (pas de fichier, pas un .xlsx, trimestre inconnu), null sinon
     */
    public record EnvoiQuestionnaire(ResultatImportQuestionnaire rapport, String erreur) {
    }

    /**
     * Import des reponses brutes au questionnaire ({@link ImportQuestionnaireService})
     * pour un trimestre deja ouvert : les collaborateurs doivent exister. Rien d'autre
     * n'est modifie (ni le score d'engagement, ni les calculs).
     *
     * @param trimestre "AAAA-N" choisi dans la liste des trimestres
     */
    public EnvoiQuestionnaire importerQuestionnaire(MultipartFile fichier, String trimestre) {
        if (fichier == null || fichier.isEmpty()) {
            return new EnvoiQuestionnaire(null, "Choisir le fichier de réponses à importer (fichier .xlsx).");
        }
        String nom = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename();
        if (!nom.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return new EnvoiQuestionnaire(null, "Seuls les classeurs Excel .xlsx sont acceptés : « " + nom
                    + " » n'a pas été importé.");
        }
        if (tailleMax.toBytes() >= 0 && fichier.getSize() > tailleMax.toBytes()) {
            return new EnvoiQuestionnaire(null, messageTailleMax());
        }
        Optional<Trimestre> choisi = trimestreCourant.lister().stream()
                .filter(option -> option.valeur().equals(trimestre == null ? "" : trimestre.trim()))
                .findFirst()
                .flatMap(option -> trimestreRepository.findByNumeroAndAnnee(option.numero(), option.annee()));
        if (choisi.isEmpty()) {
            return new EnvoiQuestionnaire(null, "Choisir un trimestre déjà importé : les réponses sont rattachées "
                    + "aux collaborateurs du classeur.");
        }
        try (InputStream contenu = fichier.getInputStream()) {
            return new EnvoiQuestionnaire(importQuestionnaireService.importer(contenu, nom, choisi.get()), null);
        } catch (IOException e) {
            return new EnvoiQuestionnaire(null, "Fichier illisible : rien n'a été importé.");
        }
    }

    public String messageTailleMax() {
        return "Fichier trop volumineux : " + libelle(tailleMax) + " au maximum. Rien n'a été importé.";
    }

    // --------------------------------------------------------------------------------

    private ImportPageView vue(Formulaire formulaire, Rapport rapport, String erreur) {
        return new ImportPageView(formulaire, rapport, erreur, journal(), tailleMax.toBytes(), libelle(tailleMax));
    }

    private List<LigneJournal> journal() {
        return importService.journal().stream()
                .limit(JOURNAL_AFFICHE)
                .map(ImportPageService::ligne)
                .toList();
    }

    private static LigneJournal ligne(ImportExcel journal) {
        Trimestre trimestre = journal.getTrimestre();
        return new LigneJournal(journal.getIdImport(), journal.getDateImport(), journal.getNomFichier(),
                trimestre == null ? null : TrimestreCourantService.libelle(trimestre), journal.getStatut(),
                journal.getNbLignes(), journal.getNbErreurs(),
                journal.getUtilisateur() == null ? null : journal.getUtilisateur().getLogin(), journal.getMessage());
    }

    private static Rapport rapport(ResultatCampagne campagne, LocalDate dateReference, boolean trimestreVide) {
        ResultatImport importation = campagne.importation();
        String valeur = importation.annee() + "-" + importation.numero();
        boolean ecrit = !importation.simulation() && importation.statut() != StatutImport.ECHEC;
        return new Rapport(importation.nomFichier(), "T" + importation.numero() + " " + importation.annee(),
                valeur, importation.simulation(), importation.statut().name(), importation.nbLignes(),
                importation.nbErreurs(), importation.feuilles(), importation.erreurs(), importation.desactives(),
                importation.reactives(), importation.message(), calcul(campagne.calcul()), campagne.erreurCalcul(),
                ecrit ? dateReference : null, trimestreVide,
                ecrit && campagne.calcul() != null ? "/?trimestre=" + valeur : null,
                importation.decisionsConservees());
    }

    private static Calcul calcul(ResultatCalculTrimestre calcul) {
        if (calcul == null) {
            return null;
        }
        ResultatRecalcul scores = calcul.scores();
        ResultatRecalcul placements = calcul.placements();
        return new Calcul(scores.nombreCalcules(), scores.nombreIgnores(), placements.nombreCalcules(),
                placements.nombreIgnores(), calcul.vivierReleve().crees().size()
                + calcul.vivierReleve().dejaPresents().size(),
                scores.ignores().stream().map(ignore -> ignore.matricule() + " : " + ignore.motif()).toList());
    }

    private static String libelle(DataSize taille) {
        if (taille.toBytes() < 0) {
            return "sans limite";
        }
        if (taille.toMegabytes() >= 1 && taille.toBytes() % DataSize.ofMegabytes(1).toBytes() == 0) {
            return taille.toMegabytes() + " Mo";
        }
        if (taille.toKilobytes() >= 1) {
            return taille.toKilobytes() + " Ko";
        }
        return taille.toBytes() + " octets";
    }
}
