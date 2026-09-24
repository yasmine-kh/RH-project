package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PagesController {

    private final DashboardService dashboardService;

    public PagesController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String accueil(Model model) {
        model.addAttribute("kpis", dashboardService.buildKpis());
        model.addAttribute("activePage", "accueil");
        return "dashboard";
    }

    @GetMapping("/9box")
    public String neufBox(Model model) {
        model.addAttribute("activePage", "9box");
        model.addAttribute("pageTitle", "Matrice 9-Box");
        return "placeholder";
    }

    @GetMapping("/viviers")
    public String viviers(Model model) {
        model.addAttribute("activePage", "viviers");
        model.addAttribute("pageTitle", "Viviers de talents");
        return "placeholder";
    }

    @GetMapping("/postes-critiques")
    public String postesCritiques(Model model) {
        model.addAttribute("activePage", "postes-critiques");
        model.addAttribute("pageTitle", "Postes critiques");
        return "placeholder";
    }

    @GetMapping("/comite-talent")
    public String comiteTalent(Model model) {
        model.addAttribute("activePage", "comite-talent");
        model.addAttribute("pageTitle", "Comite Talent");
        return "placeholder";
    }

    @GetMapping("/alertes")
    public String alertes(Model model) {
        model.addAttribute("activePage", "alertes");
        model.addAttribute("pageTitle", "Alertes");
        return "placeholder";
    }

    @GetMapping("/parametres")
    public String parametres(Model model) {
        model.addAttribute("activePage", "parametres");
        model.addAttribute("pageTitle", "Parametres / Ponderations");
        return "placeholder";
    }
}