package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "trimestre")
public class Trimestre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTrimestre;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private Integer annee;

    public Trimestre() {
    }

    public Integer getIdTrimestre() {
        return idTrimestre;
    }

    public void setIdTrimestre(Integer idTrimestre) {
        this.idTrimestre = idTrimestre;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public Integer getAnnee() {
        return annee;
    }

    public void setAnnee(Integer annee) {
        this.annee = annee;
    }
}