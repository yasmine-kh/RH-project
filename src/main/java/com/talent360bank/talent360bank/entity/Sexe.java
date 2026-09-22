package com.talent360bank.talent360bank.entity;

public enum Sexe {
    HOMME,
    FEMME;

    /**
     * Tolerant aux libelles du fichier Excel (M / F / Homme / Masculin ...).
     * Retourne null si la valeur est absente ou non reconnue.
     */
    public static Sexe depuisLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        String valeur = libelle.trim().toUpperCase();
        if (valeur.startsWith("H") || valeur.startsWith("M")) {
            return HOMME;
        }
        if (valeur.startsWith("F")) {
            return FEMME;
        }
        return null;
    }
}
