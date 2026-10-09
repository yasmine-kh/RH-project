package com.talent360bank.talent360bank.service.enums;

/** Delai estime avant qu'un candidat soit pret a prendre le poste cible. */
public enum NiveauReadiness {
    // Libelles du classeur (09_SUCCESSION!L, 00_PARAMETRES lignes Readiness), a l'identique.
    READY_NOW("Ready Now"),
    MOINS_1_AN("Ready < 1 an"),
    ENTRE_1_ET_2_ANS("Ready 1-2 ans"),
    PLUS_2_ANS("Ready > 2 ans");

    private final String libelle;

    NiveauReadiness(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
