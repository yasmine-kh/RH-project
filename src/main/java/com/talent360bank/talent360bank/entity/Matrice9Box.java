package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "matrice_9box")
public class Matrice9Box {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPosition;

    @Column(name = "niveau_performance", nullable = false)
    private Integer niveauPerformance;

    @Column(name = "niveau_potentiel", nullable = false)
    private Integer niveauPotentiel;

    @Column(nullable = false)
    private String categorie;

    public Matrice9Box() {
    }

    public Integer getIdPosition() {
        return idPosition;
    }

    public void setIdPosition(Integer idPosition) {
        this.idPosition = idPosition;
    }

    public Integer getNiveauPerformance() {
        return niveauPerformance;
    }

    public void setNiveauPerformance(Integer niveauPerformance) {
        this.niveauPerformance = niveauPerformance;
    }

    public Integer getNiveauPotentiel() {
        return niveauPotentiel;
    }

    public void setNiveauPotentiel(Integer niveauPotentiel) {
        this.niveauPotentiel = niveauPotentiel;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }
}