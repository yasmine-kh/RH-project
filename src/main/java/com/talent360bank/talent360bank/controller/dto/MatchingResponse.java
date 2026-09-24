package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.ResultatMatching;

import java.math.BigDecimal;

/**
 * Candidat classe sur un poste. Le detail des six criteres est expose avec le
 * score : un classement sans le detail ne se defend pas devant un comite.
 */
public record MatchingResponse(EmployeResume candidat, BigDecimal scoreMatching,
                               String readiness, String readinessLibelle, DetailResponse detail) {

    /** Sous-scores sur 100. null = critere non applicable, ecarte de la moyenne. */
    public record DetailResponse(BigDecimal competences, BigDecimal performance,
                                 BigDecimal potentiel, BigDecimal experience,
                                 BigDecimal leadership, BigDecimal mobilite) {
    }

    public static MatchingResponse de(ResultatMatching resultat) {
        ResultatMatching.DetailMatching detail = resultat.detail();
        return new MatchingResponse(
                EmployeResume.de(resultat.candidat()),
                resultat.scoreMatching(),
                resultat.readiness().name(),
                resultat.readiness().getLibelle(),
                new DetailResponse(detail.competences(), detail.performance(), detail.potentiel(),
                        detail.experience(), detail.leadership(), detail.mobilite()));
    }
}
