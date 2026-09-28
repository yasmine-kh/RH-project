package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;

import java.util.HashMap;
import java.util.Map;

/**
 * Doublure de {@link ValidationComiteSource} pour les tests, en attendant
 * l'entite ValidationComite et son import. Une decision vaut pour tous les
 * trimestres.
 */
public class ValidationsComiteEnMemoire implements ValidationComiteSource {

    private final Map<String, StatutValidationComite> statutParEmploye = new HashMap<>();

    public ValidationsComiteEnMemoire decider(String employeeId, StatutValidationComite statut) {
        statutParEmploye.put(employeeId, statut);
        return this;
    }

    @Override
    public StatutValidationComite statut(String employeeId, Trimestre trimestre) {
        return statutParEmploye.getOrDefault(employeeId, StatutValidationComite.EN_ATTENTE);
    }
}
