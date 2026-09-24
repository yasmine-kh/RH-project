package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import java.math.BigDecimal;

/**
 * Seuils de detection automatique, tels que les fixe 00_PARAMETRES section 5 :
 * un talent a sa performance ET son potentiel a leur seuil ; un haut potentiel
 * a son potentiel ET sa performance a leurs propres seuils (85 et 75). Les deux
 * statuts sont independants, un employe peut cumuler les deux.
 *
 * <p>Les seuils de haut potentiel, ajoutes apres coup, portent leur valeur par
 * defaut en base comme les autres colonnes ajoutees (voir BaremeExperience).
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

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("85")
    @Column(name = "seuil_hp_pot", precision = 5, scale = 2)
    private BigDecimal seuilHautPotentielPotentiel;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("75")
    @Column(name = "seuil_hp_perf", precision = 5, scale = 2)
    private BigDecimal seuilHautPotentielPerformance;

    public SeuilsTalent() {
    }

    public SeuilsTalent(BigDecimal seuilPerformance, BigDecimal seuilPotentiel,
                        BigDecimal seuilHautPotentielPotentiel, BigDecimal seuilHautPotentielPerformance) {
        this.seuilPerformance = seuilPerformance;
        this.seuilPotentiel = seuilPotentiel;
        this.seuilHautPotentielPotentiel = seuilHautPotentielPotentiel;
        this.seuilHautPotentielPerformance = seuilHautPotentielPerformance;
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

    public BigDecimal getSeuilHautPotentielPotentiel() {
        return seuilHautPotentielPotentiel;
    }

    public void setSeuilHautPotentielPotentiel(BigDecimal seuilHautPotentielPotentiel) {
        this.seuilHautPotentielPotentiel = seuilHautPotentielPotentiel;
    }

    public BigDecimal getSeuilHautPotentielPerformance() {
        return seuilHautPotentielPerformance;
    }

    public void setSeuilHautPotentielPerformance(BigDecimal seuilHautPotentielPerformance) {
        this.seuilHautPotentielPerformance = seuilHautPotentielPerformance;
    }
}
