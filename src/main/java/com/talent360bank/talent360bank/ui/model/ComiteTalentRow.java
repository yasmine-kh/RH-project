package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;

/**
 * Une ligne du tableau Comite Talent, prete pour l'affichage.
 * Aucun calcul ici : scores et categories viennent de Score (Jas), la
 * decision du comite de ValidationComiteService (Jas).
 */
public class ComiteTalentRow {

    private final String nomComplet;
    private final String direction;
    private final BigDecimal performance;
    private final String categoriePerformance;
    private final BigDecimal potentiel;
    private final String categoriePotentiel;
    private final String positionBox;
    private final String statut;      // code : OUI, EN_ATTENTE, NON
    private final String statutLibelle;

    public ComiteTalentRow(String nomComplet, String direction,
                           BigDecimal performance, String categoriePerformance,
                           BigDecimal potentiel, String categoriePotentiel,
                           String positionBox, String statut, String statutLibelle) {
        this.nomComplet = nomComplet;
        this.direction = direction;
        this.performance = performance;
        this.categoriePerformance = categoriePerformance;
        this.potentiel = potentiel;
        this.categoriePotentiel = categoriePotentiel;
        this.positionBox = positionBox;
        this.statut = statut;
        this.statutLibelle = statutLibelle;
    }

    public String getNomComplet() {
        return nomComplet;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getPerformance() {
        return performance;
    }

    public String getCategoriePerformance() {
        return categoriePerformance;
    }

    public BigDecimal getPotentiel() {
        return potentiel;
    }

    public String getCategoriePotentiel() {
        return categoriePotentiel;
    }

    public String getPositionBox() {
        return positionBox;
    }

    public String getStatut() {
        return statut;
    }

    public String getStatutLibelle() {
        return statutLibelle;
    }

}
