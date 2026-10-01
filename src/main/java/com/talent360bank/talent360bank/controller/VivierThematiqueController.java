package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.VivierThematiqueResponse;
import com.talent360bank.talent360bank.controller.dto.ViviersThematiquesResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/** Viviers thematiques : tous les collaborateurs ranges par vivier, statuts talent / HP compris. */
@RestController
@RequestMapping("/api/viviers-thematiques")
public class VivierThematiqueController {

    private final VivierThematiqueService vivierThematiqueService;
    private final ChargeurRessources chargeur;

    public VivierThematiqueController(VivierThematiqueService vivierThematiqueService, ChargeurRessources chargeur) {
        this.vivierThematiqueService = vivierThematiqueService;
        this.chargeur = chargeur;
    }

    /** Tous les viviers, chacun present meme vide, et les collaborateurs non classes. */
    @GetMapping
    public ViviersThematiquesResponse viviers(@RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return ViviersThematiquesResponse.de(vivierThematiqueService.getViviersThematiques(trimestre),
                trimestre.getDateReference());
    }

    /** Un seul vivier, par son code (COMMERCIAL, DIGITAL...), sans tenir compte de la casse. */
    @GetMapping("/{code}")
    public VivierThematiqueResponse vivier(@PathVariable String code,
                                           @RequestParam int annee, @RequestParam int numero) {
        VivierThematique vivier = Arrays.stream(VivierThematique.values())
                .filter(candidat -> candidat.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun vivier thematique " + code));
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return VivierThematiqueResponse.de(vivier, vivierThematiqueService.getVivier(vivier, trimestre));
    }
}
