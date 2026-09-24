package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.RecalculResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.NeufBoxService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placement des employes sur la matrice 9-box.
 *
 * <p>Il n'y a pas d'endpoint de lecture ici : la case d'un employe est portee
 * par son Score (champ positionBox), donc GET /trimestres/{annee}/{numero}/scores
 * suffit a construire la grille. Un second chemin vers la meme donnee
 * risquerait de diverger.
 */
@RestController
@RequestMapping("/api")
public class NeufBoxController {

    private final NeufBoxService neufBoxService;
    private final ChargeurRessources chargeur;

    public NeufBoxController(NeufBoxService neufBoxService, ChargeurRessources chargeur) {
        this.neufBoxService = neufBoxService;
        this.chargeur = chargeur;
    }

    /**
     * Place tous les employes scores du trimestre et enregistre leur case.
     * A lancer apres le recalcul des scores : le placement lit les scores, il
     * ne les calcule pas.
     */
    @PostMapping("/trimestres/{annee}/{numero}/9box/placement")
    public RecalculResponse placer(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return RecalculResponse.de(neufBoxService.placerTrimestre(trimestre));
    }
}
