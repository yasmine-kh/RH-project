package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.SuiviCampagne;
import com.talent360bank.talent360bank.ui.service.SuiviCampagneViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Avancement de la campagne d'evaluation d'un trimestre (voir docs/guide-developpeur.md). */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/campagne")
public class SuiviCampagneController {

    private final SuiviCampagneViewService service;

    public SuiviCampagneController(SuiviCampagneViewService service) {
        this.service = service;
    }

    /** Sans {@code entite} : une ligne par direction ; avec : une ligne par entite fille. 404 si inconnu. */
    @GetMapping
    public SuiviCampagne campagne(@PathVariable int annee, @PathVariable int numero,
                                  @RequestParam(required = false) String entite) {
        return service.construire(annee, numero, entite == null || entite.isBlank() ? null : entite.trim());
    }
}
