package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;

/**
 * Une competence d'un collaborateur avec son gap et le statut calcule avec le seuil
 * du trimestre. Le statut importe (CompetenceCollaborateur.statutGap) n'est pas repris.
 *
 * @param gap    niveau cible - niveau actuel, null si l'un des deux manque
 * @param statut null si le gap est inconnu
 */
public record GapCompetence(CompetenceCollaborateur competence, Integer gap, StatutGapCompetence statut) {
}
