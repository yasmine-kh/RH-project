package com.talent360bank.talent360bank.service.enums;

/**
 * Decision du Comite Talent sur un employe, comme la colonne Validation
 * Comite Talent de 10_TALENTS (saisie manuelle Oui / Non / En attente).
 */
public enum StatutValidationComite {
    OUI("Oui"),
    NON("Non"),
    EN_ATTENTE("En attente");

    private final String libelle;

    StatutValidationComite(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
