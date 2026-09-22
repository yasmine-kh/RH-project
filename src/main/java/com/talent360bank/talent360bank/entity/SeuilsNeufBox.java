package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Seuils de decoupage des axes de la matrice 9-box. Les memes valeurs
 * s'appliquent a la performance et au potentiel : score >= eleve, sinon
 * >= moyen, sinon faible.
 */
@Embeddable
public class SeuilsNeufBox {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_box_eleve", precision = 5, scale = 2)
    private BigDecimal seuilEleve;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_box_moyen", precision = 5, scale = 2)
    private BigDecimal seuilMoyen;

    public SeuilsNeufBox() {
    }

    public SeuilsNeufBox(BigDecimal seuilEleve, BigDecimal seuilMoyen) {
        this.seuilEleve = seuilEleve;
        this.seuilMoyen = seuilMoyen;
    }

    @Transient
    @AssertTrue(message = "Le seuil eleve doit etre strictement superieur au seuil moyen")
    public boolean isOrdreValide() {
        return Poids.ordreDecroissant(seuilEleve, seuilMoyen);
    }

    public BigDecimal getSeuilEleve() {
        return seuilEleve;
    }

    public void setSeuilEleve(BigDecimal seuilEleve) {
        this.seuilEleve = seuilEleve;
    }

    public BigDecimal getSeuilMoyen() {
        return seuilMoyen;
    }

    public void setSeuilMoyen(BigDecimal seuilMoyen) {
        this.seuilMoyen = seuilMoyen;
    }
}
