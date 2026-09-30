package com.talent360bank.talent360bank.entity;

/**
 * Niveau d'une entite de l'organisation, du plus large au plus fin : une
 * direction contient des departements, qui contiennent des regions, qui
 * contiennent des agences (colonnes H a K de 01_COLLABORATEURS).
 */
public enum TypeEntite {
    DIRECTION("DIR"),
    DEPARTEMENT("DEP"),
    REGION("REG"),
    AGENCE("AGE");

    private final String prefixeCode;

    TypeEntite(String prefixeCode) {
        this.prefixeCode = prefixeCode;
    }

    /** Prefixe du segment de ce niveau dans {@link Entite#getCode()}. */
    public String getPrefixeCode() {
        return prefixeCode;
    }
}
