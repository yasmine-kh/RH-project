package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;

import java.util.List;

/**
 * Bilan d'un recalcul. Les collaborateurs ecartes sont rendus avec leur motif :
 * c'est la moitie utile de la reponse, le RH doit savoir qui n'a pas ete
 * traite et pourquoi.
 */
public record RecalculResponse(int nombreCalcules, int nombreIgnores,
                               List<ScoreResume> scores, List<CollaborateurIgnoreResponse> ignores) {

    public record CollaborateurIgnoreResponse(String idCollaborateur, String motif) {
    }

    public static RecalculResponse de(ResultatRecalcul resultat) {
        return new RecalculResponse(
                resultat.nombreCalcules(),
                resultat.nombreIgnores(),
                resultat.scoresEnregistres().stream().map(ScoreResume::de).toList(),
                resultat.ignores().stream()
                        .map(ignore -> new CollaborateurIgnoreResponse(ignore.matricule(), ignore.motif()))
                        .toList());
    }
}
