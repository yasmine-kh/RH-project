package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.RecalculResponse;
import com.talent360bank.talent360bank.controller.dto.ScoreResume;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
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
    private final VerrouCalculTrimestre verrou;

    public ScoreController(ScoreService scoreService, ScoreRepository scoreRepository,
                           ChargeurRessources chargeur, VerrouCalculTrimestre verrou) {
        this.scoreService = scoreService;
        this.scoreRepository = scoreRepository;
        this.chargeur = chargeur;
        this.verrou = verrou;
    }

    /**
     * Recalcule et enregistre les scores de tout le trimestre.
     *
     * <p>POST et non PUT : l'appel n'est pas idempotent au sens strict, il
     * repose la date de calcul a chaque passage. Il reste rejouable sans
     * risque, un collaborateur n'ayant qu'un score par trimestre.     *
     * <p>Sous le verrou du trimestre ({@link VerrouCalculTrimestre}), comme le
     * calcul complet : 409 si un calcul du trimestre tourne deja.
     */
    @PostMapping("/trimestres/{annee}/{numero}/scores/recalcul")
    public RecalculResponse recalculer(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return RecalculResponse.de(verrou.executer(trimestre, () -> scoreService.recalculerTrimestre(trimestre)));
    }

    /** Scores enregistres du trimestre. */
    @GetMapping("/trimestres/{annee}/{numero}/scores")
    @Transactional(readOnly = true)
    public List<ScoreResume> duTrimestre(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return scoreRepository.findByTrimestreAvecCollaborateur(trimestre).stream()
                .map(ScoreResume::de)
                .toList();
    }

    /** Historique d'un collaborateur, du trimestre le plus recent au plus ancien. */
    @GetMapping("/collaborateurs/{idCollaborateur}/scores")
    @Transactional(readOnly = true)
    public List<ScoreResume> historique(@PathVariable String idCollaborateur) {
        Collaborateur collaborateur = chargeur.exigerCollaborateur(idCollaborateur);
        return scoreService.historique(collaborateur).stream()
                .map(ScoreResume::de)
                .toList();
    }
}
