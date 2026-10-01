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

    private final Map<String, StatutValidationComite> statutParCollaborateur = new HashMap<>();

    public ValidationsComiteEnMemoire decider(String idCollaborateur, StatutValidationComite statut) {
        statutParCollaborateur.put(idCollaborateur, statut);
        return this;
    }

    @Override
    public StatutValidationComite statut(String idCollaborateur, Trimestre trimestre) {
        return statutParCollaborateur.getOrDefault(idCollaborateur, StatutValidationComite.EN_ATTENTE);
    }
}
