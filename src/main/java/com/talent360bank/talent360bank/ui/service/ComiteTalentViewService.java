package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.ui.model.ComiteTalentRow;
import com.talent360bank.talent360bank.ui.model.ComiteTalentView;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.OptionFiltre;
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
    private final TrimestreRepository trimestreRepository;

    public ComiteTalentViewService(ValidationComiteService validationComiteService,
                                   TrimestreRepository trimestreRepository) {
        this.validationComiteService = validationComiteService;
        this.trimestreRepository = trimestreRepository;
    }

    /**
     * @param trimestreDemande trimestre choisi, au format "2026-3" ; absent ou
     *                         inconnu, c'est le plus recent qui est affiche
     * @param statutDemande    OUI, EN_ATTENTE ou NON ; absent ou inconnu, tous
     */
    public ComiteTalentView build(String trimestreDemande, String statutDemande) {
        List<Trimestre> trimestres = trimestreRepository.findAllByOrderByAnneeDescNumeroDesc();
        Trimestre trimestre = choisirTrimestre(trimestres, trimestreDemande);
        StatutValidationComite filtre = lireStatut(statutDemande);

        List<OptionFiltre> optionsTrimestre = new ArrayList<>();
        for (Trimestre candidat : trimestres) {
            optionsTrimestre.add(new OptionFiltre(valeur(candidat), libelle(candidat), candidat == trimestre));
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
        Employe employe = score.getEmploye();
        StatutValidationComite statut = decision.statut();
        return new ComiteTalentRow(
                employe.getPrenom() + " " + employe.getNom(),
                employe.getDirection(),
                score.getScorePerformance(),
                score.getCategoriePerformance() == null ? null : score.getCategoriePerformance().getLibelle(),
                score.getScorePotentiel(),
                score.getCategoriePotentiel() == null ? null : score.getCategoriePotentiel().getLibelle(),
                score.getPositionBox(),
                statut.name(),
                statut.getLibelle(),
                badgeClass(statut));
    }

    static String badgeClass(StatutValidationComite statut) {
        return switch (statut) {
            case OUI -> "bg-success";
            case EN_ATTENTE -> "bg-warning text-dark";
            case NON -> "bg-danger";
        };
    }

    private KpiCard kpi(int talentsValides) {
        return new KpiCard("Talents valides (Comite)", String.valueOf(talentsValides),
                "bi-patch-check", "kpi-green");
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

    private static Trimestre choisirTrimestre(List<Trimestre> trimestres, String demande) {
        if (trimestres.isEmpty()) {
            return null;
        }
        if (demande != null) {
            for (Trimestre trimestre : trimestres) {
                if (valeur(trimestre).equals(demande.trim())) {
                    return trimestre;
                }
            }
        }
        return trimestres.get(0);
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

    private static String valeur(Trimestre trimestre) {
        return trimestre.getAnnee() + "-" + trimestre.getNumero();
    }

    private static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
