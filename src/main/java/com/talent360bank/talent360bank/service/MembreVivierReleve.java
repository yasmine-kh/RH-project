package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Score;

/**
 * Membre du vivier de releve et la raison de sa presence : talent, haut
 * potentiel, ou les deux (10_TALENTS!J = E OU F). Au moins l'un des deux
 * statuts est vrai.
 */
public record MembreVivierReleve(Score score, boolean talent, boolean hautPotentiel) {
}
