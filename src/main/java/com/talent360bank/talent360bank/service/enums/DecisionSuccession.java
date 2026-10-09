package com.talent360bank.talent360bank.service.enums;

/**
 * Decision du Comite Talent sur un successeur d'un poste critique (prototype, renderComite :
 * "Successions a valider"). {@link #REEVALUER} laisse la succession a valider.
 */
public enum DecisionSuccession {
    VALIDER("Validé"),
    VALIDER_AVEC_PLAN("Validé avec plan"),
    MAINTENIR_EN_VIVIER("Maintenu en vivier"),
    REEVALUER("À réévaluer"),
    NE_PAS_RETENIR("Non retenu");

    private final String libelle;

    DecisionSuccession(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Encore a valider par le comite : sans decision, ou a reevaluer. */
    public static boolean enAttente(DecisionSuccession decision) {
        return decision == null || decision == REEVALUER;
    }
}
