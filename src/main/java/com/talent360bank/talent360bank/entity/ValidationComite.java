package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Decision du Comite Talent sur un collaborateur pour un trimestre (10_TALENTS,
 * colonne Validation Comite Talent). Pas de ligne : decision en attente.
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
}
