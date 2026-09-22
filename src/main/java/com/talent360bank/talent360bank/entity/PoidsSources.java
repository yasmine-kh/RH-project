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
 * Poids des sources d'evaluation : comment agreger AutoEvaluation,
 * EvaluationManager, les competences et QuestionnaireEngagement
 * en un score consolide.
 */
@Embeddable
public class PoidsSources {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "src_auto_evaluation", precision = 5, scale = 2)
    private BigDecimal poidsAutoEvaluation;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "src_manager", precision = 5, scale = 2)
    private BigDecimal poidsManager;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "src_competences", precision = 5, scale = 2)
    private BigDecimal poidsCompetences;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "src_engagement", precision = 5, scale = 2)
    private BigDecimal poidsEngagement;

    public PoidsSources() {
    }

    public PoidsSources(BigDecimal poidsAutoEvaluation, BigDecimal poidsManager,
                        BigDecimal poidsCompetences, BigDecimal poidsEngagement) {
        this.poidsAutoEvaluation = poidsAutoEvaluation;
        this.poidsManager = poidsManager;
        this.poidsCompetences = poidsCompetences;
        this.poidsEngagement = poidsEngagement;
    }

    @Transient
    @AssertTrue(message = "Les poids des sources d'evaluation doivent totaliser 100")
    public boolean isSommeValide() {
        return Poids.sommeValide(poidsAutoEvaluation, poidsManager, poidsCompetences, poidsEngagement);
    }

    public BigDecimal getPoidsAutoEvaluation() {
        return poidsAutoEvaluation;
    }

    public void setPoidsAutoEvaluation(BigDecimal poidsAutoEvaluation) {
        this.poidsAutoEvaluation = poidsAutoEvaluation;
    }

    public BigDecimal getPoidsManager() {
        return poidsManager;
    }

    public void setPoidsManager(BigDecimal poidsManager) {
        this.poidsManager = poidsManager;
    }

    public BigDecimal getPoidsCompetences() {
        return poidsCompetences;
    }

    public void setPoidsCompetences(BigDecimal poidsCompetences) {
        this.poidsCompetences = poidsCompetences;
    }

    public BigDecimal getPoidsEngagement() {
        return poidsEngagement;
    }

    public void setPoidsEngagement(BigDecimal poidsEngagement) {
        this.poidsEngagement = poidsEngagement;
    }
}
