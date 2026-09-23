package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "trimestre")
public class Trimestre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTrimestre;

    @NotNull(message = "Le numéro de trimestre est obligatoire")
    @Min(value = 1, message = "Le numéro doit être entre 1 et 4")
    @Max(value = 4, message = "Le numéro doit être entre 1 et 4")
    @Column(nullable = false)
    private Integer numero;

    @NotNull(message = "L'année est obligatoire")
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