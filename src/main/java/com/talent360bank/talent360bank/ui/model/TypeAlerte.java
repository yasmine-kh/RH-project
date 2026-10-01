package com.talent360bank.talent360bank.ui.model;

/**
 * Types d'alerte calculables aujourd'hui a partir du moteur, dans l'ordre
 * d'affichage. Les alertes ne sont pas enregistrees : elles sont recalculees a
 * chaque lecture pour le trimestre affiche (voir AlertesViewService).
 */
public enum TypeAlerte {

    POSTE_SANS_SUCCESSEUR("Poste critique sans successeur"),
    POSTE_SOUS_MINIMUM("Successeurs insuffisants"),
    VIGILANCE_ELEVEE("Vigilance élevée"),
    EVALUATION_MANAGER_MANQUANTE("Évaluation du manager manquante"),
    ECART_AUTO_MANAGER("Écart auto-évaluation / manager"),
    TALENT_SANS_DECISION("Talent sans décision du Comité"),
    GAPS_COMPETENCES_PRIORITAIRES("Compétences en gap prioritaire");

    private final String libelle;

    TypeAlerte(String libelle) {
        this.libelle = libelle;
    }

    public String getCode() {
        return name();
    }

    public String getLibelle() {
        return libelle;
    }
}
