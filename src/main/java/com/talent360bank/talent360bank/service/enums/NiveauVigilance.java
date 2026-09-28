package com.talent360bank.talent360bank.service.enums;

/** Niveau de l'indice de vigilance (risque de depart). */
public enum NiveauVigilance {
    FAIBLE("Faible"),
    MODEREE("Moderee"),
    ELEVEE("Elevee");

    private final String libelle;

    NiveauVigilance(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
