package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tous les viviers thematiques du trimestre, chacun present meme vide, et les
 * collaborateurs dont la direction n'est rattachee a aucun vivier.
 */
public record ViviersThematiquesResponse(List<VivierThematiqueResponse> viviers,
                                         List<CollaborateurResume> nonClasses) {

    public static ViviersThematiquesResponse de(ResultatViviersThematiques resultat,
                                                LocalDate dateReference) {
        List<VivierThematiqueResponse> viviers = new ArrayList<>();
        for (VivierThematique vivier : VivierThematique.values()) {
            viviers.add(VivierThematiqueResponse.de(vivier, resultat.membresDe(vivier)));
        }
        return new ViviersThematiquesResponse(List.copyOf(viviers),
                resultat.nonClasses().stream()
                        .map(collaborateur -> CollaborateurResume.de(collaborateur, dateReference)).toList());
    }
}
