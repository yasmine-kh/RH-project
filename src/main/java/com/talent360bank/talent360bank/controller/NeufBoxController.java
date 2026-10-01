package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.RecalculResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placement des collaborateurs sur la matrice 9-box.
 *
 * <p>Il n'y a pas d'endpoint de lecture ici : la case d'un collaborateur est portee
 * par son Score (champ positionBox), donc GET /trimestres/{annee}/{numero}/scores
 * suffit a construire la grille. Un second chemin vers la meme donnee
 * risquerait de diverger.
 */
@RestController
@RequestMapping("/api")
public class NeufBoxController {

    private final NeufBoxService neufBoxService;
    private final ChargeurRessources chargeur;
    private final VerrouCalculTrimestre verrou;

    public NeufBoxController(NeufBoxService neufBoxService, ChargeurRessources chargeur,
                             VerrouCalculTrimestre verrou) {
        this.neufBoxService = neufBoxService;
        this.chargeur = chargeur;
        this.verrou = verrou;
    }

    /**
     * Place tous les collaborateurs scores du trimestre et enregistre leur case.
     * A lancer apres le recalcul des scores : le placement lit les scores, il
     * ne les calcule pas.     *
     * <p>Sous le verrou du trimestre ({@link VerrouCalculTrimestre}), comme le
     * calcul complet : 409 si un calcul du trimestre tourne deja.
     */
    @PostMapping("/trimestres/{annee}/{numero}/9box/placement")
    public RecalculResponse placer(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return RecalculResponse.de(verrou.executer(trimestre, () -> neufBoxService.placerTrimestre(trimestre)));
    }
}
