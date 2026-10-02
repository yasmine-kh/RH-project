package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.service.CompetenceSyntheseService;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Criteres;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Module Competences d'un trimestre : une ligne par competence, les plus
 * grands gaps et ce qui manque aux successeurs de chaque poste critique (voir
 * docs/guide-developpeur.md). Les competences d'un collaborateur sont dans sa
 * fiche et dans GET /api/collaborateurs/{id}/competences.
 */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/competences")
public class CompetenceSyntheseController {

    private final CompetenceSyntheseService service;
    private final ChargeurRessources chargeur;

    public CompetenceSyntheseController(CompetenceSyntheseService service, ChargeurRessources chargeur) {
        this.service = service;
        this.chargeur = chargeur;
    }

    /**
     * Filtres facultatifs, combines : {@code entite} (code, sous-arbre compris), {@code vivier}
     * (COMMERCIAL... ou RELEVE), {@code poste} (Poste_ID d'un poste critique : ses successeurs).
     * {@code ordre} : desc (defaut, plus grand gap moyen d'abord) ou asc. {@code top} : 1 a 50 (5).
     */
    @GetMapping
    public SyntheseCompetences competences(@PathVariable int annee, @PathVariable int numero,
                                           @RequestParam(required = false) String entite,
                                           @RequestParam(required = false) String vivier,
                                           @RequestParam(required = false) String poste,
                                           @RequestParam(required = false) String ordre,
                                           @RequestParam(defaultValue = "" + SyntheseCompetences.TOP_PAR_DEFAUT)
                                           int top) {
        boolean croissant;
        if (ordre == null || ordre.isBlank() || "desc".equalsIgnoreCase(ordre)) {
            croissant = false;
        } else if ("asc".equalsIgnoreCase(ordre)) {
            croissant = true;
        } else {
            throw new IllegalArgumentException("Ordre inconnu : " + ordre + " (attendu : asc ou desc)");
        }
        return service.synthese(chargeur.exigerTrimestre(annee, numero),
                new Criteres(vide(entite), majuscules(vivier), majuscules(poste), croissant, top));
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static String majuscules(String valeur) {
        String texte = vide(valeur);
        return texte == null ? null : texte.toUpperCase(Locale.ROOT);
    }
}
