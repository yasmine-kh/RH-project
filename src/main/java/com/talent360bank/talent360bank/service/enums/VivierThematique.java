package com.talent360bank.talent360bank.service.enums;

/**
 * Viviers thematiques de 10_TALENTS (colonne Vivier thematique). Chaque
 * employe en a un, deduit de sa direction.
 *
 * <p>Le code est celui du {@link com.talent360bank.talent360bank.entity.Vivier}
 * correspondant : le moteur retrouve ses viviers par code, jamais par nom,
 * comme pour le vivier de releve.
 */
public enum VivierThematique {
    COMMERCIAL("COMMERCIAL", "Vivier Commercial"),
    DIGITAL("DIGITAL", "Vivier Digital"),
    EXPERTISE("EXPERTISE", "Vivier Expertise"),
    MANAGEMENT("MANAGEMENT", "Vivier Management"),
    RISQUES("RISQUES", "Vivier Risques");

    private final String code;
    private final String libelle;

    VivierThematique(String code, String libelle) {
        this.code = code;
        this.libelle = libelle;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }
}
