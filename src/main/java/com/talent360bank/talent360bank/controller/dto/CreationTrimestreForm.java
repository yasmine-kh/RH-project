package com.talent360bank.talent360bank.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Corps de POST /api/trimestres.
 *
 * @param dateReference facultative (aaaa-mm-jj) : date a laquelle le trimestre
 *                      est evalue ; par defaut, son dernier jour
 */
public record CreationTrimestreForm(
        @NotNull(message = "L'année est obligatoire")
        @Min(value = 2000, message = "L'année doit être entre 2000 et 2100")
        @Max(value = 2100, message = "L'année doit être entre 2000 et 2100")
        Integer annee,

        @NotNull(message = "Le numéro est obligatoire")
        @Min(value = 1, message = "Le numéro doit être entre 1 et 4")
        @Max(value = 4, message = "Le numéro doit être entre 1 et 4")
        Integer numero,

        LocalDate dateReference) {
}
