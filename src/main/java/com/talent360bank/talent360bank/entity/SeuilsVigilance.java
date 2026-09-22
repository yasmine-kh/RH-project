package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Seuils de classement de l'indice de vigilance :
 * < modere = FAIBLE, < eleve = MODEREE, sinon ELEVEE.
 */
@Embeddable
public class SeuilsVigilance {

    @NotNull
    @DecimalMin("0")
    @Column(name = "seuil_vig_modere", precision = 5, scale = 2)
    private BigDecimal seuilModere;

    @NotNull
    @DecimalMin("0")
    @Column(name = "seuil_vig_eleve", precision = 5, scale = 2)
    private BigDecimal seuilEleve;

    public SeuilsVigilance() {
    }

    public SeuilsVigilance(BigDecimal seuilModere, BigDecimal seuilEleve) {
        this.seuilModere = seuilModere;
        this.seuilEleve = seuilEleve;
    }

    @Transient
    @AssertTrue(message = "Le seuil eleve doit etre strictement superieur au seuil modere")
    public boolean isOrdreValide() {
        return Poids.ordreDecroissant(seuilEleve, seuilModere);
    }

    public BigDecimal getSeuilModere() {
        return seuilModere;
    }

    public void setSeuilModere(BigDecimal seuilModere) {
        this.seuilModere = seuilModere;
    }

    public BigDecimal getSeuilEleve() {
        return seuilEleve;
    }

    public void setSeuilEleve(BigDecimal seuilEleve) {
        this.seuilEleve = seuilEleve;
    }
}
