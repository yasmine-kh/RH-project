package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Criteres;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Tri;
import com.talent360bank.talent360bank.ui.service.ListeCollaborateursViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;

/**
 * Liste des collaborateurs d'un trimestre, filtree, triee et paginee : le meme
 * modele que l'ecran Collaborateurs (voir docs/guide-developpeur.md).
 */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/collaborateurs")
public class ListeCollaborateursController {

    private final ListeCollaborateursViewService service;

    public ListeCollaborateursController(ListeCollaborateursViewService service) {
        this.service = service;
    }

    /**
     * Tous les filtres sont facultatifs. {@code tri} : NOM (defaut), MATRICULE, PERFORMANCE,
     * POTENTIEL, MATCHING, VIGILANCE ; {@code ordre} : asc ou desc (defaut : asc pour NOM et
     * MATRICULE, desc pour les scores). 400 pour une valeur inconnue, 404 pour un trimestre inconnu.
     */
    @GetMapping
    public ListeCollaborateurs liste(@PathVariable int annee, @PathVariable int numero,
                                     @RequestParam(required = false) String entite,
                                     @RequestParam(name = "case", required = false) Integer caseNeufBox,
                                     @RequestParam(required = false) Boolean talent,
                                     @RequestParam(required = false) String vivier,
                                     @RequestParam(required = false) String readiness,
                                     @RequestParam(required = false) String vigilance,
                                     @RequestParam(name = "q", required = false) String recherche,
                                     @RequestParam(required = false) String tri,
                                     @RequestParam(required = false) String ordre,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "" + Criteres.TAILLE_PAR_DEFAUT) int taille) {
        Tri colonne = tri == null || tri.isBlank() ? Tri.NOM : enumeration(tri);
        boolean decroissant;
        if (ordre == null || ordre.isBlank()) {
            decroissant = colonne != Tri.NOM && colonne != Tri.MATRICULE;
        } else if ("asc".equalsIgnoreCase(ordre) || "desc".equalsIgnoreCase(ordre)) {
            decroissant = "desc".equalsIgnoreCase(ordre);
        } else {
            throw new IllegalArgumentException("Ordre inconnu : " + ordre + " (attendu : asc ou desc)");
        }
        return service.construire(annee, numero, new Criteres(vide(entite), caseNeufBox, talent,
                majuscules(vivier), majuscules(readiness), majuscules(vigilance), vide(recherche), colonne,
                decroissant, page, taille));
    }

    private static Tri enumeration(String tri) {
        return Arrays.stream(Tri.values())
                .filter(valeur -> valeur.name().equalsIgnoreCase(tri.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tri inconnu : " + tri + " (attendu : "
                        + Arrays.toString(Tri.values()) + ")"));
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static String majuscules(String valeur) {
        String texte = vide(valeur);
        return texte == null ? null : texte.toUpperCase(Locale.ROOT);
    }
}
