package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Trimestre;

/**
 * Trimestre demande, et ce que l'appel a du creer pour l'obtenir.
 *
 * @param trimestreCree le trimestre n'existait pas
 * @param parametreCree ses reglages n'existaient pas et ont ete poses aux
 *                      valeurs par defaut
 */
public record ResultatCreationTrimestre(Trimestre trimestre, boolean trimestreCree, boolean parametreCree) {
}
