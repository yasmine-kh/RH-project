package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "score", uniqueConstraints = @UniqueConstraint(
        name = "uk_score_collaborateur_trimestre",
        columnNames = {"id_collaborateur", "id_trimestre"}))
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
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    /**
     * Entite du collaborateur au moment du calcul. L'import ecrase l'entite
     * du collaborateur a chaque campagne : les ecrans d'un trimestre lisent
     * celle-ci pour qu'un ancien trimestre garde sa direction d'origine.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_entite")
    private Entite entite;

    /** Manager du collaborateur au moment du calcul, meme raison que {@link #entite}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_manager")
    private Manager manager;

    public Score() {
    }

    /** Fige l'entite et le manager actuels du collaborateur sur ce score. */
    public void figerOrganisation() {
        this.entite = collaborateur == null ? null : collaborateur.getEntite();
        this.manager = collaborateur == null ? null : collaborateur.getManager();
    }

    /** Direction du collaborateur au moment du calcul, null si inconnue. */
    @Transient
    public String getDirection() {
        return entite == null ? null : entite.libelleDe(TypeEntite.DIRECTION);
    }

    /** Departement du collaborateur au moment du calcul, null si inconnu. */
    @Transient
    public String getDepartement() {
        return entite == null ? null : entite.libelleDe(TypeEntite.DEPARTEMENT);
    }

    public Entite getEntite() {
        return entite;
    }

    public void setEntite(Entite entite) {
        this.entite = entite;
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
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
}