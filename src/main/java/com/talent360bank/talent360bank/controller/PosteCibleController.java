package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.PosteCibleResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.PosteCibleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Poste cible de chaque collaborateur (liste des collaborateurs, tableau de bord DG). */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/postes-cibles")
public class PosteCibleController {

    private final PosteCibleService posteCibleService;
    private final ChargeurRessources chargeur;

    public PosteCibleController(PosteCibleService posteCibleService, ChargeurRessources chargeur) {
        this.posteCibleService = posteCibleService;
        this.chargeur = chargeur;
    }

    /** Un element par collaborateur actif ayant un score complet, par matricule. */
    @GetMapping
    public List<PosteCibleResponse> postesCibles(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return posteCibleService.postesCibles(trimestre).stream()
                .map(cible -> PosteCibleResponse.de(cible, trimestre.getDateReference()))
                .toList();
    }
}
