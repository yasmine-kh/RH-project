package com.talent360bank.talent360bank.controller.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chiffres cles de la succession pour le tableau de bord (00_DASHBOARD) :
 * postes critiques suivis, taux de couverture, postes en alerte.
 *
 * @param tauxCouverture sur 100, au centieme ; null sans poste critique
 */
public record SyntheseCouvertureResponse(int nbPostesCritiques, int nbAlertes, BigDecimal tauxCouverture,
                                         List<CouverturePosteResponse> alertes) {
}
