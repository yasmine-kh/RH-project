package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Postes critiques et succession. Ecran a construire : la page d'attente en attendant
 * (les donnees sont deja servies par /api/postes-critiques).
 *
 * <p>Accepte {@code ?trimestre=AAAA-N} comme les autres ecrans (404 si le
 * trimestre est inconnu) et pose les attributs "trimestre" et "trimestres"
 * (voir {@link TrimestreCourantService}).
 */
@Controller
public class PostesCritiquesController {

    private final TrimestreCourantService trimestreCourant;

    public PostesCritiquesController(TrimestreCourantService trimestreCourant) {
        this.trimestreCourant = trimestreCourant;
    }

    @GetMapping("/postes-critiques")
    public String postesCritiques(
            @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre, Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("activePage", "postes-critiques");
        model.addAttribute("pageTitle", "Postes critiques");
        return "placeholder";
    }
}
