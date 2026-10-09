package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Un evenement de l'historique (ecran /historique) : un import, une decision du Comite Talent, un
 * changement de reglages, l'ajout ou la suppression d'un collaborateur. Ecrit par JournalService au
 * moment de l'action, ou rattrape au demarrage depuis import_excel et les decisions datees
 * (JournalRattrapage). Jamais modifie ensuite.
 */
@Entity
@Table(name = "journal_evenement", indexes = {
        @Index(name = "idx_journal_date", columnList = "date_evenement"),
        @Index(name = "idx_journal_reference", columnList = "reference"),
        @Index(name = "idx_journal_matricule", columnList = "matricule")})
public class JournalEvenement {

    public static final int DESCRIPTION_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idEvenement;

    @NotNull
    @Column(name = "date_evenement", nullable = false)
    private LocalDateTime dateEvenement;

    /** Compte RH a l'origine de l'action ; null si inconnu (hors requete, import ancien). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeEvenement type;

    /** Trimestre concerne ; null pour un evenement hors trimestre (collaborateur, import refuse). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre")
    private Trimestre trimestre;

    @NotBlank
    @Size(max = DESCRIPTION_MAX)
    @Column(nullable = false, length = DESCRIPTION_MAX)
    private String description;

    /** Lien vers la page concernee (Fiche, Comite, Import...), chemin local ; null sans page. */
    @Size(max = 300)
    @Column(length = 300)
    private String lien;

    /** Collaborateur concerne (matricule, sans cle etrangere : l'historique survit a une suppression). */
    @Size(max = 20)
    @Column(length = 20)
    private String matricule;

    /** Ligne a l'origine de l'evenement ("import_excel:12", "validation_comite:5"...) : sert au rattrapage. */
    @Size(max = 60)
    @Column(length = 60)
    private String reference;

    public JournalEvenement() {
    }

    public JournalEvenement(LocalDateTime dateEvenement, Utilisateur utilisateur, TypeEvenement type,
                            Trimestre trimestre, String description, String lien, String matricule,
                            String reference) {
        this.dateEvenement = dateEvenement;
        this.utilisateur = utilisateur;
        this.type = type;
        this.trimestre = trimestre;
        this.description = description.length() > DESCRIPTION_MAX
                ? description.substring(0, DESCRIPTION_MAX - 1) + "…" : description;
        this.lien = lien;
        this.matricule = matricule;
        this.reference = reference;
    }

    public Integer getIdEvenement() { return idEvenement; }
    public LocalDateTime getDateEvenement() { return dateEvenement; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public TypeEvenement getType() { return type; }
    public Trimestre getTrimestre() { return trimestre; }
    public String getDescription() { return description; }
    public String getLien() { return lien; }
    public String getMatricule() { return matricule; }
    public String getReference() { return reference; }
}
