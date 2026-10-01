package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Trimestre;

import java.time.LocalDate;

/**
 * Trimestre connu de l'application.
 *
 * @param libelle       "T3 2026", pour l'affichage
 * @param dateReference date a laquelle le trimestre est evalue (anciennete)
 * @param reglages      ses reglages existent : sans eux, aucun calcul ne tourne
 */
public record TrimestreResponse(int annee, int numero, String libelle, LocalDate dateReference,
                                boolean reglages) {

    public static TrimestreResponse de(Trimestre trimestre, boolean reglages) {
        return new TrimestreResponse(trimestre.getAnnee(), trimestre.getNumero(), libelle(trimestre),
                trimestre.getDateReference(), reglages);
    }

    static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
