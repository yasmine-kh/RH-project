package com.talent360bank.talent360bank.entity;

public enum StatutEmploye {
    /** En poste, entre dans tous les calculs. */
    ACTIF,
    /** Toujours dans l'effectif mais exclu des calculs (conge longue duree, suspension). */
    INACTIF,
    /** Sorti de l'effectif. Conserve pour l'historique, jamais recalcule. */
    ARCHIVE
}
