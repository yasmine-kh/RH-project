package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.ui.service.LiensPages;
import com.talent360bank.talent360bank.ui.service.ProfilsPagesService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;

/**
 * Pages des "profils" que le RH ouvre (ce ne sont pas des connexions : un seul
 * compte RH) : Vue manager, Vue entite, leurs pages de choix, et la
 * redirection du selecteur de profil de la sidebar.
 *
 * <p>Chaque page accepte {@code ?trimestre=AAAA-N}. Le code d'une entite passe
 * en parametre ({@code /entites?code=...}) : il contient des "/". Un matricule
 * ou un code inconnu donne la page 404 (ProfilsAdvice).
 */
@Controller
public class ProfilsPagesController {

    private final TrimestreCourantService trimestreCourant;
    private final ProfilsPagesService pages;

    public ProfilsPagesController(TrimestreCourantService trimestreCourant, ProfilsPagesService pages) {
        this.trimestreCourant = trimestreCourant;
        this.pages = pages;
    }

    /**
     * Le selecteur de profil : RH vers l'accueil, Comite vers le Comite Talent,
     * Collaborateur / Manager / Entite vers la fiche, la vue manager ou la vue
     * entite choisie (ou leur page de choix sans choix), pour le trimestre courant.
     */
    @GetMapping("/profil")
    public String profil(@RequestParam(required = false) String profil, @RequestParam(required = false) String cible,
                         @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre) {
        String choix = cible == null || cible.isBlank() ? null : cible.trim().split("\\s+")[0];
        String t = trimestre == null || trimestre.isBlank() ? null : trimestre.trim();
        String type = profil == null ? "RH" : profil.trim().toUpperCase(Locale.ROOT);
        String cibleUrl = switch (type) {
            case "COLLABORATEUR" -> choix == null ? avecTrimestre("/fiche-collaborateur", null)
                    : LiensPages.fiche(choix, t);
            case "MANAGER" -> choix == null ? avecTrimestre("/managers", t) : LiensPages.vueManager(choix, t);
            case "ENTITE" -> choix == null ? avecTrimestre("/entites", t) : LiensPages.vueEntite(choix, t);
            case "COMITE" -> avecTrimestre("/comite-talent", t);
            default -> avecTrimestre("/", t);
        };
        return "redirect:" + cibleUrl;
    }

    @GetMapping("/managers")
    public String managers(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                           Model model) {
        Trimestre choisi = selectionner(trimestre, model, "managers");
        if (choisi != null) {
            model.addAttribute("managers", pages.managers(choisi));
        }
        return "managers";
    }

    @GetMapping("/managers/{matricule}")
    public String vueManager(@PathVariable String matricule,
                             @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                             Model model) {
        Trimestre choisi = selectionner(trimestre, model, "vue-manager");
        model.addAttribute("profilCible", matricule);
        if (choisi != null) {
            model.addAttribute("page", pages.vueManager(matricule, choisi));
        }
        return "vue-manager";
    }

    /** Sans {@code code} : l'organigramme a parcourir ; avec : la vue de cette entite. */
    @GetMapping("/entites")
    public String entites(@RequestParam(required = false) String code,
                          @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          Model model) {
        if (code == null || code.isBlank()) {
            Trimestre choisi = selectionner(trimestre, model, "entites");
            if (choisi != null) {
                model.addAttribute("entites", pages.entites());
            }
            return "entites";
        }
        Trimestre choisi = selectionner(trimestre, model, "vue-entite");
        model.addAttribute("profilCible", code.trim());
        if (choisi != null) {
            model.addAttribute("page", pages.vueEntite(code.trim(), choisi));
        }
        return "vue-entite";
    }

    private Trimestre selectionner(String trimestre, Model model, String page) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("activePage", page);
        return selection.trimestre();
    }

    private static String avecTrimestre(String chemin, String trimestre) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath(chemin);
        if (trimestre != null) {
            lien.queryParam(TrimestreCourantService.PARAMETRE, trimestre);
        }
        return lien.encode().build().toUriString();
    }
}
