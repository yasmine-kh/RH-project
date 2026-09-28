package com.talent360bank.talent360bank.service.enums;

/** Delai estime avant qu'un candidat soit pret a prendre le poste cible. */
public enum NiveauReadiness {
    READY_NOW("Ready Now"),
    MOINS_1_AN("< 1 an"),
    ENTRE_1_ET_2_ANS("1-2 ans"),
    PLUS_2_ANS("> 2 ans");

    private final String libelle;

    NiveauReadiness(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
