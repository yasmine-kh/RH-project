package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Bareme du critere experience du matching succession, tel que le fixe
 * 00_PARAMETRES (section 10) : chaque annee d'anciennete rapporte un nombre
 * fixe de points, le total etant plafonne.
 */
@Embeddable
public class BaremeExperience {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "exp_points_par_annee", precision = 5, scale = 2)
    private BigDecimal pointsParAnnee;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "exp_plafond", precision = 5, scale = 2)
    private BigDecimal plafond;

    public BaremeExperience() {
    }

    public BaremeExperience(BigDecimal pointsParAnnee, BigDecimal plafond) {
        this.pointsParAnnee = pointsParAnnee;
        this.plafond = plafond;
    }

    public BigDecimal getPointsParAnnee() {
        return pointsParAnnee;
    }

    public void setPointsParAnnee(BigDecimal pointsParAnnee) {
        this.pointsParAnnee = pointsParAnnee;
    }

    public BigDecimal getPlafond() {
        return plafond;
    }

    public void setPlafond(BigDecimal plafond) {
        this.plafond = plafond;
    }
}
