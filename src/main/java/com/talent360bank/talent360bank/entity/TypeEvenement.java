package com.talent360bank.talent360bank.entity;

/** Nature d'un evenement de l'historique (journal_evenement). */
public enum TypeEvenement {
    IMPORT("Import"),
    DECISION_TALENT("Décision talent"),
    DECISION_SUCCESSION("Décision succession"),
    PARAMETRES("Paramètres"),
    COLLABORATEUR("Collaborateur");

    private final String libelle;

    TypeEvenement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
