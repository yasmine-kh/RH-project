package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.service.VueManagerViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vue manager : la liste des managers, puis les resultats de l'equipe de l'un
 * d'eux sur un trimestre. Meme modele que la page "Vue manager" (voir
 * docs/guide-developpeur.md).
 */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/managers")
public class VueManagerController {

    private final VueManagerViewService vueManagerViewService;

    public VueManagerController(VueManagerViewService vueManagerViewService) {
        this.vueManagerViewService = vueManagerViewService;
    }

    /** Managers pour la page de choix, par nom, avec la taille de leur equipe directe. */
    @GetMapping
    public List<ManagerResume> managers(@PathVariable int annee, @PathVariable int numero) {
        return vueManagerViewService.listerManagers(annee, numero);
    }

    /** 404 si le trimestre ou le matricule est inconnu, ou si ce collaborateur n'est pas manager. */
    @GetMapping("/{matricule}/vue")
    public VueManager vue(@PathVariable int annee, @PathVariable int numero, @PathVariable String matricule) {
        return vueManagerViewService.construire(matricule, annee, numero);
    }
}
