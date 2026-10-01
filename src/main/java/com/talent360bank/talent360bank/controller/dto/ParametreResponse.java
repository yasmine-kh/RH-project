package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.BaremeCompetences;
import com.talent360bank.talent360bank.entity.BaremeExperience;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.PointsVigilance;
import com.talent360bank.talent360bank.entity.PonderationSources;
import com.talent360bank.talent360bank.entity.SeuilsAutoEvaluation;
import com.talent360bank.talent360bank.entity.SeuilsCategoriePerformance;
import com.talent360bank.talent360bank.entity.SeuilsCouverture;
import com.talent360bank.talent360bank.entity.SeuilsGapCompetence;
import com.talent360bank.talent360bank.entity.SeuilsNeufBox;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.SeuilsTalent;
import com.talent360bank.talent360bank.entity.SeuilsVigilance;

/**
 * Reglages d'un trimestre. Le trimestre est aplati en (annee, numero) : la
 * relation etant LAZY, serialiser l'entite echouerait hors transaction.
 */
public record ParametreResponse(Integer idParametre, Integer annee, Integer numero, String libelle,
                                PoidsPerformance poidsPerformance,
                                PoidsPotentiel poidsPotentiel,
                                PoidsSuccession poidsSuccession,
                                PonderationSources ponderationSources,
                                SeuilsAutoEvaluation seuilsAutoEvaluation,
                                BaremeExperience baremeExperience,
                                BaremeCompetences baremeCompetences,
                                SeuilsNeufBox seuilsNeufBox,
                                SeuilsNeufBox seuilsNeufBoxPotentiel,
                                SeuilsCategoriePerformance seuilsCategoriePerformance,
                                SeuilsGapCompetence seuilsGapCompetence,
                                SeuilsReadiness seuilsReadiness,
                                SeuilsCouverture seuilsCouverture,
                                SeuilsTalent seuilsTalent,
                                PointsVigilance pointsVigilance,
                                SeuilsVigilance seuilsVigilance) {

    public static ParametreResponse de(Parametre parametre) {
        return new ParametreResponse(
                parametre.getIdParametre(),
                parametre.getTrimestre().getAnnee(),
                parametre.getTrimestre().getNumero(),
                parametre.getLibelle(),
                parametre.getPoidsPerformance(),
                parametre.getPoidsPotentiel(),
                parametre.getPoidsSuccession(),
                parametre.getPonderationSources(),
                parametre.getSeuilsAutoEvaluation(),
                parametre.getBaremeExperience(),
                parametre.getBaremeCompetences(),
                parametre.getSeuilsNeufBox(),
                parametre.getSeuilsNeufBoxPotentiel(),
                parametre.getSeuilsCategoriePerformance(),
                parametre.getSeuilsGapCompetence(),
                parametre.getSeuilsReadiness(),
                parametre.getSeuilsCouverture(),
                parametre.getSeuilsTalent(),
                parametre.getPointsVigilance(),
                parametre.getSeuilsVigilance());
    }
}
