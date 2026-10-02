package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.service.VivierSyntheseService;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Une synthese par vivier : les cinq viviers thematiques puis le vivier de releve. */
@RestController
@RequestMapping("/api/viviers")
public class VivierSyntheseController {

    private final VivierSyntheseService vivierSyntheseService;
    private final ChargeurRessources chargeur;

    public VivierSyntheseController(VivierSyntheseService vivierSyntheseService, ChargeurRessources chargeur) {
        this.vivierSyntheseService = vivierSyntheseService;
        this.chargeur = chargeur;
    }

    @GetMapping("/synthese")
    public List<SyntheseVivier> synthese(@RequestParam int annee, @RequestParam int numero) {
        return vivierSyntheseService.synthese(chargeur.exigerTrimestre(annee, numero));
    }
}
