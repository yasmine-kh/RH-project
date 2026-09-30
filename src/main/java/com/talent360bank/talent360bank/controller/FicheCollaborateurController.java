package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fiche d'un collaborateur sur un trimestre, en un appel : le meme modele que
 * la page "Fiche collaborateur" (voir docs/guide-developpeur.md).
 */
@RestController
@RequestMapping("/api")
public class FicheCollaborateurController {

    private final FicheCollaborateurViewService ficheCollaborateurViewService;

    public FicheCollaborateurController(FicheCollaborateurViewService ficheCollaborateurViewService) {
        this.ficheCollaborateurViewService = ficheCollaborateurViewService;
    }

    /** 404 si le trimestre ou le matricule est inconnu ; une donnee absente laisse son bloc vide. */
    @GetMapping("/trimestres/{annee}/{numero}/collaborateurs/{matricule}/fiche")
    public FicheCollaborateur fiche(@PathVariable int annee, @PathVariable int numero,
                                    @PathVariable String matricule) {
        return ficheCollaborateurViewService.construire(matricule, annee, numero);
    }
}
