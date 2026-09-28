package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.CompetenceEmploye;

/**
 * Une competence d'un employe, aplatie pour l'API. Le statut est celui du
 * moteur, calcule avec le seuil du trimestre demande.
 *
 * @param statutGap code (MAITRISE, A_DEVELOPPER, PRIORITAIRE), null si le gap est inconnu
 */
public record CompetenceEmployeResponse(String competenceId, String competence, String categorie,
                                        Integer niveauActuel, Integer niveauCible, Integer gap,
                                        String statutGap, String statutGapLibelle) {

    public static CompetenceEmployeResponse de(CompetenceEmploye resultat) {
        return new CompetenceEmployeResponse(
                resultat.competence().getCompetence().getCompetenceId(),
                resultat.competence().getCompetence().getNom(),
                resultat.competence().getCompetence().getCategorie(),
                resultat.competence().getNiveauActuel(),
                resultat.competence().getNiveauCible(),
                resultat.gap(),
                resultat.statut() == null ? null : resultat.statut().name(),
                resultat.statut() == null ? null : resultat.statut().getLibelle());
    }
}
