package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

/**
 * Ecart entre l'auto-evaluation et l'evaluation du manager, en points sur 100
 * et en valeur absolue, a partir duquel la vue manager signale un membre
 * (performance ou potentiel). Seuil d'affichage : aucun score n'en depend,
 * changer ce seuil ne recalcule rien d'autre que la liste signalee.
 *
 * <p>Comme pour {@link SeuilsCouverture}, la colonne porte la valeur par
 * defaut en base pour les lignes existantes au moment de son ajout.
 */
@Embeddable
public class SeuilsAutoEvaluation {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("15")
    @Column(name = "seuil_ecart_auto", precision = 5, scale = 2)
    private BigDecimal seuilEcartImportant;

    public SeuilsAutoEvaluation() {
    }

    public SeuilsAutoEvaluation(BigDecimal seuilEcartImportant) {
        this.seuilEcartImportant = seuilEcartImportant;
    }

    public BigDecimal getSeuilEcartImportant() {
        return seuilEcartImportant;
    }

    public void setSeuilEcartImportant(BigDecimal seuilEcartImportant) {
        this.seuilEcartImportant = seuilEcartImportant;
    }
}
