package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;

import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * Candidat classe sur un poste. Le detail des six criteres est expose avec le
 * score : un classement sans le detail ne se defend pas devant un comite.
 *
 * @param plusGrandGap competence la plus eloignee du niveau requis (09_SUCCESSION!M) ;
 *                     {@code ecart} = 0 quand le candidat n'a aucun ecart. null si le
 *                     poste n'exige aucune competence chiffree
 */
public record MatchingResponse(CollaborateurResume candidat, BigDecimal scoreMatching,
                               String readiness, String readinessLibelle, DetailResponse detail,
                               PlusGrandGap plusGrandGap) {

    /** Sous-scores sur 100. null = critere non applicable, ecarte de la moyenne. */
    public record DetailResponse(BigDecimal competences, BigDecimal performance,
                                 BigDecimal potentiel, BigDecimal experience,
                                 BigDecimal leadership, BigDecimal mobilite) {
    }

    public static MatchingResponse de(ResultatMatching resultat, LocalDate dateReference) {
        ResultatMatching.DetailMatching detail = resultat.detail();
        return new MatchingResponse(
                CollaborateurResume.de(resultat.candidat(), dateReference),
                resultat.scoreMatching(),
                resultat.readiness().name(),
                resultat.readiness().getLibelle(),
                new DetailResponse(detail.competences(), detail.performance(), detail.potentiel(),
                        detail.experience(), detail.leadership(), detail.mobilite()),
                resultat.plusGrandGap());
    }
}
