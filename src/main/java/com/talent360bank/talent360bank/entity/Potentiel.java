package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Notes brutes des 7 criteres de potentiel d'un employe sur un trimestre.
 * Le score pondere qui en decoule est calcule par le service et stocke
 * dans {@link Score} : cette entite ne porte que la saisie.
 */
@Entity
@Table(name = "potentiel", uniqueConstraints = @UniqueConstraint(
        name = "uk_potentiel_employe_trimestre",
        columnNames = {"id_employe", "id_trimestre"}))
public class Potentiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPotentiel;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employe", nullable = false)
    private Employe employe;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_learning", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteLearning;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_leadership", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteLeadership;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_adaptabilite", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteAdaptabilite;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_complexite", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteComplexite;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_mobilite", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteMobilite;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_strategie", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteStrategie;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "note_autonomie", nullable = false, precision = 5, scale = 2)
    private BigDecimal noteAutonomie;

    public Potentiel() {
    }

    public Potentiel(Employe employe, Trimestre trimestre, BigDecimal noteLearning,
                     BigDecimal noteLeadership, BigDecimal noteAdaptabilite,
                     BigDecimal noteComplexite, BigDecimal noteMobilite,
                     BigDecimal noteStrategie, BigDecimal noteAutonomie) {
        this.employe = employe;
        this.trimestre = trimestre;
        this.noteLearning = noteLearning;
        this.noteLeadership = noteLeadership;
        this.noteAdaptabilite = noteAdaptabilite;
        this.noteComplexite = noteComplexite;
        this.noteMobilite = noteMobilite;
        this.noteStrategie = noteStrategie;
        this.noteAutonomie = noteAutonomie;
    }

    public Integer getIdPotentiel() {
        return idPotentiel;
    }

    public void setIdPotentiel(Integer idPotentiel) {
        this.idPotentiel = idPotentiel;
    }

    public Employe getEmploye() {
        return employe;
    }

    public void setEmploye(Employe employe) {
        this.employe = employe;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }

    public BigDecimal getNoteLearning() {
        return noteLearning;
    }

    public void setNoteLearning(BigDecimal noteLearning) {
        this.noteLearning = noteLearning;
    }

    public BigDecimal getNoteLeadership() {
        return noteLeadership;
    }

    public void setNoteLeadership(BigDecimal noteLeadership) {
        this.noteLeadership = noteLeadership;
    }

    public BigDecimal getNoteAdaptabilite() {
        return noteAdaptabilite;
    }

    public void setNoteAdaptabilite(BigDecimal noteAdaptabilite) {
        this.noteAdaptabilite = noteAdaptabilite;
    }

    public BigDecimal getNoteComplexite() {
        return noteComplexite;
    }

    public void setNoteComplexite(BigDecimal noteComplexite) {
        this.noteComplexite = noteComplexite;
    }

    public BigDecimal getNoteMobilite() {
        return noteMobilite;
    }

    public void setNoteMobilite(BigDecimal noteMobilite) {
        this.noteMobilite = noteMobilite;
    }

    public BigDecimal getNoteStrategie() {
        return noteStrategie;
    }

    public void setNoteStrategie(BigDecimal noteStrategie) {
        this.noteStrategie = noteStrategie;
    }

    public BigDecimal getNoteAutonomie() {
        return noteAutonomie;
    }

    public void setNoteAutonomie(BigDecimal noteAutonomie) {
        this.noteAutonomie = noteAutonomie;
    }
}
