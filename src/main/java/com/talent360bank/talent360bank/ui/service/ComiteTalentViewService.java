package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.entity.ValidationSuccession;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.repository.ValidationSuccessionRepository;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.DecisionSuccession;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.ui.model.ComiteTalentRow;
import com.talent360bank.talent360bank.ui.model.DecisionSaisie;
import com.talent360bank.talent360bank.ui.model.SuccessionComiteRow;
import com.talent360bank.talent360bank.ui.model.ComiteTalentView;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.OptionFiltre;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assemble l'ecran Comite Talent pour l'affichage.
 *
 * REGLE : aucun calcul ici. Les talents proposes et la decision du comite
 * viennent de ValidationComiteService (Jas), les scores et categories de
 * Score, deja remplis par ScoreService et NeufBoxService (Jas).
 */
@Service
public class ComiteTalentViewService {

    /** Valeur du filtre de statut qui affiche tous les talents. */
    public static final String TOUS = "TOUS";

    private final ValidationComiteService validationComiteService;
    private final PosteCritiqueService posteCritiqueService;
    private final ValidationComiteRepository validationComiteRepository;
    private final ValidationSuccessionRepository validationSuccessionRepository;

    public ComiteTalentViewService(ValidationComiteService validationComiteService,
                                   PosteCritiqueService posteCritiqueService,
                                   ValidationComiteRepository validationComiteRepository,
                                   ValidationSuccessionRepository validationSuccessionRepository) {
        this.validationComiteService = validationComiteService;
        this.posteCritiqueService = posteCritiqueService;
        this.validationComiteRepository = validationComiteRepository;
        this.validationSuccessionRepository = validationSuccessionRepository;
    }

    /**
     * @param selection     trimestre affiche et liste des trimestres (TrimestreCourantService :
     *                      le trimestre demande, sinon le plus recent qui a des scores)
     * @param statutDemande OUI, EN_ATTENTE ou NON ; absent ou inconnu, tous
     */
    public ComiteTalentView build(TrimestreCourantService.Selection selection, String statutDemande) {
        Trimestre trimestre = selection.trimestre();
        StatutValidationComite filtre = lireStatut(statutDemande);

        String choisi = trimestre == null ? null : TrimestreCourantService.valeur(trimestre);
        List<OptionFiltre> optionsTrimestre = new ArrayList<>();
        for (OptionTrimestre candidat : selection.trimestres()) {
            optionsTrimestre.add(new OptionFiltre(candidat.valeur(), candidat.libelle(),
                    candidat.valeur().equals(choisi)));
        }

        if (trimestre == null) {
            return new ComiteTalentView(optionsTrimestre, null, optionsStatut(filtre, Map.of()),
                    List.of(kpi(0)), 0, List.of(), null, List.of());
        }

        List<DecisionComite> decisions;
        List<CouverturePoste> couvertures;
        String erreur = null;
        try {
            decisions = validationComiteService.getDecisionsComite(trimestre);
            couvertures = posteCritiqueService.listerPostesCritiques(trimestre);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            decisions = List.of();
            couvertures = List.of();
            erreur = e.getMessage();
        }
        Map<String, DecisionSaisie> saisies = new HashMap<>();
        if (!decisions.isEmpty()) {
            for (ValidationComite validation : validationComiteRepository.findByTrimestreAvecAuteur(trimestre)) {
                if (validation.estSaisieApplication()) {
                    saisies.put(validation.getCollaborateur().getIdCollaborateur(), new DecisionSaisie(
                            validation.getDateDecision(), login(validation.getUtilisateur()),
                            validation.getCommentaire()));
                }
            }
        }

        Map<StatutValidationComite, Integer> parStatut = new EnumMap<>(StatutValidationComite.class);
        List<ComiteTalentRow> rows = new ArrayList<>();
        for (DecisionComite decision : decisions) {
            parStatut.merge(decision.statut(), 1, Integer::sum);
            if (filtre == null || decision.statut() == filtre) {
                rows.add(ligne(decision, saisies));
            }
        }

        return new ComiteTalentView(optionsTrimestre, libelle(trimestre), optionsStatut(filtre, parStatut),
                List.of(kpi(parStatut.getOrDefault(StatutValidationComite.OUI, 0))),
                decisions.size(), rows, erreur, successions(trimestre, couvertures));
    }

    /**
     * Chaque successeur evalue de chaque poste critique (ordre des postes, meilleur matching d'abord),
     * avec la decision du comite du trimestre.
     */
    private List<SuccessionComiteRow> successions(Trimestre trimestre, List<CouverturePoste> couvertures) {
        if (couvertures.stream().allMatch(c -> c.successeurs().isEmpty())) {
            return List.of();
        }
        Map<String, ValidationSuccession> decisions = new HashMap<>();
        for (ValidationSuccession v : validationSuccessionRepository.findByTrimestreAvecDetails(trimestre)) {
            decisions.put(v.getPoste().getPosteId() + "|" + v.getSuccesseur().getIdCollaborateur(), v);
        }
        List<SuccessionComiteRow> lignes = new ArrayList<>();
        for (CouverturePoste couverture : couvertures) {
            String posteId = couverture.poste().getPosteId();
            for (ResultatMatching successeur : couverture.successeurs()) {
                String matricule = successeur.candidat().getIdCollaborateur();
                ValidationSuccession v = decisions.get(posteId + "|" + matricule);
                DecisionSuccession decision = v == null ? null : v.getDecision();
                lignes.add(new SuccessionComiteRow(posteId, couverture.poste().getNomPoste(), matricule,
                        successeur.candidat().getNomComplet(), successeur.scoreMatching(),
                        successeur.readiness().getLibelle(), decision == null ? null : decision.name(),
                        decision == null ? null : decision.getLibelle(), DecisionSuccession.enAttente(decision),
                        v == null ? null : new DecisionSaisie(v.getDateDecision(), login(v.getUtilisateur()),
                                v.getCommentaire())));
            }
        }
        return List.copyOf(lignes);
    }

    private static String login(Utilisateur utilisateur) {
        return utilisateur == null ? null : utilisateur.getLogin();
    }

    private ComiteTalentRow ligne(DecisionComite decision, Map<String, DecisionSaisie> saisies) {
        Score score = decision.score();
        Collaborateur collaborateur = score.getCollaborateur();
        StatutValidationComite statut = decision.statut();
        return new ComiteTalentRow(
                collaborateur.getIdCollaborateur(),
                collaborateur.getPrenom() + " " + collaborateur.getNom(),
                score.getDirection(),
                score.getScorePerformance(),
                score.getCategoriePerformance() == null ? null : score.getCategoriePerformance().getLibelle(),
                score.getScorePotentiel(),
                score.getCategoriePotentiel() == null ? null : score.getCategoriePotentiel().getLibelle(),
                score.getPositionBox(),
                statut.name(),
                statut.getLibelle(),
                saisies.get(collaborateur.getIdCollaborateur()));
    }

    private KpiCard kpi(int talentsValides) {
        return new KpiCard("Talents validés (Comité)", String.valueOf(talentsValides));
    }

    /** Tous, puis chaque statut avec son effectif sur le trimestre. */
    private List<OptionFiltre> optionsStatut(StatutValidationComite filtre,
                                             Map<StatutValidationComite, Integer> parStatut) {
        int total = parStatut.values().stream().mapToInt(Integer::intValue).sum();
        List<OptionFiltre> options = new ArrayList<>();
        options.add(new OptionFiltre(TOUS, "Tous (" + total + ")", filtre == null));
        for (StatutValidationComite statut : StatutValidationComite.values()) {
            options.add(new OptionFiltre(statut.name(),
                    statut.getLibelle() + " (" + parStatut.getOrDefault(statut, 0) + ")",
                    statut == filtre));
        }
        return options;
    }

    private static StatutValidationComite lireStatut(String demande) {
        if (demande == null) {
            return null;
        }
        for (StatutValidationComite statut : StatutValidationComite.values()) {
            if (statut.name().equalsIgnoreCase(demande.trim())) {
                return statut;
            }
        }
        return null;
    }

    private static String libelle(Trimestre trimestre) {
        return TrimestreCourantService.libelle(trimestre);
    }
}
