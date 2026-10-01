package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;

/**
 * Seuil du statut de gap de competence, tel que le fixe 00_PARAMETRES
 * ligne 77 et que l'applique 06_EMPLOYEE_SKILLS colonne H : a partir de ce
 * nombre de niveaux manquants, la competence est Prioritaire.
 *
 * <p>Borne a 2..4 : un gap de 1 est toujours A developper dans le classeur,
 * un seuil de 1 n'aurait donc aucun effet ; les niveaux allant de 1 a 5, un
 * gap ne depasse pas 4.
 *
 * <p>Comme pour {@link BaremeExperience}, la colonne porte la valeur par
 * defaut en base pour les lignes existantes au moment de son ajout.
 */
@Embeddable
public class SeuilsGapCompetence {

    @NotNull
    @Min(2)
    @Max(4)
    @ColumnDefault("2")
    @Column(name = "comp_seuil_gap_prioritaire")
    private Integer seuilPrioritaire;

    public SeuilsGapCompetence() {
    }

    public SeuilsGapCompetence(Integer seuilPrioritaire) {
        this.seuilPrioritaire = seuilPrioritaire;
    }

    public Integer getSeuilPrioritaire() {
        return seuilPrioritaire;
    }

    public void setSeuilPrioritaire(Integer seuilPrioritaire) {
        this.seuilPrioritaire = seuilPrioritaire;
    }
}
