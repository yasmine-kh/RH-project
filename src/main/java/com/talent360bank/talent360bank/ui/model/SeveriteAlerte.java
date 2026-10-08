package com.talent360bank.talent360bank.ui.model;

/** Gravite d'une alerte, de la plus grave a la moins grave (ordre de tri). */
public enum SeveriteAlerte {

    CRITIQUE("Critique"),
    ELEVEE("Élevée"),
    MOYENNE("Moyenne");

    private final String libelle;

    SeveriteAlerte(String libelle) {
        this.libelle = libelle;
    }

    public String getCode() {
        return name();
    }

    public String getLibelle() {
        return libelle;
    }

}
