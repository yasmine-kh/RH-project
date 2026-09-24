package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Score;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Score d'un employe sur un trimestre, aplati pour l'API. */
public record ScoreResume(String employeeId, String nomComplet, BigDecimal scorePerformance,
                          BigDecimal scorePotentiel, String positionBox, LocalDate dateCalcul) {

    public static ScoreResume de(Score score) {
        return new ScoreResume(
                score.getEmploye().getEmployeeId(),
                score.getEmploye().getNomComplet(),
                score.getScorePerformance(),
                score.getScorePotentiel(),
                score.getPositionBox(),
                score.getDateCalcul());
    }
}
