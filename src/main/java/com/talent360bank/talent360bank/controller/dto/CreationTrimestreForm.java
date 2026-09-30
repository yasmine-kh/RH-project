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
        @NotNull(message = "L'annee est obligatoire")
        @Min(value = 2000, message = "L'annee doit etre entre 2000 et 2100")
        @Max(value = 2100, message = "L'annee doit etre entre 2000 et 2100")
        Integer annee,

        @NotNull(message = "Le numero est obligatoire")
        @Min(value = 1, message = "Le numero doit etre entre 1 et 4")
        @Max(value = 4, message = "Le numero doit etre entre 1 et 4")
        Integer numero,

        LocalDate dateReference) {
}
