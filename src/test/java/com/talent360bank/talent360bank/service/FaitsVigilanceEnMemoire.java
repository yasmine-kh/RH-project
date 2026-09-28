package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;

import java.util.HashMap;
import java.util.Map;

/**
 * Doublure de {@link FaitsVigilanceSource} pour les tests, en attendant
 * l'entite de l'import et son depot. Un seul trimestre : les faits declares
 * valent pour tout trimestre demande.
 */
public class FaitsVigilanceEnMemoire implements FaitsVigilanceSource {

    private final Map<String, FaitsVigilance> faitsParEmploye = new HashMap<>();

    public FaitsVigilanceEnMemoire declarer(String employeeId, FaitsVigilance faits) {
        faitsParEmploye.put(employeeId, faits);
        return this;
    }

    @Override
    public Map<String, FaitsVigilance> faitsDuTrimestre(Trimestre trimestre) {
        return Map.copyOf(faitsParEmploye);
    }
}
