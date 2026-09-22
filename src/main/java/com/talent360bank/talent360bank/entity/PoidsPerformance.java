package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Poids des 5 criteres du score de performance. */
@Embeddable
public class PoidsPerformance {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "perf_objectifs", precision = 5, scale = 2)
    private BigDecimal poidsObjectifs;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "perf_competences", precision = 5, scale = 2)
    private BigDecimal poidsCompetences;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "perf_comportement", precision = 5, scale = 2)
    private BigDecimal poidsComportement;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "perf_contribution", precision = 5, scale = 2)
    private BigDecimal poidsContribution;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "perf_developpement", precision = 5, scale = 2)
    private BigDecimal poidsDeveloppement;

    public PoidsPerformance() {
    }

    public PoidsPerformance(BigDecimal poidsObjectifs, BigDecimal poidsCompetences,
                            BigDecimal poidsComportement, BigDecimal poidsContribution,
                            BigDecimal poidsDeveloppement) {
        this.poidsObjectifs = poidsObjectifs;
        this.poidsCompetences = poidsCompetences;
        this.poidsComportement = poidsComportement;
        this.poidsContribution = poidsContribution;
        this.poidsDeveloppement = poidsDeveloppement;
    }

    @Transient
    @AssertTrue(message = "Les poids du score de performance doivent totaliser 100")
    public boolean isSommeValide() {
        return Poids.sommeValide(poidsObjectifs, poidsCompetences, poidsComportement,
                poidsContribution, poidsDeveloppement);
    }

    public BigDecimal getPoidsObjectifs() {
        return poidsObjectifs;
    }

    public void setPoidsObjectifs(BigDecimal poidsObjectifs) {
        this.poidsObjectifs = poidsObjectifs;
    }

    public BigDecimal getPoidsCompetences() {
        return poidsCompetences;
    }

    public void setPoidsCompetences(BigDecimal poidsCompetences) {
        this.poidsCompetences = poidsCompetences;
    }

    public BigDecimal getPoidsComportement() {
        return poidsComportement;
    }

    public void setPoidsComportement(BigDecimal poidsComportement) {
        this.poidsComportement = poidsComportement;
    }

    public BigDecimal getPoidsContribution() {
        return poidsContribution;
    }

    public void setPoidsContribution(BigDecimal poidsContribution) {
        this.poidsContribution = poidsContribution;
    }

    public BigDecimal getPoidsDeveloppement() {
        return poidsDeveloppement;
    }

    public void setPoidsDeveloppement(BigDecimal poidsDeveloppement) {
        this.poidsDeveloppement = poidsDeveloppement;
    }
}
