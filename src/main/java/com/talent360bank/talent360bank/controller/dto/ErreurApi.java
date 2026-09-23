package com.talent360bank.talent360bank.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Corps d'erreur unique de l'API, pour que l'UI n'ait qu'une forme a lire. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErreurApi(int statut, String erreur, String message, List<String> details) {

    public static ErreurApi de(int statut, String erreur, String message) {
        return new ErreurApi(statut, erreur, message, List.of());
    }
}
