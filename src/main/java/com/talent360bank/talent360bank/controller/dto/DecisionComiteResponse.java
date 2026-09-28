package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.DecisionComite;

import java.math.BigDecimal;

/** Decision du Comite Talent sur un talent propose, aplatie pour l'API. */
public record DecisionComiteResponse(String employeeId, String nomComplet, String direction,
                                     BigDecimal scorePerformance, BigDecimal scorePotentiel, String positionBox,
                                     String statut, String statutLibelle, boolean talentValide) {

    public static DecisionComiteResponse de(DecisionComite decision) {
        ScoreResume score = ScoreResume.de(decision.score());
        return new DecisionComiteResponse(score.employeeId(), score.nomComplet(),
                decision.score().getEmploye().getDirection(),
                score.scorePerformance(), score.scorePotentiel(), score.positionBox(),
                decision.statut().name(), decision.statut().getLibelle(), decision.talentValide());
    }
}
