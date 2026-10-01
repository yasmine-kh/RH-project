package com.talent360bank.talent360bank.service.resultat;

/**
 * Import d'un classeur suivi du calcul du trimestre.
 *
 * @param importation  bilan de l'import
 * @param calcul       bilan du calcul, nul s'il n'a pas ete lance ou a echoue
 * @param erreurCalcul cause de l'echec du calcul, nulle sinon ; l'import reste enregistre
 */
public record ResultatCampagne(ResultatImport importation, ResultatCalculTrimestre calcul, String erreurCalcul) {
}
