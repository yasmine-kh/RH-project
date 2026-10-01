package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;

/**
 * Bilan du recalcul lance apres un changement de reglages.
 *
 * @param recalcule              vrai si les trois etapes (scores, 9-box, vivier de releve) ont abouti
 * @param nbCollaborateursScores collaborateurs dont le score a ete recalcule ; null si echec
 * @param nbPlaces9Box           collaborateurs places dans la matrice 9-box ; null si echec
 * @param dureeMs                duree du recalcul, en millisecondes
 * @param erreur                 message lisible si le recalcul a echoue, sinon null ; les
 *                               reglages sont alors bien enregistres
 */
public record RecalculReglagesResponse(boolean recalcule, Integer nbCollaborateursScores, Integer nbPlaces9Box,
                                       long dureeMs, String erreur) {

    public static RecalculReglagesResponse reussi(ResultatCalculTrimestre resultat, long dureeMs) {
        return new RecalculReglagesResponse(true, resultat.scores().nombreCalcules(),
                resultat.placements().nombreCalcules(), dureeMs, null);
    }

    public static RecalculReglagesResponse echoue(String erreur, long dureeMs) {
        return new RecalculReglagesResponse(false, null, null, dureeMs, erreur);
    }
}
