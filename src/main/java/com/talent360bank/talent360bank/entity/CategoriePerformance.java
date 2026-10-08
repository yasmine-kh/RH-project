package com.talent360bank.talent360bank.entity;

/**
 * Etiquette individuelle de performance, comme la colonne Categorie
 * Performance de 02_PERFORMANCE. Les seuils viennent de
 * SeuilsCategoriePerformance.
 */
public enum CategoriePerformance {
    EXCEPTIONNELLE("Exceptionnelle"),
    ELEVEE("Élevée"),
    SOLIDE("Solide"),
    A_RENFORCER("À renforcer"),
    INSUFFISANTE("Insuffisante");

    private final String libelle;

    CategoriePerformance(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
