package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.MatchingResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.SuccessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Plans de succession : candidats classes sur un poste cible. */
@RestController
@RequestMapping("/api")
public class SuccessionController {

    private final SuccessionService successionService;
    private final ChargeurRessources chargeur;

    public SuccessionController(SuccessionService successionService, ChargeurRessources chargeur) {
        this.successionService = successionService;
        this.chargeur = chargeur;
    }

    /**
     * Candidats classes sur un poste, du meilleur matching au moins bon.
     *
     * <p>Le trimestre est en parametre de requete et non dans le chemin : la
     * ressource est le poste, le trimestre n'est que la periode de reference
     * des scores. {@code limite} rend la short-list de comite.
     */
    @GetMapping("/postes/{posteId}/candidats")
    public List<MatchingResponse> candidats(@PathVariable String posteId,
                                            @RequestParam int annee,
                                            @RequestParam int numero,
                                            @RequestParam(required = false) Integer limite) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);

        List<com.talent360bank.talent360bank.service.ResultatMatching> classement =
                limite == null
                        ? successionService.classerCandidats(posteId, trimestre)
                        : successionService.classerCandidats(posteId, trimestre, limite);

        return classement.stream().map(MatchingResponse::de).toList();
    }

    /** Matching d'un seul candidat sur un poste. */
    @GetMapping("/postes/{posteId}/candidats/{employeeId}")
    public MatchingResponse candidat(@PathVariable String posteId,
                                     @PathVariable String employeeId,
                                     @RequestParam int annee,
                                     @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return MatchingResponse.de(successionService.evaluer(
                chargeur.exigerEmploye(employeeId), posteId, trimestre));
    }
}
