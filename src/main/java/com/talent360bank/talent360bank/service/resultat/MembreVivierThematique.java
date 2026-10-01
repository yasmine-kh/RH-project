package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Score;

/**
 * Collaborateur d'un vivier thematique, avec ses statuts de talent et de haut
 * potentiel pour que l'ecran puisse filtrer. Tout collaborateur est membre, talent
 * ou non ; les deux statuts sont faux si ses scores sont incomplets.
 */
public record MembreVivierThematique(Score score, boolean talent, boolean hautPotentiel) {
}
