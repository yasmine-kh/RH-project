package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;

/**
 * Decision du Comite Talent sur un talent propose par le moteur. Le talent
 * est valide seulement si le comite a dit Oui (10_TALENTS!H = E ET G).
 */
public record DecisionComite(Score score, StatutValidationComite statut) {

    public boolean talentValide() {
        return statut == StatutValidationComite.OUI;
    }
}
