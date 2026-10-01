package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import java.math.BigDecimal;

/**
 * Notes brutes des 5 criteres de performance d'un collaborateur sur un trimestre,
 * donnees par son manager ou par le collaborateur lui-meme ({@link #getSource()}) :
 * une ligne au plus par collaborateur, trimestre et source.
 * Le score pondere qui en decoule est calcule par le service et stocke
 * dans {@link Score} : cette entite ne porte que la saisie.
 */
@Entity
@Table(name = "performance", uniqueConstraints = @UniqueConstraint(
        name = "uk_performance_collaborateur_trimestre_source",
        columnNames = {"id_collaborateur", "id_trimestre", "source_evaluation"}))
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPerformance;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    /**
     * Qui a rempli cette evaluation. MANAGER pour les lignes anterieures a
     * l'auto-evaluation et pour l'import du classeur (colonne creee avec cette
     * valeur par defaut, voir SourceEvaluationInitializer).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MANAGER'")
    @Column(name = "source_evaluation", nullable = false, length = 10)
    private SourceEvaluation source = SourceEvaluation.MANAGER;

    /**
     * Manager qui a evalue, null si inconnu ou pour une auto-evaluation.
     * 02_PERFORMANCE ne le nomme pas : l'import reprend le manager du
     * collaborateur (01_COLLABORATEURS N) au moment de l'import.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_manager_evaluateur")
    private Manager evaluateur;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_objectifs", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteObjectifs;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_competences", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteCompetences;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_comportement", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteComportement;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_contribution", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteContribution;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_developpement", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteDeveloppement;

    public Performance() {
    }

    public Performance(Collaborateur collaborateur, Trimestre trimestre, BigDecimal noteObjectifs,
                       BigDecimal noteCompetences, BigDecimal noteComportement,
                       BigDecimal noteContribution, BigDecimal noteDeveloppement) {
        this.collaborateur = collaborateur;
        this.trimestre = trimestre;
        this.noteObjectifs = noteObjectifs;
        this.noteCompetences = noteCompetences;
        this.noteComportement = noteComportement;
        this.noteContribution = noteContribution;
        this.noteDeveloppement = noteDeveloppement;
    }

    public Integer getIdPerformance() {
        return idPerformance;
    }

    public void setIdPerformance(Integer idPerformance) {
        this.idPerformance = idPerformance;
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

    public SourceEvaluation getSource() {
        return source;
    }

    public void setSource(SourceEvaluation source) {
        this.source = source;
    }

    public Manager getEvaluateur() {
        return evaluateur;
    }

    public void setEvaluateur(Manager evaluateur) {
        this.evaluateur = evaluateur;
    }

    public BigDecimal getNoteObjectifs() {
        return noteObjectifs;
    }

    public void setNoteObjectifs(BigDecimal noteObjectifs) {
        this.noteObjectifs = noteObjectifs;
    }

    public BigDecimal getNoteCompetences() {
        return noteCompetences;
    }

    public void setNoteCompetences(BigDecimal noteCompetences) {
        this.noteCompetences = noteCompetences;
    }

    public BigDecimal getNoteComportement() {
        return noteComportement;
    }

    public void setNoteComportement(BigDecimal noteComportement) {
        this.noteComportement = noteComportement;
    }

    public BigDecimal getNoteContribution() {
        return noteContribution;
    }

    public void setNoteContribution(BigDecimal noteContribution) {
        this.noteContribution = noteContribution;
    }

    public BigDecimal getNoteDeveloppement() {
        return noteDeveloppement;
    }

    public void setNoteDeveloppement(BigDecimal noteDeveloppement) {
        this.noteDeveloppement = noteDeveloppement;
    }
}
