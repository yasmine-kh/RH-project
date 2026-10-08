package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Import du classeur TALENT_360_BANK_Dataset depuis l'application.
 *
 * <p>Deroule d'un import : lecture du fichier, controles qui refusent le
 * fichier entier avant toute ecriture (format : feuilles et en-tetes, voir
 * {@link FormatClasseur} ; periode annoncee par le classeur, voir
 * {@link PeriodeClasseur}), ouverture du trimestre (cree s'il n'existe pas,
 * voir {@link TrimestreService}), ecriture des feuilles dans une seule
 * transaction ({@link ImportClasseurService}), puis journalisation dans
 * {@link ImportExcel}. Le journal est ecrit hors de la transaction des
 * donnees : un import qui echoue et n'ecrit rien laisse tout de meme sa trace.
 *
 * <p>En simulation, tout se deroule de la meme facon dans une transaction
 * annulee a la fin : memes controles, memes compteurs, rien n'est enregistre,
 * pas meme le journal ni le trimestre.
 */
@Service
public class ImportService {

    /**
     * Message d'une erreur inattendue (import ou simulation) : simple pour le RH ; le detail
     * technique (exception) va seulement au journal de l'application.
     */
    public static final String MESSAGE_ECHEC = "L'import a échoué. Vérifiez le fichier et réessayez.";

    /** Fichier qui n'est pas un classeur Excel lisible ; la cause technique va au journal. */
    public static final String MESSAGE_ILLISIBLE = "Fichier illisible : un classeur Excel .xlsx est attendu. "
            + "Vérifiez le fichier et réessayez.";

    /** Valeur de ImportExcel.source pour un classeur TALENT 360 BANK. */
    public static final String SOURCE_CLASSEUR = "CLASSEUR_TALENT_360";

    private static final int LONGUEUR_MAX_MESSAGE = 1000;
    private static final int LONGUEUR_MAX_NOM_FICHIER = 255;

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final TrimestreService trimestreService;
    private final ImportClasseurService importClasseurService;
    private final com.talent360bank.talent360bank.securite.UtilisateurCourant utilisateurCourant;
    private final ImportExcelRepository importExcelRepository;
    private final TransactionTemplate transaction;

    public ImportService(TrimestreService trimestreService,
                         ImportClasseurService importClasseurService,
                         ImportExcelRepository importExcelRepository,
                         PlatformTransactionManager transactionManager,
                         com.talent360bank.talent360bank.securite.UtilisateurCourant utilisateurCourant) {
        this.utilisateurCourant = utilisateurCourant;
        this.trimestreService = trimestreService;
        this.importClasseurService = importClasseurService;
        this.importExcelRepository = importExcelRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Import reel du classeur : voir {@link #importer(MultipartFile, int, int, boolean)}. */
    public ResultatImport importer(MultipartFile fichier, int annee, int numero) {
        return importer(fichier, annee, numero, false);
    }

    /**
     * Importe tout le classeur pour le trimestre. Un fichier illisible, non
     * conforme au format ou annoncant une autre periode est refuse en entier
     * (statut ECHEC, rien n'est ecrit) ; ensuite, les lignes invalides sont
     * rendues dans le bilan, pas levees.
     *
     * @param simulation valider et compter sans rien enregistrer
     * @throws IllegalArgumentException si le trimestre est invalide ou le
     *                                  fichier absent ou vide
     */
    public ResultatImport importer(MultipartFile fichier, int annee, int numero, boolean simulation) {
        TrimestreService.verifier(annee, numero);
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu, ou fichier vide");
        }
        String nomFichier = nomFichier(fichier);

        Workbook classeur;
        try (InputStream contenu = fichier.getInputStream()) {
            classeur = WorkbookFactory.create(contenu);
        } catch (Exception e) {
            log.warn("Import {} : fichier illisible ({})", nomFichier, e.getMessage());
            return echec(nomFichier, null, annee, numero, simulation, List.of(), MESSAGE_ILLISIBLE);
        }

        try {
            List<ErreurImport> ecarts = FormatClasseur.verifier(classeur);
            if (!ecarts.isEmpty()) {
                log.warn("Import {} : format non conforme, {} ecart(s)", nomFichier, ecarts.size());
                return echec(nomFichier, null, annee, numero, simulation, ecarts, "Format du classeur non conforme ("
                        + ecarts.size() + " écart(s)) : rien n'a été importé. Feuilles ou colonnes manquantes, "
                        + "renommées ou déplacées : voir les erreurs.");
            }
            String autrePeriode = autrePeriode(nomFichier, classeur, annee, numero);
            if (autrePeriode != null) {
                log.warn("Import {} : periode discordante ({})", nomFichier, autrePeriode);
                return echec(nomFichier, null, annee, numero, simulation, List.of(), autrePeriode);
            }
            return simulation
                    ? simuler(classeur, nomFichier, annee, numero)
                    : importerPourDeBon(classeur, nomFichier, annee, numero);
        } finally {
            fermer(classeur, nomFichier);
        }
    }

    private ResultatImport importerPourDeBon(Workbook classeur, String nomFichier, int annee, int numero) {
        Trimestre trimestre = trimestreService.creerSiAbsent(annee, numero).trimestre();
        RapportImport rapport;
        try {
            rapport = importClasseurService.importer(classeur, trimestre);
        } catch (RuntimeException e) {
            log.error("Import {} : erreur inattendue, transaction annulee", nomFichier, e);
            return echec(nomFichier, trimestre, annee, numero, false, List.of(), MESSAGE_ECHEC);
        }
        return journaliser(nomFichier, trimestre, annee, numero, rapport);
    }

    /** Tout l'import, trimestre compris, dans une transaction annulee a la fin. */
    private ResultatImport simuler(Workbook classeur, String nomFichier, int annee, int numero) {
        RapportImport rapport;
        try {
            rapport = transaction.execute(statut -> {
                statut.setRollbackOnly();
                Trimestre trimestre = trimestreService.creerSiAbsent(annee, numero).trimestre();
                return importClasseurService.importer(classeur, trimestre);
            });
        } catch (RuntimeException e) {
            log.error("Simulation {} : erreur inattendue", nomFichier, e);
            return echec(nomFichier, null, annee, numero, true, List.of(), MESSAGE_ECHEC);
        }
        StatutImport statut = statut(rapport);
        log.info("Simulation {} pour T{} {} : {}, {} ligne(s), {} erreur(s), {} desactivation(s)", nomFichier,
                numero, annee, statut, rapport.nbLignes(), rapport.nbErreurs(), rapport.desactives().size());
        return new ResultatImport(null, nomFichier, annee, numero, true, statut, rapport.nbLignes(),
                rapport.bilans(), rapport.erreurs(), rapport.desactives(), rapport.reactives(), message(rapport));
    }

    /**
     * Message de refus si le classeur annonce une autre periode que le
     * trimestre demande, nul sinon (y compris s'il n'annonce rien).
     */
    private static String autrePeriode(String nomFichier, Workbook classeur, int annee, int numero) {
        List<PeriodeClasseur.Periode> discordantes =
                PeriodeClasseur.discordantes(PeriodeClasseur.lire(nomFichier, classeur), annee, numero);
        if (discordantes.isEmpty()) {
            return null;
        }
        return "Le classeur porte une autre période que T" + numero + " " + annee + ", rien n'a été importé : "
                + discordantes.stream()
                .map(p -> p.libelle() + " (" + p.source() + " : \"" + p.texte() + "\")")
                .distinct()
                .collect(Collectors.joining(", "))
                + ". Vérifier le fichier ou le trimestre choisi.";
    }

    /** Journal des imports, le plus recent en premier. */
    public List<ImportExcel> journal() {
        return importExcelRepository.findAllRecentsDabord();
    }

    private ResultatImport journaliser(String nomFichier, Trimestre trimestre, int annee, int numero,
                                       RapportImport rapport) {
        StatutImport statut = statut(rapport);
        String message = message(rapport);
        ImportExcel journal = enregistrer(nomFichier, trimestre, statut, rapport.nbLignes(), rapport.nbErreurs(),
                message);
        log.info("Import {} pour T{} {} : {}, {} ligne(s), {} erreur(s), {} desactivation(s), {} reactivation(s)",
                nomFichier, numero, annee, statut, rapport.nbLignes(), rapport.nbErreurs(),
                rapport.desactives().size(), rapport.reactives().size());
        return new ResultatImport(journal.getIdImport(), nomFichier, annee, numero, false, statut,
                rapport.nbLignes(), rapport.bilans(), rapport.erreurs(), rapport.desactives(), rapport.reactives(),
                message);
    }

    private static StatutImport statut(RapportImport rapport) {
        if (rapport.nbLignes() == 0) {
            return StatutImport.ECHEC;
        }
        return rapport.nbErreurs() == 0 ? StatutImport.SUCCES : StatutImport.PARTIEL;
    }

    private static String message(RapportImport rapport) {
        return rapport.nbLignes() == 0
                ? "Aucune ligne importée : vérifier qu'il s'agit bien du classeur TALENT_360_BANK_Dataset"
                : null;
    }

    /** Refus du fichier entier : journalise sauf en simulation. */
    private ResultatImport echec(String nomFichier, Trimestre trimestre, int annee, int numero, boolean simulation,
                                 List<ErreurImport> erreurs, String message) {
        String tronque = tronquer(message, LONGUEUR_MAX_MESSAGE);
        Integer idImport = simulation
                ? null
                : enregistrer(nomFichier, trimestre, StatutImport.ECHEC, 0, erreurs.size(), tronque).getIdImport();
        return new ResultatImport(idImport, nomFichier, annee, numero, simulation, StatutImport.ECHEC, 0,
                List.of(), List.copyOf(erreurs), List.of(), List.of(), tronque);
    }

    private ImportExcel enregistrer(String nomFichier, Trimestre trimestre, StatutImport statut, int nbLignes,
                                    int nbErreurs, String message) {
        ImportExcel journal = new ImportExcel();
        journal.setSource(SOURCE_CLASSEUR);
        // Compte RH connecte qui lance l'import (null hors requete, ou sans compte en base).
        journal.setUtilisateur(utilisateurCourant.utilisateur().orElse(null));
        journal.setNomFichier(nomFichier);
        journal.setDateImport(LocalDate.now());
        journal.setStatut(statut.name());
        journal.setTrimestre(trimestre);
        journal.setNbLignes(nbLignes);
        journal.setNbErreurs(nbErreurs);
        journal.setMessage(message);
        return importExcelRepository.save(journal);
    }

    /** Nom sans chemin : certains navigateurs envoient le chemin complet du poste. */
    private static String nomFichier(MultipartFile fichier) {
        String nom = fichier.getOriginalFilename();
        if (nom == null || nom.isBlank()) {
            return "classeur.xlsx";
        }
        String sansChemin = nom.substring(Math.max(nom.lastIndexOf('/'), nom.lastIndexOf('\\')) + 1);
        return tronquer(sansChemin.isBlank() ? "classeur.xlsx" : sansChemin, LONGUEUR_MAX_NOM_FICHIER);
    }

    private static String tronquer(String texte, int longueurMax) {
        return texte.length() <= longueurMax ? texte : texte.substring(0, longueurMax);
    }

    private static void fermer(Workbook classeur, String nomFichier) {
        try {
            classeur.close();
        } catch (IOException e) {
            log.warn("Import {} : fermeture du classeur impossible ({})", nomFichier, e.getMessage());
        }
    }
}
