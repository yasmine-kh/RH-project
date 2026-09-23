package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.RecalculResponse;
import com.talent360bank.talent360bank.controller.dto.ScoreResume;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.ScoreService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Calcul et lecture des scores de performance et de potentiel. */
@RestController
@RequestMapping("/api")
public class ScoreController {

    private final ScoreService scoreService;
    private final ScoreRepository scoreRepository;
    private final ChargeurRessources chargeur;

    public ScoreController(ScoreService scoreService, ScoreRepository scoreRepository,
                           ChargeurRessources chargeur) {
        this.scoreService = scoreService;
        this.scoreRepository = scoreRepository;
        this.chargeur = chargeur;
    }

    /**
     * Recalcule et enregistre les scores de tout le trimestre.
     *
     * <p>POST et non PUT : l'appel n'est pas idempotent au sens strict, il
     * repose la date de calcul a chaque passage. Il reste rejouable sans
     * risque, un employe n'ayant qu'un score par trimestre.
     */
    @PostMapping("/trimestres/{annee}/{numero}/scores/recalcul")
    public RecalculResponse recalculer(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return RecalculResponse.de(scoreService.recalculerTrimestre(trimestre));
    }

    /** Scores enregistres du trimestre. */
    @GetMapping("/trimestres/{annee}/{numero}/scores")
    @Transactional(readOnly = true)
    public List<ScoreResume> duTrimestre(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return scoreRepository.findByTrimestreAvecEmploye(trimestre).stream()
                .map(ScoreResume::de)
                .toList();
    }

    /** Historique d'un employe, du trimestre le plus recent au plus ancien. */
    @GetMapping("/employes/{employeeId}/scores")
    @Transactional(readOnly = true)
    public List<ScoreResume> historique(@PathVariable String employeeId) {
        Employe employe = chargeur.exigerEmploye(employeeId);
        return scoreService.historique(employe).stream()
                .map(ScoreResume::de)
                .toList();
    }
}
