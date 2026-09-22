package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Poids des 7 criteres du score de potentiel. */
@Embeddable
public class PoidsPotentiel {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_learning", precision = 5, scale = 2)
    private BigDecimal poidsLearning;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_leadership", precision = 5, scale = 2)
    private BigDecimal poidsLeadership;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_adaptabilite", precision = 5, scale = 2)
    private BigDecimal poidsAdaptabilite;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_complexite", precision = 5, scale = 2)
    private BigDecimal poidsComplexite;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_mobilite", precision = 5, scale = 2)
    private BigDecimal poidsMobilite;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_strategie", precision = 5, scale = 2)
    private BigDecimal poidsStrategie;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "pot_autonomie", precision = 5, scale = 2)
    private BigDecimal poidsAutonomie;

    public PoidsPotentiel() {
    }

    public PoidsPotentiel(BigDecimal poidsLearning, BigDecimal poidsLeadership,
                          BigDecimal poidsAdaptabilite, BigDecimal poidsComplexite,
                          BigDecimal poidsMobilite, BigDecimal poidsStrategie,
                          BigDecimal poidsAutonomie) {
        this.poidsLearning = poidsLearning;
        this.poidsLeadership = poidsLeadership;
        this.poidsAdaptabilite = poidsAdaptabilite;
        this.poidsComplexite = poidsComplexite;
        this.poidsMobilite = poidsMobilite;
        this.poidsStrategie = poidsStrategie;
        this.poidsAutonomie = poidsAutonomie;
    }

    @Transient
    @AssertTrue(message = "Les poids du score de potentiel doivent totaliser 100")
    public boolean isSommeValide() {
        return Poids.sommeValide(poidsLearning, poidsLeadership, poidsAdaptabilite,
                poidsComplexite, poidsMobilite, poidsStrategie, poidsAutonomie);
    }

    public BigDecimal getPoidsLearning() {
        return poidsLearning;
    }

    public void setPoidsLearning(BigDecimal poidsLearning) {
        this.poidsLearning = poidsLearning;
    }

    public BigDecimal getPoidsLeadership() {
        return poidsLeadership;
    }

    public void setPoidsLeadership(BigDecimal poidsLeadership) {
        this.poidsLeadership = poidsLeadership;
    }

    public BigDecimal getPoidsAdaptabilite() {
        return poidsAdaptabilite;
    }

    public void setPoidsAdaptabilite(BigDecimal poidsAdaptabilite) {
        this.poidsAdaptabilite = poidsAdaptabilite;
    }

    public BigDecimal getPoidsComplexite() {
        return poidsComplexite;
    }

    public void setPoidsComplexite(BigDecimal poidsComplexite) {
        this.poidsComplexite = poidsComplexite;
    }

    public BigDecimal getPoidsMobilite() {
        return poidsMobilite;
    }

    public void setPoidsMobilite(BigDecimal poidsMobilite) {
        this.poidsMobilite = poidsMobilite;
    }

    public BigDecimal getPoidsStrategie() {
        return poidsStrategie;
    }

    public void setPoidsStrategie(BigDecimal poidsStrategie) {
        this.poidsStrategie = poidsStrategie;
    }

    public BigDecimal getPoidsAutonomie() {
        return poidsAutonomie;
    }

    public void setPoidsAutonomie(BigDecimal poidsAutonomie) {
        this.poidsAutonomie = poidsAutonomie;
    }
}
