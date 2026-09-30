package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Faits de vigilance importes, tels que le RH les a saisis (12_VIGILANCE,
 * colonnes F a K : mobilite, developpement, performance, reconnaissance,
 * formation).
 *
 * <p>Une saisie, pas un calcul : le moteur additionne les points et classe,
 * l'import dit quels signaux sont presents. Contrat cote moteur, sans
 * dependance a la couche de persistance : le depot de la future entite de
 * l'import l'implemente ou s'y adapte. Tant qu'aucune implementation n'est
 * declaree, {@link VigilanceService} ne leve aucun de ces signaux.
 */
public interface FaitsVigilanceSource {

    /**
     * Faits du trimestre indexes par Employee_ID, jamais null. Un collaborateur
     * absent de la map n'a aucun fait connu.
     */
    Map<String, FaitsVigilance> faitsDuTrimestre(Trimestre trimestre);

    /**
     * Faits d'un lot de collaborateurs (une equipe) sur le trimestre, indexes par
     * Employee_ID, jamais null. Par defaut, filtre les faits du trimestre ; une
     * implementation en base peut ne lire que ceux du lot.
     */
    default Map<String, FaitsVigilance> faitsDe(Collection<String> idsCollaborateurs, Trimestre trimestre) {
        Map<String, FaitsVigilance> faits = faitsDuTrimestre(trimestre);
        Map<String, FaitsVigilance> retenus = new HashMap<>();
        if (faits != null) {
            for (String id : idsCollaborateurs) {
                FaitsVigilance fait = faits.get(id);
                if (fait != null) {
                    retenus.put(id, fait);
                }
            }
        }
        return retenus;
    }

    /** Faits d'un collaborateur sur le trimestre, {@link FaitsVigilance#AUCUN} si inconnus. */
    default FaitsVigilance faits(String idCollaborateur, Trimestre trimestre) {
        Map<String, FaitsVigilance> faits = faitsDuTrimestre(trimestre);
        return faits == null ? FaitsVigilance.AUCUN : faits.getOrDefault(idCollaborateur, FaitsVigilance.AUCUN);
    }
}
