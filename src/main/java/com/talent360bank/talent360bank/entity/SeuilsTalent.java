package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Seuils de detection automatique d'un talent : performance ET potentiel
 * doivent tous deux atteindre leur seuil.
 */
@Embeddable
public class SeuilsTalent {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_talent_perf", precision = 5, scale = 2)
    private BigDecimal seuilPerformance;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_talent_pot", precision = 5, scale = 2)
    private BigDecimal seuilPotentiel;

    public SeuilsTalent() {
    }

    public SeuilsTalent(BigDecimal seuilPerformance, BigDecimal seuilPotentiel) {
        this.seuilPerformance = seuilPerformance;
        this.seuilPotentiel = seuilPotentiel;
    }

    public BigDecimal getSeuilPerformance() {
        return seuilPerformance;
    }

    public void setSeuilPerformance(BigDecimal seuilPerformance) {
        this.seuilPerformance = seuilPerformance;
    }

    public BigDecimal getSeuilPotentiel() {
        return seuilPotentiel;
    }

    public void setSeuilPotentiel(BigDecimal seuilPotentiel) {
        this.seuilPotentiel = seuilPotentiel;
    }
}
