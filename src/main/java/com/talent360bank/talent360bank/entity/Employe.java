package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "employe")
public class Employe {

    @Id
    private String employeeId;

    private String nom;
    private String prenom;
    private String sexe;
    private LocalDate dateNaissance;
    private LocalDate dateEntree;
    private Double anciennete;
    private String direction;
    private String departement;
    private String region;
    private String agence;
    private String fonction;
    private String grade;
    private String managerId;
    private String statut;

    public Employe() {}

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }
    public LocalDate getDateEntree() { return dateEntree; }
    public void setDateEntree(LocalDate dateEntree) { this.dateEntree = dateEntree; }
    public Double getAnciennete() { return anciennete; }
    public void setAnciennete(Double anciennete) { this.anciennete = anciennete; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAgence() { return agence; }
    public void setAgence(String agence) { this.agence = agence; }
    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
}