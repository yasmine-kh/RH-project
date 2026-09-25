package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.VivierRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Assemble le tableau des Viviers pour l'affichage.
 *
 * REGLE : aucun calcul ici. La liste des employes par vivier vient de
 * AppartenanceVivierRepository, leurs scores viennent de ScoreRepository.
 */
@Service
public class VivierService {

    private final AppartenanceVivierRepository appartenanceVivierRepository;
    private final ScoreRepository scoreRepository;
    private final TrimestreRepository trimestreRepository;

    public VivierService(AppartenanceVivierRepository appartenanceVivierRepository,
                         ScoreRepository scoreRepository,
                         TrimestreRepository trimestreRepository) {
        this.appartenanceVivierRepository = appartenanceVivierRepository;
        this.scoreRepository = scoreRepository;
        this.trimestreRepository = trimestreRepository;
    }

    public List<VivierRow> buildRows() {
        List<VivierRow> rows = new ArrayList<>();

        Optional<Trimestre> dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        if (dernierTrimestre.isEmpty()) {
            return rows;
        }

        Trimestre trimestre = dernierTrimestre.get();

        for (AppartenanceVivier appartenance : appartenanceVivierRepository.findByTrimestre(trimestre)) {
            Employe employe = appartenance.getEmploye();

            Optional<Score> score = scoreRepository.findByEmployeAndTrimestre(employe, trimestre);

            rows.add(new VivierRow(
                    employe.getNom(),
                    employe.getPrenom(),
                    employe.getFonction(),
                    employe.getDepartement(),
                    score.map(Score::getScorePotentiel).orElse(null),
                    score.map(Score::getScorePerformance).orElse(null),
                    appartenance.getVivier().getNomCategorie()
            ));
        }

        return rows;
    }
}