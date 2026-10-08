package com.talent360bank.talent360bank.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Modules du prototype (docs/prototype/index.html) que l'application ne calcule
 * ni ne stocke encore : Carriere & Mobilite, Engagement & Fidelisation,
 * Historique des decisions, et les formulaires du collaborateur (questionnaire
 * d'engagement, auto-evaluation) et du manager (evaluation). Chaque page reprend la
 * disposition du prototype avec un bandeau "En cours de developpement", sans aucun
 * chiffre : aucun service n'est appele (templates/placeholder.html).
 */
@Controller
public class PagesEnDeveloppementController {

    @GetMapping("/carriere-mobilite")
    public String carriereMobilite(Model model) {
        return page(model, "carriere-mobilite", "Carrière & Mobilité", "Souhaits d'évolution et mobilités");
    }

    @GetMapping("/engagement")
    public String engagement(Model model) {
        return page(model, "engagement", "Engagement & Fidélisation",
                "Résultats agrégés du questionnaire d'engagement trimestriel");
    }

    @GetMapping("/historique")
    public String historique(Model model) {
        return page(model, "historique", "Historique", "Traçabilité des décisions et statuts");
    }

    @GetMapping("/mon-engagement")
    public String monEngagement(Model model) {
        return page(model, "mon-engagement", "Mon engagement",
                "Questionnaire trimestriel — 6 dimensions, environ 5 minutes");
    }

    @GetMapping("/auto-evaluation")
    public String autoEvaluation(Model model) {
        return page(model, "auto-evaluation", "Campagne d'évaluation", "Mon Talent 360 — auto-évaluation");
    }

    @GetMapping("/evaluation-manager")
    public String evaluationManager(Model model) {
        return page(model, "evaluation-manager", "Évaluation manager",
                "Talent 360 — évaluation de la performance et du potentiel");
    }

    private static String page(Model model, String id, String titre, String sousTitre) {
        model.addAttribute("activePage", id);
        model.addAttribute("pageTitle", titre);
        model.addAttribute("pageSousTitre", sousTitre);
        return "placeholder";
    }
}
