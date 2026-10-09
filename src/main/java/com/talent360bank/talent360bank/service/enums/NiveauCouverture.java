package com.talent360bank.talent360bank.service.enums;

/**
 * Couverture d'un poste critique par ses successeurs identifies, comme la
 * colonne Couverture de 08_POSTES_CRITIQUES.
 */
public enum NiveauCouverture {
    // Libelles du classeur (08_POSTES_CRITIQUES!I), a l'identique ; "a renforcer" garde son accent.
    // ALERTE avec au moins un successeur (sous le minimum regle) : voir CouverturePoste.libelleCouverture.
    ALERTE("Aucun successeur - ALERTE"),
    READY_NOW("Couverte - Ready Now"),
    MOINS_1_AN("Couverte - <1 an"),
    PARTIELLE("Partielle - à renforcer");

    private final String libelle;

    NiveauCouverture(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
