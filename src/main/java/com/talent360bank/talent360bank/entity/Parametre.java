package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Jeu de reglages du moteur de calcul, configurable par le RH.
 * Un seul Parametre par trimestre : rejouer un calcul d'une periode passee
 * revient a charger le Parametre de son trimestre.
 */
@Entity
@Table(name = "parametre", uniqueConstraints = @UniqueConstraint(
        name = "uk_parametre_trimestre", columnNames = "id_trimestre"))
public class Parametre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idParametre;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    @Size(max = 100)
    @Column(length = 100)
    private String libelle;

    @Valid
    @NotNull
    @Embedded
    private PoidsSources poidsSources;

    @Valid
    @NotNull
    @Embedded
    private PoidsPerformance poidsPerformance;

    @Valid
    @NotNull
    @Embedded
    private PoidsPotentiel poidsPotentiel;

    @Valid
    @NotNull
    @Embedded
    private PoidsSuccession poidsSuccession;

    @Valid
    @NotNull
    @Embedded
    private SeuilsNeufBox seuilsNeufBox;

    @Valid
    @NotNull
    @Embedded
    private SeuilsReadiness seuilsReadiness;

    @Valid
    @NotNull
    @Embedded
    private SeuilsTalent seuilsTalent;

    @Valid
    @NotNull
    @Embedded
    private PointsVigilance pointsVigilance;

    @Valid
    @NotNull
    @Embedded
    private SeuilsVigilance seuilsVigilance;

    public Parametre() {
    }

    /**
     * Jeu de reglages initial pour un trimestre, aux valeurs de la specification.
     * Les poids des sources d'evaluation sont volontairement repartis a parts
     * egales : la specification ne les definit pas, ils sont a arbitrer avec le RH.
     */
    public static Parametre parDefaut(Trimestre trimestre) {
        Parametre parametre = new Parametre();
        parametre.setTrimestre(trimestre);
        parametre.setLibelle("Reglages par defaut");
        parametre.setPoidsSources(new PoidsSources(
                new BigDecimal("25"), new BigDecimal("25"),
                new BigDecimal("25"), new BigDecimal("25")));
        parametre.setPoidsPerformance(new PoidsPerformance(
                new BigDecimal("40"), new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("10")));
        parametre.setPoidsPotentiel(new PoidsPotentiel(
                new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("15"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10")));
        parametre.setPoidsSuccession(new PoidsSuccession(
                new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("15"), new BigDecimal("10"), new BigDecimal("10")));
        parametre.setSeuilsNeufBox(new SeuilsNeufBox(
                new BigDecimal("85"), new BigDecimal("70")));
        parametre.setSeuilsReadiness(new SeuilsReadiness(
                new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("65")));
        parametre.setSeuilsTalent(new SeuilsTalent(
                new BigDecimal("85"), new BigDecimal("85")));
        parametre.setPointsVigilance(new PointsVigilance(
                new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("15"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("5")));
        parametre.setSeuilsVigilance(new SeuilsVigilance(
                new BigDecimal("30"), new BigDecimal("60")));
        return parametre;
    }

    /**
     * Controle inter-blocs : un seuil de vigilance plus haut que la somme des
     * points rendrait le niveau ELEVEE impossible a atteindre.
     */
    @Transient
    @AssertTrue(message = "Le seuil de vigilance elevee depasse le total des points attribuables")
    public boolean isSeuilVigilanceAtteignable() {
        if (pointsVigilance == null || seuilsVigilance == null) {
            return true;
        }
        BigDecimal maximum = pointsVigilance.getTotalMaximum();
        BigDecimal seuil = seuilsVigilance.getSeuilEleve();
        if (maximum == null || seuil == null) {
            return true;
        }
        return seuil.compareTo(maximum) <= 0;
    }

    public Integer getIdParametre() {
        return idParametre;
    }

    public void setIdParametre(Integer idParametre) {
        this.idParametre = idParametre;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public PoidsSources getPoidsSources() {
        return poidsSources;
    }

    public void setPoidsSources(PoidsSources poidsSources) {
        this.poidsSources = poidsSources;
    }

    public PoidsPerformance getPoidsPerformance() {
        return poidsPerformance;
    }

    public void setPoidsPerformance(PoidsPerformance poidsPerformance) {
        this.poidsPerformance = poidsPerformance;
    }

    public PoidsPotentiel getPoidsPotentiel() {
        return poidsPotentiel;
    }

    public void setPoidsPotentiel(PoidsPotentiel poidsPotentiel) {
        this.poidsPotentiel = poidsPotentiel;
    }

    public PoidsSuccession getPoidsSuccession() {
        return poidsSuccession;
    }

    public void setPoidsSuccession(PoidsSuccession poidsSuccession) {
        this.poidsSuccession = poidsSuccession;
    }

    public SeuilsNeufBox getSeuilsNeufBox() {
        return seuilsNeufBox;
    }

    public void setSeuilsNeufBox(SeuilsNeufBox seuilsNeufBox) {
        this.seuilsNeufBox = seuilsNeufBox;
    }

    public SeuilsReadiness getSeuilsReadiness() {
        return seuilsReadiness;
    }

    public void setSeuilsReadiness(SeuilsReadiness seuilsReadiness) {
        this.seuilsReadiness = seuilsReadiness;
    }

    public SeuilsTalent getSeuilsTalent() {
        return seuilsTalent;
    }

    public void setSeuilsTalent(SeuilsTalent seuilsTalent) {
        this.seuilsTalent = seuilsTalent;
    }

    public PointsVigilance getPointsVigilance() {
        return pointsVigilance;
    }

    public void setPointsVigilance(PointsVigilance pointsVigilance) {
        this.pointsVigilance = pointsVigilance;
    }

    public SeuilsVigilance getSeuilsVigilance() {
        return seuilsVigilance;
    }

    public void setSeuilsVigilance(SeuilsVigilance seuilsVigilance) {
        this.seuilsVigilance = seuilsVigilance;
    }
}
