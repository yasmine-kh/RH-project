package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatCreationTrimestre;

/**
 * @param trimestreCree le trimestre n'existait pas
 * @param parametreCree ses reglages ont ete crees aux valeurs par defaut
 */
public record CreationTrimestreResponse(int annee, int numero, String libelle,
                                        boolean trimestreCree, boolean parametreCree) {

    public static CreationTrimestreResponse de(ResultatCreationTrimestre resultat) {
        return new CreationTrimestreResponse(resultat.trimestre().getAnnee(), resultat.trimestre().getNumero(),
                TrimestreResponse.libelle(resultat.trimestre()), resultat.trimestreCree(), resultat.parametreCree());
    }
}
