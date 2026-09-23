package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Les cinq competences attendues sur le poste referencent le referentiel
     * (05_REFERENTIEL_COMPETENCES) plutot que de porter un libelle en texte
     * libre : c'est par cet identifiant que le matching les rapproche des
     * EmployeeSkill du candidat, qui referencent deja la competence par ID.
     * Un nom recopie a la main ne se rapprocherait de rien.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_requise_1")
    private Competence competenceRequise1;
    private Integer niveau1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_requise_2")
    private Competence competenceRequise2;
    private Integer niveau2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_requise_3")
    private Competence competenceRequise3;
    private Integer niveau3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_requise_4")
    private Competence competenceRequise4;
    private Integer niveau4;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_requise_5")
    private Competence competenceRequise5;
    private Integer niveau5;

    @Column(name = "poste_critique")
    private String posteCritique;

    @Column(name = "titulaire_id")
    private String titulaireId;

    @Column(name = "titulaire_nom")
    private String titulaireNom;

    public Poste() {}

    /** Une competence attendue sur le poste et le niveau minimum exige. */
    public record ExigenceCompetence(Competence competence, Integer niveauRequis) {
    }

    /**
     * Les cinq colonnes competenceRequiseN / niveauN vues comme une liste, dans
     * l'ordre du fichier source. Les emplacements vides sont ecartes : un poste
     * peut n'exiger que deux competences, les trois autres colonnes sont nulles.
     */
    @Transient
    public List<ExigenceCompetence> getExigencesCompetences() {
        Competence[] competences = {competenceRequise1, competenceRequise2, competenceRequise3,
                competenceRequise4, competenceRequise5};
        Integer[] niveaux = {niveau1, niveau2, niveau3, niveau4, niveau5};

        List<ExigenceCompetence> exigences = new ArrayList<>();
        for (int i = 0; i < competences.length; i++) {
            if (competences[i] != null) {
                exigences.add(new ExigenceCompetence(competences[i], niveaux[i]));
            }
        }
        return exigences;
    }

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
    public Competence getCompetenceRequise1() { return competenceRequise1; }
    public void setCompetenceRequise1(Competence v) { this.competenceRequise1 = v; }
    public Integer getNiveau1() { return niveau1; }
    public void setNiveau1(Integer v) { this.niveau1 = v; }
    public Competence getCompetenceRequise2() { return competenceRequise2; }
    public void setCompetenceRequise2(Competence v) { this.competenceRequise2 = v; }
    public Integer getNiveau2() { return niveau2; }
    public void setNiveau2(Integer v) { this.niveau2 = v; }
    public Competence getCompetenceRequise3() { return competenceRequise3; }
    public void setCompetenceRequise3(Competence v) { this.competenceRequise3 = v; }
    public Integer getNiveau3() { return niveau3; }
    public void setNiveau3(Integer v) { this.niveau3 = v; }
    public Competence getCompetenceRequise4() { return competenceRequise4; }
    public void setCompetenceRequise4(Competence v) { this.competenceRequise4 = v; }
    public Integer getNiveau4() { return niveau4; }
    public void setNiveau4(Integer v) { this.niveau4 = v; }
    public Competence getCompetenceRequise5() { return competenceRequise5; }
    public void setCompetenceRequise5(Competence v) { this.competenceRequise5 = v; }
    public Integer getNiveau5() { return niveau5; }
    public void setNiveau5(Integer v) { this.niveau5 = v; }
    public String getPosteCritique() { return posteCritique; }
    public void setPosteCritique(String posteCritique) { this.posteCritique = posteCritique; }
    public String getTitulaireId() { return titulaireId; }
    public void setTitulaireId(String titulaireId) { this.titulaireId = titulaireId; }
    public String getTitulaireNom() { return titulaireNom; }
    public void setTitulaireNom(String titulaireNom) { this.titulaireNom = titulaireNom; }
}
