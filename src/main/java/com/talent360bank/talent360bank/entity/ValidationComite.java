package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Decision du Comite Talent sur un collaborateur pour un trimestre (10_TALENTS,
 * colonne Validation Comite Talent, ou saisie sur l'ecran Comite Talent). Pas de
 * ligne : decision en attente.
 *
 * <p>Une decision saisie dans l'application porte sa date ({@link #estSaisieApplication()}) :
 * un nouvel import du meme trimestre ne la remplace ni ne la supprime.
 */
@Entity
@Table(name = "validation_comite", uniqueConstraints = @UniqueConstraint(
        name = "uk_validation_comite_collaborateur_trimestre",
        columnNames = {"id_collaborateur", "id_trimestre"}))
public class ValidationComite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idValidation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutValidationComite statut;

    /** Date de la decision saisie dans l'application ; nulle pour une decision importee. */
    @Column(name = "date_decision")
    private LocalDateTime dateDecision;

    /** Compte RH qui a saisi la decision (UtilisateurCourant) ; nul si importee ou hors requete. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Size(max = COMMENTAIRE_MAX)
    @Column(length = COMMENTAIRE_MAX)
    private String commentaire;

    public static final int COMMENTAIRE_MAX = 500;

    public ValidationComite() {
    }

    public ValidationComite(Collaborateur collaborateur, Trimestre trimestre, StatutValidationComite statut) {
        this.collaborateur = collaborateur;
        this.trimestre = trimestre;
        this.statut = statut;
    }

    public Integer getIdValidation() {
        return idValidation;
    }

    public Collaborateur getCollaborateur() {
        return collaborateur;
    }

    public void setCollaborateur(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }

    public StatutValidationComite getStatut() {
        return statut;
    }

    public void setStatut(StatutValidationComite statut) {
        this.statut = statut;
    }

    public LocalDateTime getDateDecision() {
        return dateDecision;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public String getCommentaire() {
        return commentaire;
    }

    /** Decision prise dans l'application (et non lue dans le classeur). */
    public boolean estSaisieApplication() {
        return dateDecision != null;
    }

    /** Enregistre une decision du comite saisie dans l'application. */
    public void decider(StatutValidationComite statut, LocalDateTime date, Utilisateur utilisateur,
                        String commentaire) {
        this.statut = statut;
        this.dateDecision = date;
        this.utilisateur = utilisateur;
        this.commentaire = commentaire;
    }
}
