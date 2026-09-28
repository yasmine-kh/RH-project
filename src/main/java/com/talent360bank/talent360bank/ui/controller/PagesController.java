package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.service.ComiteTalentViewService;
import com.talent360bank.talent360bank.ui.service.DashboardService;
import com.talent360bank.talent360bank.ui.service.NineBoxViewService;
import com.talent360bank.talent360bank.ui.service.VivierService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PagesController {

    private final DashboardService dashboardService;
    private final NineBoxViewService nineBoxViewService;
    private final VivierService vivierService;
    private final ComiteTalentViewService comiteTalentViewService;

    public PagesController(DashboardService dashboardService, NineBoxViewService nineBoxViewService,
                           VivierService vivierService, ComiteTalentViewService comiteTalentViewService) {
        this.dashboardService = dashboardService;
        this.nineBoxViewService = nineBoxViewService;
        this.vivierService = vivierService;
        this.comiteTalentViewService = comiteTalentViewService;
    }

    @GetMapping("/")
    public String accueil(Model model) {
        model.addAttribute("kpis", dashboardService.buildKpis());
        model.addAttribute("activePage", "accueil");
        return "dashboard";
    }

    @GetMapping("/9box")
    public String neufBox(Model model) {
        model.addAttribute("cells", nineBoxViewService.buildGrid());
        model.addAttribute("activePage", "9box");
        return "9box";
    }

    @GetMapping("/viviers")
    public String viviers(Model model) {
        model.addAttribute("rows", vivierService.buildRows());
        model.addAttribute("activePage", "viviers");
        return "viviers";
    }

    @GetMapping("/postes-critiques")
    public String postesCritiques(Model model) {
        model.addAttribute("activePage", "postes-critiques");
        model.addAttribute("pageTitle", "Postes critiques");
        return "placeholder";
    }

    @GetMapping("/comite-talent")
    public String comiteTalent(@RequestParam(required = false) String trimestre,
                               @RequestParam(required = false) String statut,
                               Model model) {
        model.addAttribute("vue", comiteTalentViewService.build(trimestre, statut));
        model.addAttribute("activePage", "comite-talent");
        return "comite-talent";
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