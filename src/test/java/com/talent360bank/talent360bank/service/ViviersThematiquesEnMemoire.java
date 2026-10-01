package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.service.enums.VivierThematique;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Doublure de {@link VivierThematiqueSource} pour les tests, en attendant le
 * referentiel direction / vivier et son import.
 */
public class ViviersThematiquesEnMemoire implements VivierThematiqueSource {

    private final Map<String, VivierThematique> vivierParDirection = new HashMap<>();

    public ViviersThematiquesEnMemoire rattacher(VivierThematique vivier, String... directions) {
        for (String direction : directions) {
            vivierParDirection.put(direction, vivier);
        }
        return this;
    }

    @Override
    public Optional<VivierThematique> vivierPourDirection(String direction) {
        return Optional.ofNullable(vivierParDirection.get(direction));
    }
}
