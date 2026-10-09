package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.service.enums.DecisionSuccession;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Decision du Comite Talent sur un successeur d'un poste critique, pour un trimestre, saisie sur
 * l'ecran Comite Talent. Pas de ligne : succession a valider. Le classeur ne porte pas ces
 * decisions : l'import n'ecrit jamais cette table.
 */
@Entity
@Table(name = "validation_succession", uniqueConstraints = @UniqueConstraint(
        name = "uk_validation_succession_trimestre_poste_successeur",
        columnNames = {"id_trimestre", "id_poste", "id_collaborateur"}))
public class ValidationSuccession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idValidationSuccession;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_poste", nullable = false)
    private Poste poste;

    /** Le successeur. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur successeur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DecisionSuccession decision;

    @Column(name = "date_decision")
    private LocalDateTime dateDecision;

    /** Compte RH qui a saisi la decision (UtilisateurCourant) ; nul hors requete authentifiee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Size(max = ValidationComite.COMMENTAIRE_MAX)
    @Column(length = ValidationComite.COMMENTAIRE_MAX)
    private String commentaire;

    public ValidationSuccession() {
    }

    public ValidationSuccession(Trimestre trimestre, Poste poste, Collaborateur successeur) {
        this.trimestre = trimestre;
        this.poste = poste;
        this.successeur = successeur;
    }

    /** Enregistre (ou remplace) la decision. */
    public void decider(DecisionSuccession decision, LocalDateTime date, Utilisateur utilisateur, String commentaire) {
        this.decision = decision;
        this.dateDecision = date;
        this.utilisateur = utilisateur;
        this.commentaire = commentaire;
    }

    public Integer getIdValidationSuccession() {
        return idValidationSuccession;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public Poste getPoste() {
        return poste;
    }

    public Collaborateur getSuccesseur() {
        return successeur;
    }

    public DecisionSuccession getDecision() {
        return decision;
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
}
