package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatCreationTrimestre;

import java.time.LocalDate;

/**
 * @param dateReference date a laquelle le trimestre est evalue (anciennete)
 * @param trimestreCree le trimestre n'existait pas
 * @param parametreCree ses reglages ont ete crees aux valeurs par defaut
 */
public record CreationTrimestreResponse(int annee, int numero, String libelle, LocalDate dateReference,
                                        boolean trimestreCree, boolean parametreCree) {

    public static CreationTrimestreResponse de(ResultatCreationTrimestre resultat) {
        return new CreationTrimestreResponse(resultat.trimestre().getAnnee(), resultat.trimestre().getNumero(),
                TrimestreResponse.libelle(resultat.trimestre()), resultat.trimestre().getDateReference(),
                resultat.trimestreCree(), resultat.parametreCree());
    }
}
