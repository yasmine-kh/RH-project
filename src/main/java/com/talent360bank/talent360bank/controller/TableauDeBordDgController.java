package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.TableauDeBordDg;
import com.talent360bank.talent360bank.ui.service.TableauDeBordDgViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tableau de bord DG : cartes du tableau de bord RH, releve des postes critiques, meilleurs talents. */
@RestController
@RequestMapping("/api/dashboard")
public class TableauDeBordDgController {

    private final TableauDeBordDgViewService service;
    private final ChargeurRessources chargeur;

    public TableauDeBordDgController(TableauDeBordDgViewService service, ChargeurRessources chargeur) {
        this.service = service;
        this.chargeur = chargeur;
    }

    /** {@code limite} : nombre de talents, 1 a 50 (8 par defaut) ; 400 hors bornes, 404 pour un trimestre inconnu. */
    @GetMapping("/dg")
    public TableauDeBordDg dg(@RequestParam int annee, @RequestParam int numero,
                              @RequestParam(defaultValue = "" + TableauDeBordDg.TOP_TALENTS_PAR_DEFAUT) int limite) {
        return service.construire(chargeur.exigerTrimestre(annee, numero), limite);
    }
}
