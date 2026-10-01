package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;

/**
 * Bilan du calcul complet d'un trimestre, une entree par etape, dans l'ordre :
 * memes corps que les trois endpoints appeles un par un.
 */
public record CalculTrimestreResponse(RecalculResponse scores, RecalculResponse placement9Box,
                                      ConstitutionVivierResponse vivierReleve) {

    public static CalculTrimestreResponse de(ResultatCalculTrimestre resultat) {
        return new CalculTrimestreResponse(
                RecalculResponse.de(resultat.scores()),
                RecalculResponse.de(resultat.placements()),
                ConstitutionVivierResponse.de(resultat.vivierReleve()));
    }
}
