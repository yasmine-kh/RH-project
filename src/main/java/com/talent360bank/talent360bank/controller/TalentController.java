package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ConstitutionVivierResponse;
import com.talent360bank.talent360bank.controller.dto.MembreVivierReleveResume;
import com.talent360bank.talent360bank.controller.dto.ScoreResume;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import com.talent360bank.talent360bank.service.VivierReleveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Detection des talents et des hauts potentiels, et vivier de releve qui les
 * reunit : lecture a la demande, ou enregistrement dans AppartenanceVivier.
 */
@RestController
@RequestMapping("/api")
public class TalentController {

    private final TalentService talentService;
    private final VivierReleveService vivierReleveService;
    private final ChargeurRessources chargeur;
    private final VerrouCalculTrimestre verrou;

    public TalentController(TalentService talentService, VivierReleveService vivierReleveService,
                            ChargeurRessources chargeur, VerrouCalculTrimestre verrou) {
        this.talentService = talentService;
        this.vivierReleveService = vivierReleveService;
        this.chargeur = chargeur;
        this.verrou = verrou;
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
     * Statut de talent d'un collaborateur. Rendu comme objet et non comme booleen
     * nu : un corps JSON scalaire est penible a faire evoluer.
     */
    @GetMapping("/trimestres/{annee}/{numero}/talents/{idCollaborateur}")
    public Map<String, Object> pourCollaborateur(@PathVariable int annee, @PathVariable int numero,
                                                 @PathVariable String idCollaborateur) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return Map.of(
                "idCollaborateur", idCollaborateur,
                "estTalent", talentService.estTalent(chargeur.exigerCollaborateur(idCollaborateur), trimestre));
    }

    /** Hauts potentiels du trimestre, du meilleur au moins bon en performance. */
    @GetMapping("/trimestres/{annee}/{numero}/hauts-potentiels")
    public List<ScoreResume> hautsPotentielsDuTrimestre(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return talentService.detecterHautsPotentiels(trimestre).stream()
                .map(ScoreResume::de)
                .toList();
    }

    /** Statut de haut potentiel d'un collaborateur, rendu comme objet comme celui de talent. */
    @GetMapping("/trimestres/{annee}/{numero}/hauts-potentiels/{idCollaborateur}")
    public Map<String, Object> hautPotentielPourCollaborateur(@PathVariable int annee, @PathVariable int numero,
                                                              @PathVariable String idCollaborateur) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return Map.of(
                "idCollaborateur", idCollaborateur,
                "estHautPotentiel",
                talentService.estHautPotentiel(chargeur.exigerCollaborateur(idCollaborateur), trimestre));
    }

    /** Vivier de releve du trimestre : talents OU hauts potentiels, sans doublon. */
    @GetMapping("/trimestres/{annee}/{numero}/vivier-releve")
    public List<MembreVivierReleveResume> vivierReleve(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return talentService.getVivierReleve(trimestre).stream()
                .map(MembreVivierReleveResume::de)
                .toList();
    }

    /**
     * Enregistre le vivier de releve du trimestre dans AppartenanceVivier.
     * Rejouable : les lignes du moteur sont remplacees, jamais doublees, et
     * celles d'un import ou d'une saisie RH ne sont pas touchees.     *
     * <p>Sous le verrou du trimestre ({@link VerrouCalculTrimestre}), comme le
     * calcul complet : 409 si un calcul du trimestre tourne deja.
     */
    @PostMapping("/trimestres/{annee}/{numero}/vivier-releve")
    public ConstitutionVivierResponse enregistrerVivierReleve(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return ConstitutionVivierResponse.de(
                verrou.executer(trimestre, () -> vivierReleveService.constituerViviers(trimestre)));
    }
}
