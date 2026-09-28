package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;

import java.util.List;

/**
 * Bilan d'une constitution du vivier de releve pour un trimestre.
 *
 * @param nbRemplaces       lignes du moteur supprimees avant reecriture (celles
 *                          d'un precedent passage sur le meme trimestre)
 * @param crees             lignes ecrites par ce passage
 * @param dejaPresents      Employee_ID deja dans le vivier par une autre
 *                          origine (import, saisie RH) : non doubles
 */
public record ResultatConstitutionVivier(int nbRemplaces, List<AppartenanceVivier> crees,
                                         List<String> dejaPresents) {
}
