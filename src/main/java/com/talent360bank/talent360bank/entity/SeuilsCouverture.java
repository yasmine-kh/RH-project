package com.talent360bank.talent360bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;

/**
 * Seuil d'alerte de la couverture des postes critiques, tel que l'applique
 * 08_POSTES_CRITIQUES : un poste critique qui compte moins de successeurs
 * identifies que ce minimum est en alerte. Le classeur alerte a zero
 * successeur, soit un minimum de 1.
 *
 * <p>Les niveaux de couverture au-dessus du minimum reprennent les seuils de
 * {@link SeuilsReadiness}, comme le classeur (memes cellules B54 et B55).
 *
 * <p>Comme pour {@link BaremeCompetences}, la colonne porte la valeur par
 * defaut en base pour les lignes existantes au moment de son ajout.
 */
@Embeddable
public class SeuilsCouverture {

    @NotNull
    @Min(1)
    @Max(10)
    @ColumnDefault("1")
    @Column(name = "couv_nb_min_successeurs")
    private Integer nbMinSuccesseurs;

    public SeuilsCouverture() {
    }

    public SeuilsCouverture(Integer nbMinSuccesseurs) {
        this.nbMinSuccesseurs = nbMinSuccesseurs;
    }

    public Integer getNbMinSuccesseurs() {
        return nbMinSuccesseurs;
    }

    public void setNbMinSuccesseurs(Integer nbMinSuccesseurs) {
        this.nbMinSuccesseurs = nbMinSuccesseurs;
    }
}
