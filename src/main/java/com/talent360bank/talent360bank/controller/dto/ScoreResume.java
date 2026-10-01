package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Score;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Score d'un collaborateur sur un trimestre, aplati pour l'API. Les categories sont
 * les libelles du classeur (02_PERFORMANCE J, 03_POTENTIEL L), null tant que
 * le recalcul ou le placement 9-box ne les a pas posees.
 */
public record ScoreResume(String idCollaborateur, String nomComplet, BigDecimal scorePerformance,
                          String categoriePerformance, BigDecimal scorePotentiel,
                          String categoriePotentiel, String positionBox, LocalDate dateCalcul) {

    public static ScoreResume de(Score score) {
        return new ScoreResume(
                score.getCollaborateur().getIdCollaborateur(),
                score.getCollaborateur().getNomComplet(),
                score.getScorePerformance(),
                score.getCategoriePerformance() == null ? null : score.getCategoriePerformance().getLibelle(),
                score.getScorePotentiel(),
                score.getCategoriePotentiel() == null ? null : score.getCategoriePotentiel().getLibelle(),
                score.getPositionBox(),
                score.getDateCalcul());
    }
}
