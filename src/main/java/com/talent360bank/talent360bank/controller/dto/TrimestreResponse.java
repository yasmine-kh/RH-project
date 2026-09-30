package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Trimestre;

/**
 * Trimestre connu de l'application.
 *
 * @param libelle  "T3 2026", pour l'affichage
 * @param reglages ses reglages existent : sans eux, aucun calcul ne tourne
 */
public record TrimestreResponse(int annee, int numero, String libelle, boolean reglages) {

    public static TrimestreResponse de(Trimestre trimestre, boolean reglages) {
        return new TrimestreResponse(trimestre.getAnnee(), trimestre.getNumero(), libelle(trimestre), reglages);
    }

    static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
