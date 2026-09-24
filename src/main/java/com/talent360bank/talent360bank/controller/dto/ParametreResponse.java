package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.BaremeExperience;
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

/**
 * Reglages d'un trimestre. Le trimestre est aplati en (annee, numero) : la
 * relation etant LAZY, serialiser l'entite echouerait hors transaction.
 */
public record ParametreResponse(Integer idParametre, Integer annee, Integer numero, String libelle,
                                PoidsSources poidsSources,
                                PoidsPerformance poidsPerformance,
                                PoidsPotentiel poidsPotentiel,
                                PoidsSuccession poidsSuccession,
                                BaremeExperience baremeExperience,
                                SeuilsNeufBox seuilsNeufBox,
                                SeuilsReadiness seuilsReadiness,
                                SeuilsTalent seuilsTalent,
                                PointsVigilance pointsVigilance,
                                SeuilsVigilance seuilsVigilance) {

    public static ParametreResponse de(Parametre parametre) {
        return new ParametreResponse(
                parametre.getIdParametre(),
                parametre.getTrimestre().getAnnee(),
                parametre.getTrimestre().getNumero(),
                parametre.getLibelle(),
                parametre.getPoidsSources(),
                parametre.getPoidsPerformance(),
                parametre.getPoidsPotentiel(),
                parametre.getPoidsSuccession(),
                parametre.getBaremeExperience(),
                parametre.getSeuilsNeufBox(),
                parametre.getSeuilsReadiness(),
                parametre.getSeuilsTalent(),
                parametre.getPointsVigilance(),
                parametre.getSeuilsVigilance());
    }
}
