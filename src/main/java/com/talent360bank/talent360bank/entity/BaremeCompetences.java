package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import java.math.BigDecimal;

/**
 * Bareme du critere competences du matching succession, tel que l'applique
 * 09_SUCCESSION : chaque niveau manquant par rapport a l'exigence du poste
 * retire un nombre fixe de points aux 100 de la competence. Une competence
 * exigee absente du profil du candidat est supposee au niveau par defaut,
 * comme le fait le classeur (IFERROR(..., 3)).
 *
 * <p>Comme pour {@link BaremeExperience}, les colonnes portent la valeur par
 * defaut en base pour les lignes existantes au moment de leur ajout.
 */
@Embeddable
public class BaremeCompetences {

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @ColumnDefault("20")
    @Column(name = "comp_points_par_niveau_manquant", precision = 5, scale = 2)
    private BigDecimal pointsParNiveauManquant;

    @NotNull
    @Min(0)
    @Max(5)
    @ColumnDefault("3")
    @Column(name = "comp_niveau_par_defaut")
    private Integer niveauParDefaut;

    public BaremeCompetences() {
    }

    public BaremeCompetences(BigDecimal pointsParNiveauManquant, Integer niveauParDefaut) {
        this.pointsParNiveauManquant = pointsParNiveauManquant;
        this.niveauParDefaut = niveauParDefaut;
    }

    public BigDecimal getPointsParNiveauManquant() {
        return pointsParNiveauManquant;
    }

    public void setPointsParNiveauManquant(BigDecimal pointsParNiveauManquant) {
        this.pointsParNiveauManquant = pointsParNiveauManquant;
    }

    public Integer getNiveauParDefaut() {
        return niveauParDefaut;
    }

    public void setNiveauParDefaut(Integer niveauParDefaut) {
        this.niveauParDefaut = niveauParDefaut;
    }
}
