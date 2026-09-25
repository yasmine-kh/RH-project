package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.TableauDeBordResponse;
import com.talent360bank.talent360bank.service.TableauDeBordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synthese du tableau de bord. Simple adaptateur HTTP : l'ecran d'accueil
 * appelle {@link TableauDeBordService} directement en Java.
 */
@RestController
@RequestMapping("/api/dashboard")
public class TableauDeBordController {

    private final TableauDeBordService tableauDeBordService;
    private final ChargeurRessources chargeur;

    public TableauDeBordController(TableauDeBordService tableauDeBordService, ChargeurRessources chargeur) {
        this.tableauDeBordService = tableauDeBordService;
        this.chargeur = chargeur;
    }

    @GetMapping("/synthese")
    public TableauDeBordResponse synthese(@RequestParam int annee, @RequestParam int numero) {
        return TableauDeBordResponse.de(tableauDeBordService.synthese(chargeur.exigerTrimestre(annee, numero)));
    }
}
