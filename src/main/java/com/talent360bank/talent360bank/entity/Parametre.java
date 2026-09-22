package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "parametre")
public class Parametre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idParametre;

    private String section;

    private String critere;

    private Double valeur;

    private String commentaire;

    public Parametre() {}

    public Integer getIdParametre() { return idParametre; }
    public void setIdParametre(Integer idParametre) { this.idParametre = idParametre; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getCritere() { return critere; }
    public void setCritere(String critere) { this.critere = critere; }
    public Double getValeur() { return valeur; }
    public void setValeur(Double valeur) { this.valeur = valeur; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}