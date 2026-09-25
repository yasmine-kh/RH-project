package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.MembreVivierReleve;

import java.math.BigDecimal;

/** Membre du vivier de releve, aplati pour l'API, avec la raison de sa presence. */
public record MembreVivierReleveResume(String employeeId, String nomComplet, BigDecimal scorePerformance,
                                       BigDecimal scorePotentiel, String positionBox,
                                       boolean talent, boolean hautPotentiel) {

    public static MembreVivierReleveResume de(MembreVivierReleve membre) {
        ScoreResume score = ScoreResume.de(membre.score());
        return new MembreVivierReleveResume(score.employeeId(), score.nomComplet(),
                score.scorePerformance(), score.scorePotentiel(), score.positionBox(),
                membre.talent(), membre.hautPotentiel());
    }
}
