package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.VigilanceResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indice de vigilance : risque de depart, du plus a risque au moins a risque.
 *
 * <p>Les signaux de mobilite, developpement, reconnaissance et formation
 * viennent des faits importes : sans source d'import declaree, ils ne sont
 * jamais leves et l'indice est sous-estime.
 */
@RestController
@RequestMapping("/api")
public class VigilanceController {

    private final VigilanceService vigilanceService;
    private final ChargeurRessources chargeur;

    public VigilanceController(VigilanceService vigilanceService, ChargeurRessources chargeur) {
        this.vigilanceService = vigilanceService;
        this.chargeur = chargeur;
    }

    /**
     * Vigilance de tous les collaborateurs scores du trimestre.
     *
     * @param minimum niveau plancher facultatif (FAIBLE, MODEREE, ELEVEE)
     */
    @GetMapping("/trimestres/{annee}/{numero}/vigilance")
    public List<VigilanceResponse> duTrimestre(@PathVariable int annee, @PathVariable int numero,
                                               @RequestParam(required = false) NiveauVigilance minimum) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);

        List<com.talent360bank.talent360bank.service.resultat.ResultatVigilance> resultats =
                minimum == null
                        ? vigilanceService.evaluerTrimestre(trimestre)
                        : vigilanceService.evaluerTrimestre(trimestre, minimum);

        return resultats.stream().map(VigilanceResponse::de).toList();
    }

    /** Vigilance d'un collaborateur, avec le detail des signaux leves. */
    @GetMapping("/trimestres/{annee}/{numero}/vigilance/{idCollaborateur}")
    public VigilanceResponse pourCollaborateur(@PathVariable int annee, @PathVariable int numero,
                                               @PathVariable String idCollaborateur) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return VigilanceResponse.de(
                vigilanceService.evaluer(chargeur.exigerCollaborateur(idCollaborateur), trimestre));
    }
}
