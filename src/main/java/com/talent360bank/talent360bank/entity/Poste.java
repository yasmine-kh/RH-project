package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "poste")
public class Poste {

    @Id
    private String posteId;

    @Column(name = "nom_poste")
    private String nomPoste;

    private String direction;

    @Column(name = "grade_cible")
    private String gradeCible;

    private String criticite;

    @Column(name = "competence_requise_1")
    private String competenceRequise1;
    private Integer niveau1;

    @Column(name = "competence_requise_2")
    private String competenceRequise2;
    private Integer niveau2;

    @Column(name = "competence_requise_3")
    private String competenceRequise3;
    private Integer niveau3;

    @Column(name = "competence_requise_4")
    private String competenceRequise4;
    private Integer niveau4;

    @Column(name = "competence_requise_5")
    private String competenceRequise5;
    private Integer niveau5;

    @Column(name = "poste_critique")
    private String posteCritique;

    @Column(name = "titulaire_id")
    private String titulaireId;

    @Column(name = "titulaire_nom")
    private String titulaireNom;

    public Poste() {}

    public String getPosteId() { return posteId; }
    public void setPosteId(String posteId) { this.posteId = posteId; }
    public String getNomPoste() { return nomPoste; }
    public void setNomPoste(String nomPoste) { this.nomPoste = nomPoste; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getGradeCible() { return gradeCible; }
    public void setGradeCible(String gradeCible) { this.gradeCible = gradeCible; }
    public String getCriticite() { return criticite; }
    public void setCriticite(String criticite) { this.criticite = criticite; }
    public String getCompetenceRequise1() { return competenceRequise1; }
    public void setCompetenceRequise1(String v) { this.competenceRequise1 = v; }
    public Integer getNiveau1() { return niveau1; }
    public void setNiveau1(Integer v) { this.niveau1 = v; }
    public String getCompetenceRequise2() { return competenceRequise2; }
    public void setCompetenceRequise2(String v) { this.competenceRequise2 = v; }
    public Integer getNiveau2() { return niveau2; }
    public void setNiveau2(Integer v) { this.niveau2 = v; }
    public String getCompetenceRequise3() { return competenceRequise3; }
    public void setCompetenceRequise3(String v) { this.competenceRequise3 = v; }
    public Integer getNiveau3() { return niveau3; }
    public void setNiveau3(Integer v) { this.niveau3 = v; }
    public String getCompetenceRequise4() { return competenceRequise4; }
    public void setCompetenceRequise4(String v) { this.competenceRequise4 = v; }
    public Integer getNiveau4() { return niveau4; }
    public void setNiveau4(Integer v) { this.niveau4 = v; }
    public String getCompetenceRequise5() { return competenceRequise5; }
    public void setCompetenceRequise5(String v) { this.competenceRequise5 = v; }
    public Integer getNiveau5() { return niveau5; }
    public void setNiveau5(Integer v) { this.niveau5 = v; }
    public String getPosteCritique() { return posteCritique; }
    public void setPosteCritique(String posteCritique) { this.posteCritique = posteCritique; }
    public String getTitulaireId() { return titulaireId; }
    public void setTitulaireId(String titulaireId) { this.titulaireId = titulaireId; }
    public String getTitulaireNom() { return titulaireNom; }
    public void setTitulaireNom(String titulaireNom) { this.titulaireNom = titulaireNom; }
}