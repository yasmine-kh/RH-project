package com.talent360.ui.controller;

import com.talent360.ui.model.KpiCard;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controleur de navigation principal.
 *
 * REGLE : cette classe ne fait AUCUN calcul metier.
 * Les valeurs des KPI sont mockees ici en attendant que Jas expose
 * CalculService / AlerteService. Quand ce sera pret, on remplacera
 * juste le contenu de buildMockKpis() par un appel au service, le
 * template dashboard.html n'aura pas besoin de changer.
 */
@Controller
public class PagesController {

    @GetMapping("/")
    public String accueil(Model model) {
        model.addAttribute("kpis", buildMockKpis());
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

    /**
     * Donnees mockees pour les 9 cartes du Dashboard.
     * Les libelles suivent les colonnes reelles du dataset
     * (01_COLLABORATEURS, 04_9BOX, 08_POSTES_CRITIQUES, 10_TALENTS, 12_VIGILANCE)
     * pour qu'on ait deja les bons noms quand Jas branchera les vrais calculs.
     */
    private List<KpiCard> buildMockKpis() {
        return List.of(
                new KpiCard("Collaborateurs actifs", "100", "bi-people", "kpi-blue"),
                new KpiCard("Talents valides", "14", "bi-star", "kpi-purple"),
                new KpiCard("Hauts potentiels", "9", "bi-graph-up-arrow", "kpi-green"),
                new KpiCard("Collaborateurs a risque", "18", "kpi-icon-warning", "kpi-red"),
                new KpiCard("Postes critiques", "15", "bi-exclamation-triangle", "kpi-orange"),
                new KpiCard("Postes sans successeur", "1", "bi-x-circle", "kpi-red"),
                new KpiCard("Alertes actives", "23", "bi-bell", "kpi-orange"),
                new KpiCard("Viviers de talents", "5", "bi-collection", "kpi-blue"),
                new KpiCard("Repartis en 9-Box", "100", "bi-grid-3x3", "kpi-purple")
        );
    }
}
