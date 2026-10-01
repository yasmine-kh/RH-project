package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.VueEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import com.talent360bank.talent360bank.ui.service.VueEntiteViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vue entite : l'organigramme pour choisir une entite, puis les resultats
 * agreges de son sous-arbre sur un trimestre. Meme modele que la page "Vue
 * entite" (voir docs/guide-developpeur.md).
 *
 * <p>Le code d'une entite est son chemin depuis la direction et contient des
 * "/" (DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD/REG:TANGER_TETOUAN) : il est
 * donc passe en parametre de requete ("?code=..."), ou les "/" sont permis
 * tels quels ou encodes (%2F). Dans le chemin, un %2F serait refuse par le
 * pare-feu de Spring Security et par Tomcat ; le dernier segment seul ne
 * suffit pas, une meme region existant sous plusieurs departements. Cote
 * Thymeleaf : {@code @{/api/trimestres/{a}/{n}/entites/vue(a=..., n=..., code=${...})}}.
 */
@RestController
@RequestMapping("/api")
public class VueEntiteController {

    private final VueEntiteViewService vueEntiteViewService;

    public VueEntiteController(VueEntiteViewService vueEntiteViewService) {
        this.vueEntiteViewService = vueEntiteViewService;
    }

    /** L'organigramme, directions par libelle, avec l'effectif de chaque sous-arbre. */
    @GetMapping("/entites")
    public List<NoeudEntite> entites() {
        return vueEntiteViewService.listerEntites();
    }

    /**
     * Vue de l'entite passee dans le parametre "code", par exemple
     * /api/trimestres/2026/3/entites/vue?code=DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD.
     * 400 sans code, 404 si le trimestre ou l'entite est inconnu.
     */
    @GetMapping("/trimestres/{annee}/{numero}/entites/vue")
    public VueEntite vue(@PathVariable int annee, @PathVariable int numero, @RequestParam String code) {
        return vueEntiteViewService.construire(code, annee, numero);
    }
}
