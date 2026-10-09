package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.service.HistoriqueViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** L'ecran Historique : evenements du journal (imports, decisions du Comite, reglages, collaborateurs). */
@Controller
public class HistoriqueController {

    private final HistoriqueViewService historiqueViewService;

    public HistoriqueController(HistoriqueViewService historiqueViewService) {
        this.historiqueViewService = historiqueViewService;
    }

    @GetMapping("/historique")
    public String historique(@RequestParam(required = false) String type,
                             @RequestParam(required = false) String trimestre,
                             @RequestParam(required = false) String du,
                             @RequestParam(required = false) String au,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) Integer page,
                             Model model) {
        model.addAttribute("vue", historiqueViewService.construire(type, trimestre, du, au, q, page));
        model.addAttribute("activePage", "historique");
        return "historique";
    }
}
