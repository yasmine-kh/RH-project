package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "competence")
public class Competence {

    @Id
    private String competenceId;

    private String nom;

    private String categorie;

    public Competence() {}

    public String getCompetenceId() { return competenceId; }
    public void setCompetenceId(String competenceId) { this.competenceId = competenceId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
}