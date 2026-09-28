package com.talent360bank.talent360bank.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doublure de {@link SuccesseurIdentifieSource} pour les tests, en attendant
 * l'entite SuccesseurIdentifie et son import.
 */
public class SuccesseursIdentifiesEnMemoire implements SuccesseurIdentifieSource {

    private final Map<String, List<String>> successeursParPoste = new HashMap<>();

    public SuccesseursIdentifiesEnMemoire identifier(String posteId, String... employeeIds) {
        List<String> successeurs = successeursParPoste.computeIfAbsent(posteId, cle -> new ArrayList<>());
        successeurs.addAll(List.of(employeeIds));
        return this;
    }

    @Override
    public List<String> successeursIdentifies(String posteId) {
        return List.copyOf(successeursParPoste.getOrDefault(posteId, List.of()));
    }
}
