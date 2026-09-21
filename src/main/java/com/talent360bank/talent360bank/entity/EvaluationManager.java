package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "evaluation_manager")
public class EvaluationManager extends Evaluation {

    private BigDecimal score;

    @ManyToOne
    @JoinColumn(name = "id_employe", nullable = false)
    private Employe employe;

    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    public EvaluationManager() {
    }

    @Override
    public BigDecimal calculerScore() {
        return score;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Employe getEmploye() {
        return employe;
    }

    public void setEmploye(Employe employe) {
        this.employe = employe;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }
}