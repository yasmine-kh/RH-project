package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.MembreVivierReleveResume;
import com.talent360bank.talent360bank.controller.dto.ScoreResume;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.TalentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Detection des talents et des hauts potentiels, et vivier de releve qui les reunit. */
@RestController
@RequestMapping("/api")
public class TalentController {

    private final TalentService talentService;
    private final ChargeurRessources chargeur;

    public TalentController(TalentService talentService, ChargeurRessources chargeur) {
        this.talentService = talentService;
        this.chargeur = chargeur;
    }

    /** Talents du trimestre, du meilleur au moins bon en performance. */
    @GetMapping("/trimestres/{annee}/{numero}/talents")
    public List<ScoreResume> duTrimestre(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return talentService.detecterTalents(trimestre).stream()
                .map(ScoreResume::de)
                .toList();
    }

    /**
     * Statut de talent d'un employe. Rendu comme objet et non comme booleen
     * nu : un corps JSON scalaire est penible a faire evoluer.
     */
    @GetMapping("/trimestres/{annee}/{numero}/talents/{employeeId}")
    public Map<String, Object> pourEmploye(@PathVariable int annee, @PathVariable int numero,
                                           @PathVariable String employeeId) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return Map.of(
                "employeeId", employeeId,
                "estTalent", talentService.estTalent(chargeur.exigerEmploye(employeeId), trimestre));
    }

    /** Hauts potentiels du trimestre, du meilleur au moins bon en performance. */
    @GetMapping("/trimestres/{annee}/{numero}/hauts-potentiels")
    public List<ScoreResume> hautsPotentielsDuTrimestre(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return talentService.detecterHautsPotentiels(trimestre).stream()
                .map(ScoreResume::de)
                .toList();
    }

    /** Statut de haut potentiel d'un employe, rendu comme objet comme celui de talent. */
    @GetMapping("/trimestres/{annee}/{numero}/hauts-potentiels/{employeeId}")
    public Map<String, Object> hautPotentielPourEmploye(@PathVariable int annee, @PathVariable int numero,
                                                        @PathVariable String employeeId) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return Map.of(
                "employeeId", employeeId,
                "estHautPotentiel",
                talentService.estHautPotentiel(chargeur.exigerEmploye(employeeId), trimestre));
    }

    /** Vivier de releve du trimestre : talents OU hauts potentiels, sans doublon. */
    @GetMapping("/trimestres/{annee}/{numero}/vivier-releve")
    public List<MembreVivierReleveResume> vivierReleve(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return talentService.getVivierReleve(trimestre).stream()
                .map(MembreVivierReleveResume::de)
                .toList();
    }
}
