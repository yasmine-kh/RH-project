package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.entity.ValidationSuccession;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.repository.ValidationSuccessionRepository;
import com.talent360bank.talent360bank.securite.UtilisateurCourant;
import com.talent360bank.talent360bank.service.enums.DecisionSuccession;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Decisions du Comite Talent saisies sur l'ecran Comite Talent : talents proposes
 * (validation_comite) et successeurs des postes critiques (validation_succession).
 *
 * <p>Chaque decision porte sa date, le compte RH connecte et un commentaire facultatif ; elle peut
 * etre modifiee ensuite (la ligne est remplacee). Seuls les talents proposes par le moteur et les
 * successeurs evalues d'un poste critique du trimestre peuvent recevoir une decision.
 *
 * <p>Les chiffres qui en dependent (talents valides du tableau de bord, du Comite et de la liste des
 * collaborateurs) sont calcules a la lecture : ils changent des la decision enregistree.
 */
@Service
public class DecisionsComiteService {

    private static final Logger log = LoggerFactory.getLogger(DecisionsComiteService.class);

    private final ValidationComiteService validationComiteService;
    private final PosteCritiqueService posteCritiqueService;
    private final ValidationComiteRepository validationComiteRepository;
    private final ValidationSuccessionRepository validationSuccessionRepository;
    private final UtilisateurCourant utilisateurCourant;
    private final JournalService journalService;

    public DecisionsComiteService(ValidationComiteService validationComiteService,
                                  PosteCritiqueService posteCritiqueService,
                                  ValidationComiteRepository validationComiteRepository,
                                  ValidationSuccessionRepository validationSuccessionRepository,
                                  UtilisateurCourant utilisateurCourant,
                                  JournalService journalService) {
        this.validationComiteService = validationComiteService;
        this.posteCritiqueService = posteCritiqueService;
        this.validationComiteRepository = validationComiteRepository;
        this.validationSuccessionRepository = validationSuccessionRepository;
        this.utilisateurCourant = utilisateurCourant;
        this.journalService = journalService;
    }

    /** Ce qui a ete enregistre, pour le message de confirmation. */
    public record DecisionEnregistree(String nomComplet, String libelleDecision) {
    }

    /**
     * @throws DecisionRefuseeException si le collaborateur n'est pas un talent propose du trimestre
     *                                  ou si le commentaire est trop long
     */
    @Transactional
    public DecisionEnregistree deciderTalent(Trimestre trimestre, String matricule, StatutValidationComite statut,
                                             String commentaire) {
        Objects.requireNonNull(trimestre, "trimestre");
        Objects.requireNonNull(statut, "statut");
        String texte = commentaire(commentaire);
        Collaborateur collaborateur = validationComiteService.getDecisionsComite(trimestre).stream()
                .map(DecisionComite::score)
                .map(score -> score.getCollaborateur())
                .filter(c -> c.getIdCollaborateur().equals(matricule))
                .findFirst()
                .orElseThrow(() -> new DecisionRefuseeException(
                        "Décision refusée : " + matricule + " n'est pas un talent proposé pour ce trimestre."));
        java.util.Optional<ValidationComite> existante = validationComiteRepository.findDecision(matricule, trimestre);
        StatutValidationComite avant = existante.map(ValidationComite::getStatut).orElse(null);
        ValidationComite validation = existante.orElseGet(() -> new ValidationComite(collaborateur, trimestre, statut));
        validation.decider(statut, maintenant(), auteur(), texte);
        journalService.decisionTalent(validationComiteRepository.save(validation), collaborateur.getNomComplet(), avant);
        log.info("Comite Talent : decision {} pour {} sur T{} {}", statut, matricule, trimestre.getNumero(),
                trimestre.getAnnee());
        return new DecisionEnregistree(collaborateur.getNomComplet(), statut.getLibelle());
    }

    /**
     * @throws DecisionRefuseeException si le collaborateur n'est pas un successeur evalue de ce poste
     *                                  critique pour le trimestre, ou si le commentaire est trop long
     */
    @Transactional
    public DecisionEnregistree deciderSuccession(Trimestre trimestre, String posteId, String matricule,
                                                 DecisionSuccession decision, String commentaire) {
        Objects.requireNonNull(trimestre, "trimestre");
        Objects.requireNonNull(decision, "decision");
        String texte = commentaire(commentaire);
        CouverturePoste couverture = posteCritiqueService.listerPostesCritiques(trimestre).stream()
                .filter(c -> c.poste().getPosteId().equals(posteId))
                .findFirst()
                .orElseThrow(() -> new DecisionRefuseeException(
                        "Décision refusée : " + posteId + " n'est pas un poste critique de ce trimestre."));
        Collaborateur successeur = couverture.successeurs().stream()
                .map(ResultatMatching::candidat)
                .filter(c -> c.getIdCollaborateur().equals(matricule))
                .findFirst()
                .orElseThrow(() -> new DecisionRefuseeException("Décision refusée : " + matricule
                        + " n'est pas un successeur évalué du poste " + posteId + "."));
        Poste poste = couverture.poste();
        java.util.Optional<ValidationSuccession> existante =
                validationSuccessionRepository.findDecision(trimestre, posteId, matricule);
        DecisionSuccession avant = existante.map(ValidationSuccession::getDecision).orElse(null);
        ValidationSuccession validation = existante.orElseGet(() -> new ValidationSuccession(trimestre, poste, successeur));
        validation.decider(decision, maintenant(), auteur(), texte);
        journalService.decisionSuccession(validationSuccessionRepository.save(validation), avant);
        log.info("Comite Talent : succession {} / {} : {} sur T{} {}", posteId, matricule, decision,
                trimestre.getNumero(), trimestre.getAnnee());
        return new DecisionEnregistree(successeur.getNomComplet() + " → " + poste.getNomPoste(),
                decision.getLibelle());
    }

    private Utilisateur auteur() {
        return utilisateurCourant.utilisateur().orElse(null);
    }

    private static LocalDateTime maintenant() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** Commentaire nettoye : null si vide ; refuse au-dela de la taille de la colonne. */
    static String commentaire(String commentaire) {
        if (commentaire == null || commentaire.isBlank()) {
            return null;
        }
        String texte = commentaire.strip();
        if (texte.length() > ValidationComite.COMMENTAIRE_MAX) {
            throw new DecisionRefuseeException("Le commentaire dépasse " + ValidationComite.COMMENTAIRE_MAX
                    + " caractères : rien n'a été enregistré.");
        }
        return texte;
    }

    /** Decision impossible : message pret a afficher, rien n'est enregistre. */
    public static class DecisionRefuseeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DecisionRefuseeException(String message) {
            super(message);
        }
    }
}
