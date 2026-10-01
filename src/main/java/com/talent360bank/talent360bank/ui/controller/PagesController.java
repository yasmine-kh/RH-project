package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.service.ComiteTalentViewService;
import com.talent360bank.talent360bank.ui.service.NineBoxViewService;
import com.talent360bank.talent360bank.ui.service.PosteCritiqueViewService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import com.talent360bank.talent360bank.ui.service.VivierService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Ecrans 9-Box, Viviers, Postes critiques, Comite Talent et Parametres. Le
 * tableau de bord et les alertes ont leur propre controleur
 * (DashboardController, AlertesController).
 *
 * <p>Les ecrans par trimestre acceptent {@code ?trimestre=AAAA-N} (404 si
 * inconnu ; sinon le plus recent qui a des scores) et posent les attributs
 * "trimestre" et "trimestres" (voir {@link TrimestreCourantService}).
 */
@Controller
public class PagesController {

    private final NineBoxViewService nineBoxViewService;
    private final VivierService vivierService;
    private final ComiteTalentViewService comiteTalentViewService;
    private final PosteCritiqueViewService posteCritiqueViewService;
    private final TrimestreCourantService trimestreCourant;

    public PagesController(NineBoxViewService nineBoxViewService, VivierService vivierService,
                           ComiteTalentViewService comiteTalentViewService,
                           PosteCritiqueViewService posteCritiqueViewService,
                           TrimestreCourantService trimestreCourant) {
        this.nineBoxViewService = nineBoxViewService;
        this.vivierService = vivierService;
        this.comiteTalentViewService = comiteTalentViewService;
        this.posteCritiqueViewService = posteCritiqueViewService;
        this.trimestreCourant = trimestreCourant;
    }

    @GetMapping("/9box")
    public String neufBox(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("cells", nineBoxViewService.buildGrid(selection.trimestre()));
        model.addAttribute("activePage", "9box");
        return "9box";
    }

    @GetMapping("/viviers")
    public String viviers(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("rows", vivierService.buildRows(selection.trimestre()));
        model.addAttribute("activePage", "viviers");
        return "viviers";
    }

    @GetMapping("/postes-critiques")
    public String postesCritiques(
            @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre, Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("rows", posteCritiqueViewService.buildRows(selection.trimestre()));
        model.addAttribute("activePage", "postes-critiques");
        return "postes-critiques";
    }

    @GetMapping("/comite-talent")
    public String comiteTalent(@RequestParam(required = false) String trimestre,
                               @RequestParam(required = false) String statut,
                               Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("vue", comiteTalentViewService.build(selection, statut));
        model.addAttribute("activePage", "comite-talent");
        return "comite-talent";
    }

    @GetMapping("/parametres")
    public String parametres(Model model) {
        model.addAttribute("activePage", "parametres");
        model.addAttribute("pageTitle", "Parametres / Ponderations");
        return "placeholder";
    }
}