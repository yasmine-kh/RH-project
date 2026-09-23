package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.PoidsSources;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.PointsVigilance;
import com.talent360bank.talent360bank.entity.SeuilsNeufBox;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.SeuilsTalent;
import com.talent360bank.talent360bank.entity.SeuilsVigilance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de mise a jour des reglages : les neuf blocs, et rien d'autre.
 *
 * <p>Ni l'identifiant ni le trimestre ne sont acceptes du client. Un jeu de
 * reglages appartient a son trimestre et n'en change pas : le trimestre vient
 * de l'URL, l'identifiant de la ligne existante.
 */
public record ParametreForm(@Size(max = 100) String libelle,
                            @Valid @NotNull PoidsSources poidsSources,
                            @Valid @NotNull PoidsPerformance poidsPerformance,
                            @Valid @NotNull PoidsPotentiel poidsPotentiel,
                            @Valid @NotNull PoidsSuccession poidsSuccession,
                            @Valid @NotNull SeuilsNeufBox seuilsNeufBox,
                            @Valid @NotNull SeuilsReadiness seuilsReadiness,
                            @Valid @NotNull SeuilsTalent seuilsTalent,
                            @Valid @NotNull PointsVigilance pointsVigilance,
                            @Valid @NotNull SeuilsVigilance seuilsVigilance) {

    /** Recopie les neuf blocs sur les reglages existants. */
    public void appliquerA(Parametre parametre) {
        parametre.setLibelle(libelle);
        parametre.setPoidsSources(poidsSources);
        parametre.setPoidsPerformance(poidsPerformance);
        parametre.setPoidsPotentiel(poidsPotentiel);
        parametre.setPoidsSuccession(poidsSuccession);
        parametre.setSeuilsNeufBox(seuilsNeufBox);
        parametre.setSeuilsReadiness(seuilsReadiness);
        parametre.setSeuilsTalent(seuilsTalent);
        parametre.setPointsVigilance(pointsVigilance);
        parametre.setSeuilsVigilance(seuilsVigilance);
    }
}
