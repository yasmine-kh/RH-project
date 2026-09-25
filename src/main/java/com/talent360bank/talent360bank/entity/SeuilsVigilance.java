package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Seuils de la vigilance, tels que les fixe 00_PARAMETRES.
 *
 * <p>Classement de l'indice : < modere = FAIBLE, < eleve = MODEREE, sinon
 * ELEVEE. S'y ajoute le seuil de declenchement du signal ENGAGEMENT_FAIBLE :
 * en dessous, l'engagement est juge faible. Il est ici et non dans le service
 * parce que le RH doit pouvoir le deplacer sans toucher au code, comme les
 * deux autres.
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

    @NotNull
    @DecimalMin("0")
    @Column(name = "seuil_vig_engagement_faible", precision = 5, scale = 2)
    private BigDecimal seuilEngagementFaible;

    public SeuilsVigilance() {
    }

    public SeuilsVigilance(BigDecimal seuilModere, BigDecimal seuilEleve,
                           BigDecimal seuilEngagementFaible) {
        this.seuilModere = seuilModere;
        this.seuilEleve = seuilEleve;
        this.seuilEngagementFaible = seuilEngagementFaible;
    }

    /**
     * Ne porte que sur les deux seuils de classement de l'indice. Le seuil
     * d'engagement est sur une autre echelle (le score du questionnaire) et
     * n'a pas a s'ordonner avec eux.
     */
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

    public BigDecimal getSeuilEngagementFaible() {
        return seuilEngagementFaible;
    }

    public void setSeuilEngagementFaible(BigDecimal seuilEngagementFaible) {
        this.seuilEngagementFaible = seuilEngagementFaible;
    }
}
