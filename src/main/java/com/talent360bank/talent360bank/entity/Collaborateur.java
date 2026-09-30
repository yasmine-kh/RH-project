package com.talent360bank.talent360bank.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "collaborateur", indexes = {
        @Index(name = "idx_collaborateur_statut", columnList = "statut"),
        @Index(name = "idx_collaborateur_manager", columnList = "id_manager"),
        @Index(name = "idx_collaborateur_entite", columnList = "id_entite")
})
public class Collaborateur {

    /**
     * Employee_ID du fichier source, utilise directement comme cle primaire.
     * CollaborateurRepository et les cles etrangeres id_collaborateur des autres
     * tables en dependent : ce n'est pas un identifiant technique interchangeable.
     */
    @Id
    @NotBlank
    @Size(max = 20)
    @Column(name = "id_collaborateur", length = 20)
    private String idCollaborateur;

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

    /**
     * Entite la plus fine connue (agence, sinon region, departement ou
     * direction). Direction, departement, region et agence s'en deduisent en
     * remontant l'arbre : voir {@link #getDirection()}. Chargee avec le
     * collaborateur, car la plupart des ecrans regroupent par direction.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_entite")
    private Entite entite;

    @Size(max = 100)
    @Column(length = 100)
    private String fonction;

    @Size(max = 50)
    @Column(length = 50)
    private String grade;

    /** Manager_ID du fichier source, resolu en relation a l'import. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_manager")
    private Manager manager;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutCollaborateur statut = StatutCollaborateur.ACTIF;

    public Collaborateur() {
    }

    /**
     * Anciennete en annees revolues, derivee de dateEntree.
     * Non persistee : stockee, elle serait fausse des le lendemain de l'import.
     * Retourne null si la date d'entree est inconnue.
     */
    @Transient
    public Integer getAnciennete(LocalDate dateReference) {
        if (dateEntree == null || dateReference == null) {
            return null;
        }
        return Period.between(dateEntree, dateReference).getYears();
    }

    @Transient
    public Integer getAnciennete() {
        return getAnciennete(LocalDate.now());
    }

    /** Seuls les collaborateurs actifs entrent dans les calculs de scores. */
    @Transient
    public boolean estCalculable() {
        return statut == StatutCollaborateur.ACTIF;
    }

    @Transient
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    public String getIdCollaborateur() {
        return idCollaborateur;
    }

    public void setIdCollaborateur(String idCollaborateur) {
        this.idCollaborateur = idCollaborateur;
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

    public Entite getEntite() {
        return entite;
    }

    public void setEntite(Entite entite) {
        this.entite = entite;
    }

    /** Libelle de la direction, en remontant depuis {@link #getEntite()} ; null si inconnue. */
    @Transient
    public String getDirection() {
        return entite == null ? null : entite.libelleDe(TypeEntite.DIRECTION);
    }

    @Transient
    public String getDepartement() {
        return entite == null ? null : entite.libelleDe(TypeEntite.DEPARTEMENT);
    }

    @Transient
    public String getRegion() {
        return entite == null ? null : entite.libelleDe(TypeEntite.REGION);
    }

    @Transient
    public String getAgence() {
        return entite == null ? null : entite.libelleDe(TypeEntite.AGENCE);
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

    /** Ignore en JSON : relation paresseuse, et le manager renverrait a son propre collaborateur. */
    @JsonIgnore
    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public StatutCollaborateur getStatut() {
        return statut;
    }

    public void setStatut(StatutCollaborateur statut) {
        this.statut = statut;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Collaborateur)) {
            return false;
        }
        Collaborateur autre = (Collaborateur) o;
        return idCollaborateur != null && idCollaborateur.equals(autre.getIdCollaborateur());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(idCollaborateur);
    }

    /**
     * Sans nom ni prenom : toString finit dans les logs et les messages
     * d'erreur, qui ne doivent pas porter de donnees personnelles.
     */
    @Override
    public String toString() {
        return "Collaborateur{idCollaborateur='" + idCollaborateur + "', statut=" + statut + "}";
    }
}
