package com.talent360bank.talent360bank.service.enums;

/**
 * Statut du gap d'une competence, comme la colonne Statut Gap de
 * 06_EMPLOYEE_SKILLS. Le seuil de Prioritaire vient de SeuilsGapCompetence.
 */
public enum StatutGapCompetence {
    MAITRISE("Maîtrisé"),
    A_DEVELOPPER("À développer"),
    PRIORITAIRE("Prioritaire");

    private final String libelle;

    StatutGapCompetence(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
