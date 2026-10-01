package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Successeur que le RH a retenu pour un poste (09_SUCCESSION, colonnes
 * Poste_ID et Employee_ID). Une saisie, pas un calcul : c'est sur ces
 * successeurs que se juge la couverture d'un poste critique.
 *
 * <p>Sans trimestre, comme le contrat SuccesseurIdentifieSource : le plan de
 * succession vaut jusqu'au prochain import.
 */
@Entity
@Table(name = "successeur_identifie", uniqueConstraints = @UniqueConstraint(
        name = "uk_successeur_poste_collaborateur",
        columnNames = {"id_poste", "id_collaborateur"}))
public class SuccesseurIdentifie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSuccesseur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_poste", nullable = false)
    private Poste poste;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    public SuccesseurIdentifie() {
    }

    public SuccesseurIdentifie(Poste poste, Collaborateur collaborateur) {
        this.poste = poste;
        this.collaborateur = collaborateur;
    }

    public Integer getIdSuccesseur() {
        return idSuccesseur;
    }

    public Poste getPoste() {
        return poste;
    }

    public void setPoste(Poste poste) {
        this.poste = poste;
    }

    public Collaborateur getCollaborateur() {
        return collaborateur;
    }

    public void setCollaborateur(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }
}
