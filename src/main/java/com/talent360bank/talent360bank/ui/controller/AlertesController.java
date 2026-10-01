package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.model.AlertesView.Filtres;
import com.talent360bank.talent360bank.ui.service.AlertesViewService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Alertes du trimestre affiche, calculees a la volee par le moteur
 * ({@link AlertesViewService}) : compteurs par type et par gravite, puis le
 * tableau filtre par type, gravite, direction et nom.
 *
 * <p>Accepte {@code ?trimestre=AAAA-N} comme les autres ecrans (404 si le
 * trimestre est inconnu) et pose les attributs "trimestre" et "trimestres"
 * (voir {@link TrimestreCourantService}).
 */
@Controller
public class AlertesController {

    private final AlertesViewService alertesViewService;
    private final TrimestreCourantService trimestreCourant;

    public AlertesController(AlertesViewService alertesViewService, TrimestreCourantService trimestreCourant) {
        this.alertesViewService = alertesViewService;
        this.trimestreCourant = trimestreCourant;
    }

    /**
     * @param type      code de {@link com.talent360bank.talent360bank.ui.model.TypeAlerte}, absent = tous
     * @param severite  code de {@link com.talent360bank.talent360bank.ui.model.SeveriteAlerte}, absent = toutes
     * @param direction libelle de la direction, absent = toutes
     * @param q         recherche dans le nom (ou le matricule / Poste_ID), sans accents ni casse
     */
    @GetMapping("/alertes")
    public String alertes(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          @RequestParam(required = false) String type,
                          @RequestParam(required = false) String severite,
                          @RequestParam(required = false) String direction,
                          @RequestParam(required = false) String q,
                          Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("vue", alertesViewService.construire(selection.trimestre(),
                new Filtres(type, severite, direction, q)));
        model.addAttribute("activePage", "alertes");
        return "alertes";
    }
}
