package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;

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
     * Faits du trimestre indexes par Employee_ID, jamais null. Un employe
     * absent de la map n'a aucun fait connu.
     */
    Map<String, FaitsVigilance> faitsDuTrimestre(Trimestre trimestre);

    /** Faits d'un employe sur le trimestre, {@link FaitsVigilance#AUCUN} si inconnus. */
    default FaitsVigilance faits(String employeeId, Trimestre trimestre) {
        Map<String, FaitsVigilance> faits = faitsDuTrimestre(trimestre);
        return faits == null ? FaitsVigilance.AUCUN : faits.getOrDefault(employeeId, FaitsVigilance.AUCUN);
    }
}
