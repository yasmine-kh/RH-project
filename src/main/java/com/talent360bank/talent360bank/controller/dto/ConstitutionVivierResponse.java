package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;

import java.util.List;

/**
 * Bilan de l'enregistrement du vivier de releve d'un trimestre.
 *
 * @param nbRemplaces  lignes du moteur d'un passage precedent, supprimees puis reecrites
 * @param nbEcrits     lignes ecrites par ce passage
 * @param dejaPresents Employee_ID deja dans le vivier par un import ou une saisie RH, non doubles
 */
public record ConstitutionVivierResponse(int nbRemplaces, int nbEcrits, List<String> dejaPresents) {

    public static ConstitutionVivierResponse de(ResultatConstitutionVivier resultat) {
        return new ConstitutionVivierResponse(resultat.nbRemplaces(), resultat.crees().size(),
                resultat.dejaPresents());
    }
}
