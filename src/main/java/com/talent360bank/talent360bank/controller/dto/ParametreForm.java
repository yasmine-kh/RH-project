package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.BaremeCompetences;
import com.talent360bank.talent360bank.entity.BaremeExperience;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.PoidsSources;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.PointsVigilance;
import com.talent360bank.talent360bank.entity.SeuilsCouverture;
import com.talent360bank.talent360bank.entity.SeuilsNeufBox;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.SeuilsTalent;
import com.talent360bank.talent360bank.entity.SeuilsVigilance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de mise a jour des reglages : les douze blocs, et rien d'autre.
 *
 * <p>Ni l'identifiant ni le trimestre ne sont acceptes du client. Un jeu de
 * reglages appartient a son trimestre et n'en change pas : le trimestre vient
 * de l'URL, l'identifiant de la ligne existante.
 *
 * <p>{@code seuilsCouverture} est facultatif : ajoute apres les autres blocs,
 * il ne doit pas casser un client qui ne le connait pas encore. Absent, la
 * valeur en place est conservee.
 */
public record ParametreForm(@Size(max = 100) String libelle,
                            @Valid @NotNull PoidsSources poidsSources,
                            @Valid @NotNull PoidsPerformance poidsPerformance,
                            @Valid @NotNull PoidsPotentiel poidsPotentiel,
                            @Valid @NotNull PoidsSuccession poidsSuccession,
                            @Valid @NotNull BaremeExperience baremeExperience,
                            @Valid @NotNull BaremeCompetences baremeCompetences,
                            @Valid @NotNull SeuilsNeufBox seuilsNeufBox,
                            @Valid @NotNull SeuilsReadiness seuilsReadiness,
                            @Valid SeuilsCouverture seuilsCouverture,
                            @Valid @NotNull SeuilsTalent seuilsTalent,
                            @Valid @NotNull PointsVigilance pointsVigilance,
                            @Valid @NotNull SeuilsVigilance seuilsVigilance) {

    /** Recopie les blocs sur les reglages existants ; un seuil de couverture absent garde sa valeur. */
    public void appliquerA(Parametre parametre) {
        parametre.setLibelle(libelle);
        parametre.setPoidsSources(poidsSources);
        parametre.setPoidsPerformance(poidsPerformance);
        parametre.setPoidsPotentiel(poidsPotentiel);
        parametre.setPoidsSuccession(poidsSuccession);
        parametre.setBaremeExperience(baremeExperience);
        parametre.setBaremeCompetences(baremeCompetences);
        parametre.setSeuilsNeufBox(seuilsNeufBox);
        parametre.setSeuilsReadiness(seuilsReadiness);
        if (seuilsCouverture != null) {
            parametre.setSeuilsCouverture(seuilsCouverture);
        }
        parametre.setSeuilsTalent(seuilsTalent);
        parametre.setPointsVigilance(pointsVigilance);
        parametre.setSeuilsVigilance(seuilsVigilance);
    }
}
