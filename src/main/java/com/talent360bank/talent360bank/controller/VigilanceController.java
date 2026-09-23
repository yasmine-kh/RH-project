package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.VigilanceResponse;
import com.talent360bank.talent360bank.entity.NiveauVigilance;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.VigilanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indice de vigilance : risque de depart, du plus a risque au moins a risque.
 *
 * <p>Attention en lisant ces reponses : la detection automatique ne couvre
 * aujourd'hui que deux des sept signaux, son plafond est de 35 points et le
 * niveau ELEVEE est donc hors d'atteinte. Le champ {@code detectable} de
 * chaque signal le rappelle cote client.
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
     * Vigilance de tous les employes scores du trimestre.
     *
     * @param minimum niveau plancher facultatif (FAIBLE, MODEREE, ELEVEE)
     */
    @GetMapping("/trimestres/{annee}/{numero}/vigilance")
    public List<VigilanceResponse> duTrimestre(@PathVariable int annee, @PathVariable int numero,
                                               @RequestParam(required = false) NiveauVigilance minimum) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);

        List<com.talent360bank.talent360bank.service.ResultatVigilance> resultats =
                minimum == null
                        ? vigilanceService.evaluerTrimestre(trimestre)
                        : vigilanceService.evaluerTrimestre(trimestre, minimum);

        return resultats.stream().map(VigilanceResponse::de).toList();
    }

    /** Vigilance d'un employe, avec le detail des signaux leves. */
    @GetMapping("/trimestres/{annee}/{numero}/vigilance/{employeeId}")
    public VigilanceResponse pourEmploye(@PathVariable int annee, @PathVariable int numero,
                                         @PathVariable String employeeId) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return VigilanceResponse.de(
                vigilanceService.evaluer(chargeur.exigerEmploye(employeeId), trimestre));
    }
}
