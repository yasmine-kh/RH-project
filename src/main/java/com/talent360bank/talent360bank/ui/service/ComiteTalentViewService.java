package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.ui.model.ComiteTalentRow;
import com.talent360bank.talent360bank.ui.model.ComiteTalentView;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.OptionFiltre;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
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

    public ComiteTalentViewService(ValidationComiteService validationComiteService) {
        this.validationComiteService = validationComiteService;
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
                    List.of(kpi(0)), 0, List.of(), null);
        }

        List<DecisionComite> decisions;
        String erreur = null;
        try {
            decisions = validationComiteService.getDecisionsComite(trimestre);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            decisions = List.of();
            erreur = e.getMessage();
        }

        Map<StatutValidationComite, Integer> parStatut = new EnumMap<>(StatutValidationComite.class);
        List<ComiteTalentRow> rows = new ArrayList<>();
        for (DecisionComite decision : decisions) {
            parStatut.merge(decision.statut(), 1, Integer::sum);
            if (filtre == null || decision.statut() == filtre) {
                rows.add(ligne(decision));
            }
        }

        return new ComiteTalentView(optionsTrimestre, libelle(trimestre), optionsStatut(filtre, parStatut),
                List.of(kpi(parStatut.getOrDefault(StatutValidationComite.OUI, 0))),
                decisions.size(), rows, erreur);
    }

    private ComiteTalentRow ligne(DecisionComite decision) {
        Score score = decision.score();
        Collaborateur collaborateur = score.getCollaborateur();
        StatutValidationComite statut = decision.statut();
        return new ComiteTalentRow(
                collaborateur.getPrenom() + " " + collaborateur.getNom(),
                score.getDirection(),
                score.getScorePerformance(),
                score.getCategoriePerformance() == null ? null : score.getCategoriePerformance().getLibelle(),
                score.getScorePotentiel(),
                score.getCategoriePotentiel() == null ? null : score.getCategoriePotentiel().getLibelle(),
                score.getPositionBox(),
                statut.name(),
                statut.getLibelle());
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
