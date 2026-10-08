package com.talent360bank.talent360bank.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Modules du prototype (docs/prototype/index.html) pas encore construits : Engagement &amp;
 * Fidelisation (resultats agreges du questionnaire) et Historique des decisions. Chaque page
 * reprend la disposition du prototype avec un bandeau "En cours de developpement", sans aucun
 * chiffre : aucun service n'est appele (templates/placeholder.html).
 *
 * <p>Carriere &amp; Mobilite n'a aucune source de donnees : retiree des menus, son ancienne adresse
 * renvoie au tableau de bord. Les formulaires du collaborateur (questionnaire, auto-evaluation)
 * et du manager (evaluation) sont retires : seul le RH se connecte, les donnees arrivent par import.
 */
@Controller
public class PagesEnDeveloppementController {

    /** Plus de page : pas de donnees de mobilite. Les anciens liens menent au tableau de bord. */
    @GetMapping("/carriere-mobilite")
    public String carriereMobilite() {
        return "redirect:/";
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

    private static String page(Model model, String id, String titre, String sousTitre) {
        model.addAttribute("activePage", id);
        model.addAttribute("pageTitle", titre);
        model.addAttribute("pageSousTitre", sousTitre);
        return "placeholder";
    }
}
