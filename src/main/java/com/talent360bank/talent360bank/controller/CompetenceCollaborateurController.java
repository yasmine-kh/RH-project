package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CompetenceCollaborateurResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Competences d'un collaborateur, avec le statut de gap calcule par le moteur (06_EMPLOYEE_SKILLS). */
@RestController
@RequestMapping({"/api/collaborateurs", "/api/employes"}) // /api/employes : ancien chemin, temporaire
public class CompetenceCollaborateurController {

    private final CompetenceCollaborateurService competenceCollaborateurService;
    private final ChargeurRessources chargeur;

    public CompetenceCollaborateurController(CompetenceCollaborateurService competenceCollaborateurService,
                                             ChargeurRessources chargeur) {
        this.competenceCollaborateurService = competenceCollaborateurService;
        this.chargeur = chargeur;
    }

    /**
     * Le trimestre choisit le seuil de Prioritaire ; sans annee ni numero,
     * c'est le plus recent.
     */
    @GetMapping("/{idCollaborateur}/competences")
    public List<CompetenceCollaborateurResponse> competences(@PathVariable String idCollaborateur,
                                                             @RequestParam(required = false) Integer annee,
                                                             @RequestParam(required = false) Integer numero) {
        Trimestre trimestre = chargeur.exigerTrimestreOuDernier(annee, numero);
        return competenceCollaborateurService.competences(chargeur.exigerCollaborateur(idCollaborateur), trimestre)
                .stream()
                .map(CompetenceCollaborateurResponse::de)
                .toList();
    }
}
