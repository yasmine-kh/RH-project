package com.talent360bank.talent360bank.ui.model;

/** Gravite d'une alerte, de la plus grave a la moins grave (ordre de tri). */
public enum SeveriteAlerte {

    CRITIQUE("Critique", "bg-danger"),
    ELEVEE("Élevée", "bg-warning text-dark"),
    MOYENNE("Moyenne", "bg-info text-dark");

    private final String libelle;
    private final String badgeClass;

    SeveriteAlerte(String libelle, String badgeClass) {
        this.libelle = libelle;
        this.badgeClass = badgeClass;
    }

    public String getCode() {
        return name();
    }

    public String getLibelle() {
        return libelle;
    }

    /** Classe Bootstrap du badge. */
    public String getBadgeClass() {
        return badgeClass;
    }
}
