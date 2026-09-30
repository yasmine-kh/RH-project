package com.talent360bank.talent360bank.service.resultat;

/**
 * Bilan des trois etapes du calcul d'un trimestre, dans leur ordre.
 *
 * @param scores       recalcul des scores et categories de performance
 * @param placements   placement 9-box
 * @param vivierReleve constitution du vivier de releve
 */
public record ResultatCalculTrimestre(ResultatRecalcul scores, ResultatRecalcul placements,
                                      ResultatConstitutionVivier vivierReleve) {
}
