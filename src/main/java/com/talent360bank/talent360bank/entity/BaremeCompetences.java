package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Bareme du critere competences du matching succession, tel que l'applique
 * 09_SUCCESSION : chaque niveau manquant par rapport a l'exigence du poste
 * retire un nombre fixe de points aux 100 de la competence.
 */
@Embeddable
public class BaremeCompetences {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "comp_points_par_niveau_manquant", precision = 5, scale = 2)
    private BigDecimal pointsParNiveauManquant;

    public BaremeCompetences() {
    }

    public BaremeCompetences(BigDecimal pointsParNiveauManquant) {
        this.pointsParNiveauManquant = pointsParNiveauManquant;
    }

    public BigDecimal getPointsParNiveauManquant() {
        return pointsParNiveauManquant;
    }

    public void setPointsParNiveauManquant(BigDecimal pointsParNiveauManquant) {
        this.pointsParNiveauManquant = pointsParNiveauManquant;
    }
}
