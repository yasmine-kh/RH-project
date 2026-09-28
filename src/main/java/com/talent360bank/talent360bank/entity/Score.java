package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "score", uniqueConstraints = @UniqueConstraint(
        name = "uk_score_employe_trimestre",
        columnNames = {"id_employe", "id_trimestre"}))
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idScore;

    @Column(name = "score_performance")
    private BigDecimal scorePerformance;

    @Column(name = "score_potentiel")
    private BigDecimal scorePotentiel;

    @Column(name = "position_box")
    private String positionBox;

    /** Pose par le recalcul des scores, avec les seuils du trimestre. */
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_performance", length = 20)
    private CategoriePerformance categoriePerformance;

    /** Pose par le placement 9-box : c'est le niveau de l'axe potentiel. */
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_potentiel", length = 10)
    private CategoriePotentiel categoriePotentiel;

    @Column(name = "date_calcul")
    private LocalDate dateCalcul;

    @ManyToOne
    @JoinColumn(name = "id_employe", nullable = false)
    private Employe employe;

    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    public Score() {
    }

    public Integer getIdScore() {
        return idScore;
    }

    public void setIdScore(Integer idScore) {
        this.idScore = idScore;
    }

    public BigDecimal getScorePerformance() {
        return scorePerformance;
    }

    public void setScorePerformance(BigDecimal scorePerformance) {
        this.scorePerformance = scorePerformance;
    }

    public BigDecimal getScorePotentiel() {
        return scorePotentiel;
    }

    public void setScorePotentiel(BigDecimal scorePotentiel) {
        this.scorePotentiel = scorePotentiel;
    }

    public String getPositionBox() {
        return positionBox;
    }

    public void setPositionBox(String positionBox) {
        this.positionBox = positionBox;
    }

    public CategoriePerformance getCategoriePerformance() {
        return categoriePerformance;
    }

    public void setCategoriePerformance(CategoriePerformance categoriePerformance) {
        this.categoriePerformance = categoriePerformance;
    }

    public CategoriePotentiel getCategoriePotentiel() {
        return categoriePotentiel;
    }

    public void setCategoriePotentiel(CategoriePotentiel categoriePotentiel) {
        this.categoriePotentiel = categoriePotentiel;
    }

    public LocalDate getDateCalcul() {
        return dateCalcul;
    }

    public void setDateCalcul(LocalDate dateCalcul) {
        this.dateCalcul = dateCalcul;
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
}