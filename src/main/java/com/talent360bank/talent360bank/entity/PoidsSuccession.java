package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Poids des 6 criteres du matching candidat / poste cible. */
@Embeddable
public class PoidsSuccession {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "succ_competences", precision = 5, scale = 2)
    private BigDecimal poidsCompetences;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "succ_performance", precision = 5, scale = 2)
    private BigDecimal poidsPerformance;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "succ_potentiel", precision = 5, scale = 2)
    private BigDecimal poidsPotentiel;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "succ_experience", precision = 5, scale = 2)
    private BigDecimal poidsExperience;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "succ_leadership", precision = 5, scale = 2)
    private BigDecimal poidsLeadership;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "succ_mobilite", precision = 5, scale = 2)
    private BigDecimal poidsMobilite;

    public PoidsSuccession() {
    }

    public PoidsSuccession(BigDecimal poidsCompetences, BigDecimal poidsPerformance,
                           BigDecimal poidsPotentiel, BigDecimal poidsExperience,
                           BigDecimal poidsLeadership, BigDecimal poidsMobilite) {
        this.poidsCompetences = poidsCompetences;
        this.poidsPerformance = poidsPerformance;
        this.poidsPotentiel = poidsPotentiel;
        this.poidsExperience = poidsExperience;
        this.poidsLeadership = poidsLeadership;
        this.poidsMobilite = poidsMobilite;
    }

    @Transient
    @AssertTrue(message = "Les poids du matching succession doivent totaliser 100")
    public boolean isSommeValide() {
        return Poids.sommeValide(poidsCompetences, poidsPerformance, poidsPotentiel,
                poidsExperience, poidsLeadership, poidsMobilite);
    }

    public BigDecimal getPoidsCompetences() {
        return poidsCompetences;
    }

    public void setPoidsCompetences(BigDecimal poidsCompetences) {
        this.poidsCompetences = poidsCompetences;
    }

    public BigDecimal getPoidsPerformance() {
        return poidsPerformance;
    }

    public void setPoidsPerformance(BigDecimal poidsPerformance) {
        this.poidsPerformance = poidsPerformance;
    }

    public BigDecimal getPoidsPotentiel() {
        return poidsPotentiel;
    }

    public void setPoidsPotentiel(BigDecimal poidsPotentiel) {
        this.poidsPotentiel = poidsPotentiel;
    }

    public BigDecimal getPoidsExperience() {
        return poidsExperience;
    }

    public void setPoidsExperience(BigDecimal poidsExperience) {
        this.poidsExperience = poidsExperience;
    }

    public BigDecimal getPoidsLeadership() {
        return poidsLeadership;
    }

    public void setPoidsLeadership(BigDecimal poidsLeadership) {
        this.poidsLeadership = poidsLeadership;
    }

    public BigDecimal getPoidsMobilite() {
        return poidsMobilite;
    }

    public void setPoidsMobilite(BigDecimal poidsMobilite) {
        this.poidsMobilite = poidsMobilite;
    }
}
