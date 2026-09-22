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
 * Seuils de conversion du score de matching en delai de disponibilite :
 * >= readyNow, sinon >= moins1An, sinon >= entre1Et2Ans, sinon plus de 2 ans.
 */
@Embeddable
public class SeuilsReadiness {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_ready_now", precision = 5, scale = 2)
    private BigDecimal seuilReadyNow;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_moins_1_an", precision = 5, scale = 2)
    private BigDecimal seuilMoins1An;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "seuil_1_2_ans", precision = 5, scale = 2)
    private BigDecimal seuilEntre1Et2Ans;

    public SeuilsReadiness() {
    }

    public SeuilsReadiness(BigDecimal seuilReadyNow, BigDecimal seuilMoins1An,
                           BigDecimal seuilEntre1Et2Ans) {
        this.seuilReadyNow = seuilReadyNow;
        this.seuilMoins1An = seuilMoins1An;
        this.seuilEntre1Et2Ans = seuilEntre1Et2Ans;
    }

    @Transient
    @AssertTrue(message = "Les seuils de readiness doivent etre strictement decroissants")
    public boolean isOrdreValide() {
        return Poids.ordreDecroissant(seuilReadyNow, seuilMoins1An, seuilEntre1Et2Ans);
    }

    public BigDecimal getSeuilReadyNow() {
        return seuilReadyNow;
    }

    public void setSeuilReadyNow(BigDecimal seuilReadyNow) {
        this.seuilReadyNow = seuilReadyNow;
    }

    public BigDecimal getSeuilMoins1An() {
        return seuilMoins1An;
    }

    public void setSeuilMoins1An(BigDecimal seuilMoins1An) {
        this.seuilMoins1An = seuilMoins1An;
    }

    public BigDecimal getSeuilEntre1Et2Ans() {
        return seuilEntre1Et2Ans;
    }

    public void setSeuilEntre1Et2Ans(BigDecimal seuilEntre1Et2Ans) {
        this.seuilEntre1Et2Ans = seuilEntre1Et2Ans;
    }
}
