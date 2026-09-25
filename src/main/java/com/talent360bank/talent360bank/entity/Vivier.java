package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "vivier")
public class Vivier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idVivier;

    @NotBlank(message = "Le nom de la catégorie est obligatoire")
    @Column(name = "nom_categorie", nullable = false)
    private String nomCategorie;

    private String description;

    public Vivier() {
    }

    public Integer getIdVivier() {
        return idVivier;
    }

    public void setIdVivier(Integer idVivier) {
        this.idVivier = idVivier;
    }

    public String getNomCategorie() {
        return nomCategorie;
    }

    public void setNomCategorie(String nomCategorie) {
        this.nomCategorie = nomCategorie;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}