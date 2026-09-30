package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un vivier thematique et ses membres, avec les effectifs que l'ecran
 * affiche sans avoir a recompter.
 */
public record VivierThematiqueResponse(String code, String libelle,
                                       int nbMembres, int nbTalents, int nbHautsPotentiels,
                                       List<Membre> membres) {

    /** Membre aplati pour l'API, avec ses statuts pour filtrer. */
    public record Membre(String idCollaborateur, String nomComplet, String direction,
                         BigDecimal scorePerformance, BigDecimal scorePotentiel, String positionBox,
                         boolean talent, boolean hautPotentiel) {

        static Membre de(MembreVivierThematique membre) {
            ScoreResume score = ScoreResume.de(membre.score());
            return new Membre(score.idCollaborateur(), score.nomComplet(),
                    membre.score().getDirection(),
                    score.scorePerformance(), score.scorePotentiel(), score.positionBox(),
                    membre.talent(), membre.hautPotentiel());
        }
    }

    public static VivierThematiqueResponse de(VivierThematique vivier, List<MembreVivierThematique> membres) {
        return new VivierThematiqueResponse(vivier.getCode(), vivier.getLibelle(), membres.size(),
                (int) membres.stream().filter(MembreVivierThematique::talent).count(),
                (int) membres.stream().filter(MembreVivierThematique::hautPotentiel).count(),
                membres.stream().map(Membre::de).toList());
    }
}
