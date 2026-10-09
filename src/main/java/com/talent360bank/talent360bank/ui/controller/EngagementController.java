package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.service.ConfigurationQuestionnaireService;
import com.talent360bank.talent360bank.ui.service.EngagementViewService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Engagement &amp; Fidelisation (resultats du questionnaire) et, depuis la page Parametres, les
 * reglages des questions (dimension, question eNPS).
 */
@Controller
public class EngagementController {

    /** Prefixe des champs du formulaire : dimension[Q01], dimension[Q02]... */
    private static final String CHAMP_DIMENSION = "dimension[";

    private final EngagementViewService engagementViewService;
    private final ConfigurationQuestionnaireService configurationService;
    private final TrimestreCourantService trimestreCourant;

    public EngagementController(EngagementViewService engagementViewService,
                                ConfigurationQuestionnaireService configurationService,
                                TrimestreCourantService trimestreCourant) {
        this.engagementViewService = engagementViewService;
        this.configurationService = configurationService;
        this.trimestreCourant = trimestreCourant;
    }

    @GetMapping("/engagement")
    public String engagement(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                             Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        if (selection.trimestre() != null) {
            model.addAttribute("vue", engagementViewService.construire(selection.trimestre()));
        }
        model.addAttribute("activePage", "engagement");
        return "engagement";
    }

    /** Formulaire de la page Parametres : une dimension par question, la question eNPS. */
    @PostMapping("/parametres/questionnaire")
    public String configurer(@RequestParam Map<String, String> champs,
                             @RequestParam(required = false) String enps,
                             @RequestParam(required = false) String trimestre,
                             RedirectAttributes redirection) {
        Map<String, String> dimensions = new HashMap<>();
        champs.forEach((nom, valeur) -> {
            if (nom.startsWith(CHAMP_DIMENSION) && nom.endsWith("]")) {
                dimensions.put(nom.substring(CHAMP_DIMENSION.length(), nom.length() - 1), valeur);
            }
        });
        try {
            redirection.addFlashAttribute("succesQuestionnaire", configurationService.enregistrer(dimensions, enps));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreurQuestionnaire", e.getMessage());
        }
        UriComponentsBuilder retour = UriComponentsBuilder.fromPath("/parametres");
        if (trimestre != null && !trimestre.isBlank()) {
            retour.queryParam("trimestre", trimestre.trim());
        }
        return "redirect:" + retour.fragment("questionnaire").encode().build().toUriString();
    }
}
