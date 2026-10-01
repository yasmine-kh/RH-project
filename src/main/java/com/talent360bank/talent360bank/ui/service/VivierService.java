package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.ui.model.VivierRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assemble le tableau des Viviers pour l'affichage.
 *
 * REGLE : aucun calcul ici. La liste des collaborateurs par vivier vient de
 * AppartenanceVivierRepository, leurs scores viennent de ScoreRepository.
 *
 * <p>Deux lectures quel que soit le nombre de lignes : les appartenances du
 * trimestre (collaborateur et entite compris) et les scores du trimestre.
 */
@Service
public class VivierService {

    private final AppartenanceVivierRepository appartenanceVivierRepository;
    private final ScoreRepository scoreRepository;

    public VivierService(AppartenanceVivierRepository appartenanceVivierRepository,
                         ScoreRepository scoreRepository) {
        this.appartenanceVivierRepository = appartenanceVivierRepository;
        this.scoreRepository = scoreRepository;
    }

    /**
     * @param trimestre trimestre affiche (TrimestreCourantService), null s'il n'en existe aucun
     */
    public List<VivierRow> buildRows(Trimestre trimestre) {
        List<VivierRow> rows = new ArrayList<>();

        if (trimestre == null) {
            return rows;
        }

        Map<String, Score> scores = new HashMap<>();
        for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            scores.put(score.getCollaborateur().getIdCollaborateur(), score);
        }

        for (AppartenanceVivier appartenance : appartenanceVivierRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            Collaborateur collaborateur = appartenance.getCollaborateur();

            Optional<Score> score = Optional.ofNullable(scores.get(collaborateur.getIdCollaborateur()));

            rows.add(new VivierRow(
                    collaborateur.getNom(),
                    collaborateur.getPrenom(),
                    collaborateur.getFonction(),
                    score.map(Score::getDepartement).orElse(collaborateur.getDepartement()),
                    score.map(Score::getScorePotentiel).orElse(null),
                    score.map(Score::getScorePerformance).orElse(null),
                    appartenance.getVivier().getNomCategorie()
            ));
        }

        return rows;
    }
}