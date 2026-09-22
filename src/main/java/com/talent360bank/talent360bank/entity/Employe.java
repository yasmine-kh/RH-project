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
        @Index(name = "idx_employe_manager", columnList = "id_manager")
})
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idEmploye;

    /** Employee_ID du fichier source. Cle metier, ne change jamais. */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String matricule;

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

    @Size(max = 100)
    @Column(length = 100)
    private String poste;

    @Size(max = 100)
    @Column(length = 100)
    private String service;

    /** Manager_ID du fichier source, resolu en relation a l'import. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_manager")
    private Employe manager;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEmploye statut = StatutEmploye.ACTIF;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private UtilisateurRH utilisateur;

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

    public Integer getIdEmploye() {
        return idEmploye;
    }

    public void setIdEmploye(Integer idEmploye) {
        this.idEmploye = idEmploye;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
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

    public String getPoste() {
        return poste;
    }

    public void setPoste(String poste) {
        this.poste = poste;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
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

    public UtilisateurRH getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(UtilisateurRH utilisateur) {
        this.utilisateur = utilisateur;
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
        return matricule != null && matricule.equals(autre.getMatricule());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(matricule);
    }

    @Override
    public String toString() {
        return "Employe{matricule='" + matricule + "', nom='" + nom + "', prenom='" + prenom + "'}";
    }
}
