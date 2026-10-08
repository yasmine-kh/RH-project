package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.service.DashboardService;
import com.talent360bank.talent360bank.ui.service.TableauDeBordInteractifService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Tableau de bord RH (page d'accueil), interactif : cartes, graphiques et listes du
 * trimestre, filtres croises par l'URL (?case=9&amp;entite=...&amp;vigilance=...), voir
 * {@link TableauDeBordInteractifService}. Les chiffres viennent du moteur
 * ({@link DashboardService}) ; la page refiltre sur place (static/js/tableau-de-bord.js).
 *
 * <p>Accepte {@code ?trimestre=AAAA-N} comme les autres ecrans (404 si le
 * trimestre est inconnu) et pose les attributs "trimestre" et "trimestres"
 * (voir {@link TrimestreCourantService}). Un filtre de valeur inconnue est ignore.
 * L'ancienne adresse /dashboard-dg renvoie ici.
 */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final TableauDeBordInteractifService interactif;
    private final TrimestreCourantService trimestreCourant;

    public DashboardController(DashboardService dashboardService, TableauDeBordInteractifService interactif,
                               TrimestreCourantService trimestreCourant) {
        this.dashboardService = dashboardService;
        this.interactif = interactif;
        this.trimestreCourant = trimestreCourant;
    }

    @GetMapping("/")
    public String accueil(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          @RequestParam Map<String, String> parametres, Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        TableauDeBordView tableau = dashboardService.construire(selection.trimestre());
        model.addAttribute("tableau", tableau);
        if (selection.trimestre() != null && tableau.erreur() == null) {
            TableauDeBordInteractif vue = interactif.construire(selection.trimestre(), tableau, parametres);
            model.addAttribute("vue", vue);
        }
        model.addAttribute("activePage", "accueil");
        return "dashboard";
    }

    /** Ancien tableau de bord DG, fusionne dans le tableau de bord RH : les anciens liens y menent. */
    @GetMapping("/dashboard-dg")
    public String dashboardDg(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath("/");
        if (trimestre != null && !trimestre.isBlank()) {
            lien.queryParam(TrimestreCourantService.PARAMETRE, trimestre.trim());
        }
        return "redirect:" + lien.encode().build().toUriString();
    }
}
