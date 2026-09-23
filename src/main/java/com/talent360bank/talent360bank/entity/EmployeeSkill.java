package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "employee_skill")
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSkill;

    @Column(name = "cle_lookup")
    private String cleLookup;

    @NotNull(message = "L'employé est obligatoire")
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employe employe;

    @NotNull(message = "La compétence est obligatoire")
    @ManyToOne
    @JoinColumn(name = "competence_id", nullable = false)
    private Competence competence;

    @NotNull(message = "Le niveau actuel est obligatoire")
    @Column(name = "niveau_actuel")
    private Integer niveauActuel;

    @NotNull(message = "Le niveau cible est obligatoire")
    @Column(name = "niveau_cible")
    private Integer niveauCible;

    private Integer gap;

    @Column(name = "statut_gap")
    private String statutGap;

    public EmployeeSkill() {}

    public Integer getIdSkill() { return idSkill; }
    public void setIdSkill(Integer idSkill) { this.idSkill = idSkill; }
    public String getCleLookup() { return cleLookup; }
    public void setCleLookup(String cleLookup) { this.cleLookup = cleLookup; }
    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }
    public Competence getCompetence() { return competence; }
    public void setCompetence(Competence competence) { this.competence = competence; }
    public Integer getNiveauActuel() { return niveauActuel; }
    public void setNiveauActuel(Integer niveauActuel) { this.niveauActuel = niveauActuel; }
    public Integer getNiveauCible() { return niveauCible; }
    public void setNiveauCible(Integer niveauCible) { this.niveauCible = niveauCible; }
    public Integer getGap() { return gap; }
    public void setGap(Integer gap) { this.gap = gap; }
    public String getStatutGap() { return statutGap; }
    public void setStatutGap(String statutGap) { this.statutGap = statutGap; }
}