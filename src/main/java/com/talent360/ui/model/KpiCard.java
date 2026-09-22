package com.talent360.ui.model;

/**
 * Represente une carte statistique du Dashboard (les "9 boxes" d'indicateurs).
 * Pour l'instant les valeurs sont mockees. Plus tard, Jas fournira ces
 * chiffres via CalculService / AlerteService et on remplacera le mock
 * dans DashboardController sans toucher a cette classe ni au template.
 */
public class KpiCard {

    private final String label;
    private final String value;
    private final String icon;   // classe d'icone Bootstrap Icons, ex: "bi-people"
    private final String colorClass; // ex: "kpi-blue", "kpi-red" (definies dans style.css)

    public KpiCard(String label, String value, String icon, String colorClass) {
        this.label = label;
        this.value = value;
        this.icon = icon;
        this.colorClass = colorClass;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getIcon() {
        return icon;
    }

    public String getColorClass() {
        return colorClass;
    }
}
