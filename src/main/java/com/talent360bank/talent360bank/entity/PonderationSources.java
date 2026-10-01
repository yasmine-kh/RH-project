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
 * Part de chaque source dans les scores officiels de performance et de
 * potentiel : le score de l'evaluation du manager et celui de l'auto-evaluation,
 * chacun calcule avec les poids des criteres, puis melanges selon ces poids.
 *
 * <p>100 / 0 par defaut : le score officiel est celui du manager, comme dans le
 * classeur, tant que le client n'a pas choisi de melange (AUDIT_REPORT,
 * question 2). Les colonnes portent ces valeurs par defaut en base : quand
 * ddl-auto=update les ajoute a une table deja remplie, les reglages existants
 * restent a 100 % manager.
 *
 * <p>Colonnes pond_src_* : les anciennes src_* (quatre poids de sources que
 * rien n'utilisait, retires) restent en base, nullables, et ne sont pas reprises.
 */
@Embeddable
public class PonderationSources {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("100")
    @Column(name = "pond_src_manager", precision = 5, scale = 2)
    private BigDecimal poidsManager;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("0")
    @Column(name = "pond_src_auto", precision = 5, scale = 2)
    private BigDecimal poidsAuto;

    public PonderationSources() {
    }

    public PonderationSources(BigDecimal poidsManager, BigDecimal poidsAuto) {
        this.poidsManager = poidsManager;
        this.poidsAuto = poidsAuto;
    }

    @Transient
    @AssertTrue(message = "Les poids des sources d'evaluation (manager et auto-evaluation) doivent totaliser 100")
    public boolean isSommeValide() {
        return Poids.sommeValide(poidsManager, poidsAuto);
    }

    public BigDecimal getPoidsManager() {
        return poidsManager;
    }

    public void setPoidsManager(BigDecimal poidsManager) {
        this.poidsManager = poidsManager;
    }

    public BigDecimal getPoidsAuto() {
        return poidsAuto;
    }

    public void setPoidsAuto(BigDecimal poidsAuto) {
        this.poidsAuto = poidsAuto;
    }
}
