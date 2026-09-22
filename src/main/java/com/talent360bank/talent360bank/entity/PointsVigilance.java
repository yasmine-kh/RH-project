package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Points attribues a chaque signal de risque de depart. Contrairement aux
 * blocs de poids, la somme n'est pas contrainte a 100 : le RH peut ajuster
 * chaque signal independamment. C'est Parametre qui verifie que les seuils
 * de vigilance restent atteignables.
 */
@Embeddable
public class PointsVigilance {

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_engagement_faible", precision = 5, scale = 2)
    private BigDecimal pointEngagementFaible;

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_sans_mobilite_4_ans", precision = 5, scale = 2)
    private BigDecimal pointSansMobilite4Ans;

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_mobilite_non_traitee", precision = 5, scale = 2)
    private BigDecimal pointMobiliteNonTraitee;

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_sans_developpement", precision = 5, scale = 2)
    private BigDecimal pointSansDeveloppementRecent;

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_baisse_performance", precision = 5, scale = 2)
    private BigDecimal pointBaissePerformance;

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_faible_reconnaissance", precision = 5, scale = 2)
    private BigDecimal pointFaibleReconnaissance;

    @NotNull
    @DecimalMin("0")
    @Column(name = "vig_formation_non_faite", precision = 5, scale = 2)
    private BigDecimal pointFormationNonFaite;

    public PointsVigilance() {
    }

    public PointsVigilance(BigDecimal pointEngagementFaible, BigDecimal pointSansMobilite4Ans,
                           BigDecimal pointMobiliteNonTraitee, BigDecimal pointSansDeveloppementRecent,
                           BigDecimal pointBaissePerformance, BigDecimal pointFaibleReconnaissance,
                           BigDecimal pointFormationNonFaite) {
        this.pointEngagementFaible = pointEngagementFaible;
        this.pointSansMobilite4Ans = pointSansMobilite4Ans;
        this.pointMobiliteNonTraitee = pointMobiliteNonTraitee;
        this.pointSansDeveloppementRecent = pointSansDeveloppementRecent;
        this.pointBaissePerformance = pointBaissePerformance;
        this.pointFaibleReconnaissance = pointFaibleReconnaissance;
        this.pointFormationNonFaite = pointFormationNonFaite;
    }

    /** Indice maximum atteignable : tous les signaux de risque declenches. */
    @Transient
    public BigDecimal getTotalMaximum() {
        BigDecimal[] points = {pointEngagementFaible, pointSansMobilite4Ans, pointMobiliteNonTraitee,
                pointSansDeveloppementRecent, pointBaissePerformance, pointFaibleReconnaissance,
                pointFormationNonFaite};
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal point : points) {
            if (point == null) {
                return null;
            }
            total = total.add(point);
        }
        return total;
    }

    public BigDecimal getPointEngagementFaible() {
        return pointEngagementFaible;
    }

    public void setPointEngagementFaible(BigDecimal pointEngagementFaible) {
        this.pointEngagementFaible = pointEngagementFaible;
    }

    public BigDecimal getPointSansMobilite4Ans() {
        return pointSansMobilite4Ans;
    }

    public void setPointSansMobilite4Ans(BigDecimal pointSansMobilite4Ans) {
        this.pointSansMobilite4Ans = pointSansMobilite4Ans;
    }

    public BigDecimal getPointMobiliteNonTraitee() {
        return pointMobiliteNonTraitee;
    }

    public void setPointMobiliteNonTraitee(BigDecimal pointMobiliteNonTraitee) {
        this.pointMobiliteNonTraitee = pointMobiliteNonTraitee;
    }

    public BigDecimal getPointSansDeveloppementRecent() {
        return pointSansDeveloppementRecent;
    }

    public void setPointSansDeveloppementRecent(BigDecimal pointSansDeveloppementRecent) {
        this.pointSansDeveloppementRecent = pointSansDeveloppementRecent;
    }

    public BigDecimal getPointBaissePerformance() {
        return pointBaissePerformance;
    }

    public void setPointBaissePerformance(BigDecimal pointBaissePerformance) {
        this.pointBaissePerformance = pointBaissePerformance;
    }

    public BigDecimal getPointFaibleReconnaissance() {
        return pointFaibleReconnaissance;
    }

    public void setPointFaibleReconnaissance(BigDecimal pointFaibleReconnaissance) {
        this.pointFaibleReconnaissance = pointFaibleReconnaissance;
    }

    public BigDecimal getPointFormationNonFaite() {
        return pointFormationNonFaite;
    }

    public void setPointFormationNonFaite(BigDecimal pointFormationNonFaite) {
        this.pointFormationNonFaite = pointFormationNonFaite;
    }
}
