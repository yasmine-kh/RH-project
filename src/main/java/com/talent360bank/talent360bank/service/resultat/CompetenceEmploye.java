package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;

/**
 * Une competence d'un employe avec son gap et le statut calcule avec le seuil
 * du trimestre. Le statut importe (EmployeeSkill.statutGap) n'est pas repris.
 *
 * @param gap    niveau cible - niveau actuel, null si l'un des deux manque
 * @param statut null si le gap est inconnu
 */
public record CompetenceEmploye(EmployeeSkill competence, Integer gap, StatutGapCompetence statut) {
}
