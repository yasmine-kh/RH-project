package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

/**
 * Seuils de la categorie de performance, etiquette individuelle de
 * 02_PERFORMANCE (colonne J), tels que les fixe 00_PARAMETRES section 4 :
 * score >= exceptionnelle, sinon >= elevee, sinon >= solide, sinon
 * >= a renforcer, sinon insuffisante.
 *
 * <p>Comme pour {@link BaremeExperience}, les colonnes portent la valeur par
 * defaut en base pour les lignes existantes au moment de leur ajout.
 */
@Embeddable
public class SeuilsCategoriePerformance {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("90")
    @Column(name = "cat_perf_exceptionnelle", precision = 5, scale = 2)
    private BigDecimal seuilExceptionnelle;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("80")
    @Column(name = "cat_perf_elevee", precision = 5, scale = 2)
    private BigDecimal seuilElevee;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("70")
    @Column(name = "cat_perf_solide", precision = 5, scale = 2)
    private BigDecimal seuilSolide;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("60")
    @Column(name = "cat_perf_a_renforcer", precision = 5, scale = 2)
    private BigDecimal seuilARenforcer;

    public SeuilsCategoriePerformance() {
    }

    public SeuilsCategoriePerformance(BigDecimal seuilExceptionnelle, BigDecimal seuilElevee,
                                      BigDecimal seuilSolide, BigDecimal seuilARenforcer) {
        this.seuilExceptionnelle = seuilExceptionnelle;
        this.seuilElevee = seuilElevee;
        this.seuilSolide = seuilSolide;
        this.seuilARenforcer = seuilARenforcer;
    }

    @Transient
    @AssertTrue(message = "Les seuils des categories de performance doivent etre strictement decroissants")
    public boolean isOrdreValide() {
        return Poids.ordreDecroissant(seuilExceptionnelle, seuilElevee, seuilSolide, seuilARenforcer);
    }

    public BigDecimal getSeuilExceptionnelle() {
        return seuilExceptionnelle;
    }

    public void setSeuilExceptionnelle(BigDecimal seuilExceptionnelle) {
        this.seuilExceptionnelle = seuilExceptionnelle;
    }

    public BigDecimal getSeuilElevee() {
        return seuilElevee;
    }

    public void setSeuilElevee(BigDecimal seuilElevee) {
        this.seuilElevee = seuilElevee;
    }

    public BigDecimal getSeuilSolide() {
        return seuilSolide;
    }

    public void setSeuilSolide(BigDecimal seuilSolide) {
        this.seuilSolide = seuilSolide;
    }

    public BigDecimal getSeuilARenforcer() {
        return seuilARenforcer;
    }

    public void setSeuilARenforcer(BigDecimal seuilARenforcer) {
        this.seuilARenforcer = seuilARenforcer;
    }
}
