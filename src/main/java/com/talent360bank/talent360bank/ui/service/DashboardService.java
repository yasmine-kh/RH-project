package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.VivierRepository;
import com.talent360bank.talent360bank.service.TableauDeBordService;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Assemble les 9 cartes KPI du Dashboard.
 *
 * REGLE : aucun calcul ici. 8 des 9 chiffres viennent d'un seul appel a
 * TableauDeBordService.synthese() (Jas). "Alertes actives" reste mocke :
 * l'entite Alerte a ete supprimee du projet, rien de fiable a brancher.
 */
@Service
public class DashboardService {

    private final CollaborateurRepository collaborateurRepository;
    private final VivierRepository vivierRepository;
    private final TrimestreRepository trimestreRepository;
    private final TableauDeBordService tableauDeBordService;

    public DashboardService(CollaborateurRepository collaborateurRepository,
                            VivierRepository vivierRepository,
                            TrimestreRepository trimestreRepository,
                            TableauDeBordService tableauDeBordService) {
        this.collaborateurRepository = collaborateurRepository;
        this.vivierRepository = vivierRepository;
        this.trimestreRepository = trimestreRepository;
        this.tableauDeBordService = tableauDeBordService;
    }

    public List<KpiCard> buildKpis() {
        long collaborateursActifs = collaborateurRepository.countByStatut(StatutCollaborateur.ACTIF);
        long viviers = vivierRepository.count();
        Optional<SyntheseTableauDeBord> synthese = chargerSynthese();

        return List.of(
                new KpiCard("Collaborateurs actifs", String.valueOf(collaborateursActifs), "bi-people", "kpi-blue"),
                new KpiCard("Talents valides", valeur(synthese, SyntheseTableauDeBord::nbTalentsValides), "bi-star", "kpi-purple"),
                new KpiCard("Hauts potentiels", valeur(synthese, SyntheseTableauDeBord::nbHautsPotentiels), "bi-graph-up-arrow", "kpi-green"),
                new KpiCard("Collaborateurs a risque", valeur(synthese, SyntheseTableauDeBord::nbARisque), "bi-icon-warning", "kpi-red"),
                new KpiCard("Postes critiques", valeur(synthese, SyntheseTableauDeBord::nbPostesCritiques), "bi-exclamation-triangle", "kpi-orange"),
                new KpiCard("Postes sans successeur", valeur(synthese, SyntheseTableauDeBord::nbAlertesPostesCritiques), "bi-x-circle", "kpi-red"),
                // MOCK : entite Alerte supprimee du projet, rien a brancher pour l'instant
                new KpiCard("Alertes actives", "23", "bi-bell", "kpi-orange"),
                new KpiCard("Viviers de talents", String.valueOf(viviers), "bi-collection", "kpi-blue"),
                new KpiCard("Repartis en 9-Box", valeur(synthese, SyntheseTableauDeBord::nbPlaces9Box), "bi-grid-3x3", "kpi-purple")
        );
    }

    private Optional<SyntheseTableauDeBord> chargerSynthese() {
        Optional<Trimestre> dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        if (dernierTrimestre.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(tableauDeBordService.synthese(dernierTrimestre.get()));
        } catch (RessourceIntrouvableException e) {
            return Optional.empty();
        }
    }

    private String valeur(Optional<SyntheseTableauDeBord> synthese, java.util.function.ToIntFunction<SyntheseTableauDeBord> extracteur) {
        return synthese.map(s -> String.valueOf(extracteur.applyAsInt(s))).orElse("0");
    }
}