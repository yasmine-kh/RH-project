package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ponderation")
public class Ponderation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPonderation;

    @Column(name = "poids_auto_evaluation")
    private BigDecimal poidsAutoEvaluation;

    @Column(name = "poids_manager")
    private BigDecimal poidsManager;

    @Column(name = "poids_competences")
    private BigDecimal poidsCompetences;

    @Column(name = "poids_engagement")
    private BigDecimal poidsEngagement;

    @Column(name = "seuil_bas")
    private BigDecimal seuilBas;

    @Column(name = "seuil_haut")
    private BigDecimal seuilHaut;

    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    public Ponderation() {
    }

    public void configurer() {
        // logique de validation/écriture à faire dans le service
    }

    public void valider() {
        // logique de validation à faire dans le service
    }

    public Integer getIdPonderation() {
        return idPonderation;
    }

    public void setIdPonderation(Integer idPonderation) {
        this.idPonderation = idPonderation;
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

    public BigDecimal getSeuilBas() {
        return seuilBas;
    }

    public void setSeuilBas(BigDecimal seuilBas) {
        this.seuilBas = seuilBas;
    }

    public BigDecimal getSeuilHaut() {
        return seuilHaut;
    }

    public void setSeuilHaut(BigDecimal seuilHaut) {
        this.seuilHaut = seuilHaut;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }
}