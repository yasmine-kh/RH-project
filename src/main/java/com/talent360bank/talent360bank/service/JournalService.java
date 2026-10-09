package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEvenement;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.entity.ValidationSuccession;
import com.talent360bank.talent360bank.repository.JournalEvenementRepository;
import com.talent360bank.talent360bank.securite.UtilisateurCourant;
import com.talent360bank.talent360bank.service.enums.DecisionSuccession;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Ecrit l'historique (journal_evenement) : un evenement par import, decision du Comite, changement
 * de reglages, ajout ou suppression de collaborateur. Les descriptions sont en francais, pretes a
 * afficher ; le lien mene a la page concernee. Appele dans la transaction de l'action : si l'action
 * est annulee, l'evenement l'est aussi.
 *
 * <p>Les methodes {@code ...Repris} servent au rattrapage (JournalRattrapage) : meme texte, date de
 * l'action d'origine, auteur d'origine.
 */
@Service
public class JournalService {

    /** Prefixes de JournalEvenement.reference, une par table d'origine. */
    static final String REF_IMPORT = "import_excel:";
    static final String REF_TALENT = "validation_comite:";
    static final String REF_SUCCESSION = "validation_succession:";

    private final JournalEvenementRepository repository;
    private final UtilisateurCourant utilisateurCourant;

    public JournalService(JournalEvenementRepository repository, UtilisateurCourant utilisateurCourant) {
        this.repository = repository;
        this.utilisateurCourant = utilisateurCourant;
    }

    // ------------------------------------------------------------------ imports

    /** Un import (classeur ou questionnaire) vient d'etre journalise dans import_excel. */
    public void importEnregistre(ImportExcel journal) {
        repository.save(evenementImport(journal, maintenant(), auteur()));
    }

    JournalEvenement evenementImport(ImportExcel journal, LocalDateTime date, Utilisateur auteur) {
        boolean questionnaire = ImportQuestionnaireService.SOURCE.equals(journal.getSource());
        Trimestre trimestre = journal.getTrimestre();
        StringBuilder texte = new StringBuilder(questionnaire ? "Import des réponses au questionnaire « "
                : "Import du classeur « ").append(journal.getNomFichier()).append(" »");
        if (trimestre != null) {
            texte.append(" (").append(libelle(trimestre)).append(")");
        }
        texte.append(" : ").append(StatutImport.libelle(journal.getStatut()));
        if (journal.getNbLignes() != null) {
            texte.append(" — ").append(journal.getNbLignes()).append(" ligne(s)");
        }
        if (journal.getNbErreurs() != null && journal.getNbErreurs() > 0) {
            texte.append(", ").append(journal.getNbErreurs()).append(" signalée(s)");
        }
        if (StatutImport.ECHEC.name().equals(journal.getStatut()) && journal.getMessage() != null) {
            texte.append(". ").append(journal.getMessage());
        }
        return new JournalEvenement(date, auteur, TypeEvenement.IMPORT, trimestre, texte.toString(), "/import",
                null, REF_IMPORT + journal.getIdImport());
    }

    // ------------------------------------------------------------------ Comite Talent

    /**
     * @param avant decision precedente, null s'il n'y en avait pas
     */
    public void decisionTalent(ValidationComite validation, String nomComplet, StatutValidationComite avant) {
        repository.save(evenementTalent(validation, nomComplet, avant, maintenant(), auteur()));
    }

    JournalEvenement evenementTalent(ValidationComite validation, String nomComplet, StatutValidationComite avant,
                                     LocalDateTime date, Utilisateur auteur) {
        String matricule = validation.getCollaborateur().getIdCollaborateur();
        StringBuilder texte = new StringBuilder("Comité Talent : ").append(nomComplet).append(" (")
                .append(matricule).append(") — talent ").append(libelleTalent(validation.getStatut()));
        if (avant != null && avant != validation.getStatut()) {
            texte.append(" (avant : ").append(libelleTalent(avant)).append(")");
        }
        commentaire(texte, validation.getCommentaire());
        return new JournalEvenement(date, auteur, TypeEvenement.DECISION_TALENT, validation.getTrimestre(),
                texte.toString(), fiche(matricule, validation.getTrimestre()), matricule,
                REF_TALENT + validation.getIdValidation());
    }

    public void decisionSuccession(ValidationSuccession validation, DecisionSuccession avant) {
        repository.save(evenementSuccession(validation, avant, maintenant(), auteur()));
    }

    JournalEvenement evenementSuccession(ValidationSuccession validation, DecisionSuccession avant,
                                         LocalDateTime date, Utilisateur auteur) {
        String matricule = validation.getSuccesseur().getIdCollaborateur();
        StringBuilder texte = new StringBuilder("Comité Talent : succession ")
                .append(validation.getSuccesseur().getNomComplet()).append(" (").append(matricule).append(") → ")
                .append(validation.getPoste().getNomPoste()).append(" (").append(validation.getPoste().getPosteId())
                .append(") — ").append(validation.getDecision().getLibelle());
        if (avant != null && avant != validation.getDecision()) {
            texte.append(" (avant : ").append(avant.getLibelle()).append(")");
        }
        commentaire(texte, validation.getCommentaire());
        String lien = UriComponentsBuilder.fromPath("/postes-critiques")
                .queryParam("trimestre", valeur(validation.getTrimestre()))
                .fragment("poste-" + validation.getPoste().getPosteId()).encode().build().toUriString();
        return new JournalEvenement(date, auteur, TypeEvenement.DECISION_SUCCESSION, validation.getTrimestre(),
                texte.toString(), lien, matricule, REF_SUCCESSION + validation.getIdValidationSuccession());
    }

    // ------------------------------------------------------------------ reglages, collaborateurs

    /** Reglages du moteur, date de reference, configuration du questionnaire. */
    public void parametres(Trimestre trimestre, String description, String lien) {
        repository.save(new JournalEvenement(maintenant(), auteur(), TypeEvenement.PARAMETRES, trimestre,
                description, lien, null, null));
    }

    public void collaborateur(String matricule, String description, String lien) {
        repository.save(new JournalEvenement(maintenant(), auteur(), TypeEvenement.COLLABORATEUR, null,
                description, lien, matricule, "collaborateur:" + matricule));
    }

    // ------------------------------------------------------------------ outils

    private Utilisateur auteur() {
        return utilisateurCourant.utilisateur().orElse(null);
    }

    private static LocalDateTime maintenant() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String libelleTalent(StatutValidationComite statut) {
        return switch (statut) {
            case OUI -> "validé (Oui)";
            case NON -> "non retenu (Non)";
            case EN_ATTENTE -> "à réévaluer (En attente)";
        };
    }

    private static void commentaire(StringBuilder texte, String commentaire) {
        if (commentaire != null && !commentaire.isBlank()) {
            texte.append(" · « ").append(commentaire).append(" »");
        }
    }

    private static String fiche(String matricule, Trimestre trimestre) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath("/fiche-collaborateur").queryParam("matricule", matricule);
        if (trimestre != null) {
            lien.queryParam("trimestre", valeur(trimestre));
        }
        return lien.encode().build().toUriString();
    }

    static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }

    static String valeur(Trimestre trimestre) {
        return trimestre.getAnnee() + "-" + trimestre.getNumero();
    }
}
