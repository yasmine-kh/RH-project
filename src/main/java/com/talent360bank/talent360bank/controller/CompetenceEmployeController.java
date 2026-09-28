package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CompetenceEmployeResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.CompetenceEmployeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Competences d'un employe, avec le statut de gap calcule par le moteur (06_EMPLOYEE_SKILLS). */
@RestController
@RequestMapping("/api/employes")
public class CompetenceEmployeController {

    private final CompetenceEmployeService competenceEmployeService;
    private final ChargeurRessources chargeur;

    public CompetenceEmployeController(CompetenceEmployeService competenceEmployeService,
                                       ChargeurRessources chargeur) {
        this.competenceEmployeService = competenceEmployeService;
        this.chargeur = chargeur;
    }

    /**
     * Le trimestre choisit le seuil de Prioritaire ; sans annee ni numero,
     * c'est le plus recent.
     */
    @GetMapping("/{employeeId}/competences")
    public List<CompetenceEmployeResponse> competences(@PathVariable String employeeId,
                                                       @RequestParam(required = false) Integer annee,
                                                       @RequestParam(required = false) Integer numero) {
        Trimestre trimestre = chargeur.exigerTrimestreOuDernier(annee, numero);
        return competenceEmployeService.competences(chargeur.exigerEmploye(employeeId), trimestre).stream()
                .map(CompetenceEmployeResponse::de)
                .toList();
    }
}
