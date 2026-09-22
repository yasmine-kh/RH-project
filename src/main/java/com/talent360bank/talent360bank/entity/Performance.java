package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Notes brutes des 5 criteres de performance d'un employe sur un trimestre.
 * Le score pondere qui en decoule est calcule par le service et stocke
 * dans {@link Score} : cette entite ne porte que la saisie.
 */
@Entity
@Table(name = "performance", uniqueConstraints = @UniqueConstraint(
        name = "uk_performance_employe_trimestre",
        columnNames = {"id_employe", "id_trimestre"}))
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPerformance;

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

    public Performance(Employe employe, Trimestre trimestre, BigDecimal noteObjectifs,
                       BigDecimal noteCompetences, BigDecimal noteComportement,
                       BigDecimal noteContribution, BigDecimal noteDeveloppement) {
        this.employe = employe;
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
