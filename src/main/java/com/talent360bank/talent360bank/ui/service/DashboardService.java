package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DashboardService {

    private final EmployeRepository employeRepository;
    private final TrimestreRepository trimestreRepository;
    private final PosteRepository posteRepository;
    private final TalentService talentService;

    public DashboardService(EmployeRepository employeRepository,
                            TrimestreRepository trimestreRepository,
                            PosteRepository posteRepository,
                            TalentService talentService) {
        this.employeRepository = employeRepository;
        this.trimestreRepository = trimestreRepository;
        this.posteRepository = posteRepository;
        this.talentService = talentService;
    }

    public List<KpiCard> buildKpis() {
        long collaborateursActifs = employeRepository.count();
        int talentsValides = compterTalentsTrimestreCourant();
        long postesCritiques = posteRepository.findByPosteCritique("Oui").size();

        return List.of(
                new KpiCard("Collaborateurs actifs", String.valueOf(collaborateursActifs), "bi-people", "kpi-blue"),
                new KpiCard("Talents valides", String.valueOf(talentsValides), "bi-star", "kpi-purple"),
                new KpiCard("Hauts potentiels", "9", "bi-graph-up-arrow", "kpi-green"),
                new KpiCard("Collaborateurs a risque", "18", "kpi-icon-warning", "kpi-red"),
                new KpiCard("Postes critiques", String.valueOf(postesCritiques), "bi-exclamation-triangle", "kpi-orange"),
                new KpiCard("Postes sans successeur", "1", "bi-x-circle", "kpi-red"),
                new KpiCard("Alertes actives", "23", "bi-bell", "kpi-orange"),
                new KpiCard("Viviers de talents", "5", "bi-collection", "kpi-blue"),
                new KpiCard("Repartis en 9-Box", "100", "bi-grid-3x3", "kpi-purple")
        );
    }

    private int compterTalentsTrimestreCourant() {
        Optional<Trimestre> dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        if (dernierTrimestre.isEmpty()) {
            return 0;
        }
        try {
            return talentService.compterTalents(dernierTrimestre.get());
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            return 0;
        }
    }
}