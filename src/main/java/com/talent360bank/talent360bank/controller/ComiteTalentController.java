package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.DecisionComiteResponse;
import com.talent360bank.talent360bank.controller.dto.ScoreResume;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Comite Talent : decisions sur les talents proposes et talents valides. */
@RestController
@RequestMapping("/api/comite-talent")
public class ComiteTalentController {

    private final ValidationComiteService validationComiteService;
    private final ChargeurRessources chargeur;

    public ComiteTalentController(ValidationComiteService validationComiteService, ChargeurRessources chargeur) {
        this.validationComiteService = validationComiteService;
        this.chargeur = chargeur;
    }

    /** Decision du comite sur chaque talent propose, en attente compris. */
    @GetMapping
    public List<DecisionComiteResponse> decisions(@RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return validationComiteService.getDecisionsComite(trimestre).stream()
                .map(DecisionComiteResponse::de).toList();
    }

    /** Talents proposes et valides par le comite (10_TALENTS, Talent valide). */
    @GetMapping("/talents-valides")
    public List<ScoreResume> talentsValides(@RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return validationComiteService.getTalentsValides(trimestre).stream()
                .map(ScoreResume::de).toList();
    }
}
