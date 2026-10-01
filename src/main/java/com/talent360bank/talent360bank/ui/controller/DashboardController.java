package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.service.DashboardService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Tableau de bord RH (page d'accueil) : les chiffres du trimestre, tous issus du moteur.
 *
 * <p>Accepte {@code ?trimestre=AAAA-N} comme les autres ecrans (404 si le
 * trimestre est inconnu) et pose les attributs "trimestre" et "trimestres"
 * (voir {@link TrimestreCourantService}).
 */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final TrimestreCourantService trimestreCourant;

    public DashboardController(DashboardService dashboardService, TrimestreCourantService trimestreCourant) {
        this.dashboardService = dashboardService;
        this.trimestreCourant = trimestreCourant;
    }

    @GetMapping("/")
    public String accueil(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("tableau", dashboardService.construire(selection.trimestre()));
        model.addAttribute("activePage", "accueil");
        return "dashboard";
    }
}
