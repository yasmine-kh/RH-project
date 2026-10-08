package com.talent360bank.talent360bank.ui.model;

/**
 * Une carte chiffree d'un ecran : un libelle et une valeur deja mise en forme, calculee par le
 * moteur (DashboardService, ComiteTalentViewService). Le style de la carte est choisi par le template.
 */
public class KpiCard {

    private final String label;
    private final String value;

    public KpiCard(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}
