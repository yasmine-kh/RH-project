package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

@Entity
@Table(name = "employe", indexes = {
        @Index(name = "idx_employe_statut", columnList = "statut"),
        @Index(name = "idx_employe_manager", columnList = "manager_id")
})
public class Employe {

    /**
     * Employee_ID du fichier source, utilise directement comme cle primaire.
     * EmployeRepository et la cle etrangere employee_id d'EmployeeSkill en
     * dependent : ce n'est pas un identifiant technique interchangeable.
     */
    @Id
    @NotBlank
    @Size(max = 20)
    @Column(name = "employee_id", length = 20)
    private String employeeId;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nom;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sexe sexe;

    @Past
    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @NotNull
    @PastOrPresent
    @Column(name = "date_entree", nullable = false)
    private LocalDate dateEntree;

    @Size(max = 100)
    @Column(length = 100)
    private String direction;

    @Size(max = 100)
    @Column(length = 100)
    private String departement;

    @Size(max = 100)
    @Column(length = 100)
    private String region;

    @Size(max = 100)
    @Column(length = 100)
    private String agence;

    @Size(max = 100)
    @Column(length = 100)
    private String fonction;

    @Size(max = 50)
    @Column(length = 50)
    private String grade;

    /** Manager_ID du fichier source, resolu en relation a l'import. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employe manager;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEmploye statut = StatutEmploye.ACTIF;

    public Employe() {
    }

    /**
     * Anciennete en annees revolues, derivee de dateEntree.
     * Non persistee : stockee, elle serait fausse des le lendemain de l'import.
     * Retourne null si la date d'entree est inconnue.
     */
    @Transient
    public Integer getAnciennete() {
        if (dateEntree == null) {
            return null;
        }
        return Period.between(dateEntree, LocalDate.now()).getYears();
    }

    /** Seuls les employes actifs entrent dans les calculs de scores. */
    @Transient
    public boolean estCalculable() {
        return statut == StatutEmploye.ACTIF;
    }

    @Transient
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public Sexe getSexe() {
        return sexe;
    }

    public void setSexe(Sexe sexe) {
        this.sexe = sexe;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public LocalDate getDateEntree() {
        return dateEntree;
    }

    public void setDateEntree(LocalDate dateEntree) {
        this.dateEntree = dateEntree;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getDepartement() {
        return departement;
    }

    public void setDepartement(String departement) {
        this.departement = departement;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAgence() {
        return agence;
    }

    public void setAgence(String agence) {
        this.agence = agence;
    }

    public String getFonction() {
        return fonction;
    }

    public void setFonction(String fonction) {
        this.fonction = fonction;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Employe getManager() {
        return manager;
    }

    public void setManager(Employe manager) {
        this.manager = manager;
    }

    public StatutEmploye getStatut() {
        return statut;
    }

    public void setStatut(StatutEmploye statut) {
        this.statut = statut;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Employe)) {
            return false;
        }
        Employe autre = (Employe) o;
        return employeeId != null && employeeId.equals(autre.getEmployeeId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(employeeId);
    }

    @Override
    public String toString() {
        return "Employe{employeeId='" + employeeId + "', nom='" + nom + "', prenom='" + prenom + "'}";
    }
}
