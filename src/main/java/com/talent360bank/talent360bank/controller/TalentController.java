package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ScoreResume;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.TalentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Detection des talents : les deux scores au-dessus de leur seuil. */
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
}
