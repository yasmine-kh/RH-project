package com.talent360bank.talent360bank.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Corps de PUT /api/trimestres/{annee}/{numero}. */
public record ModificationTrimestreForm(
        @NotNull(message = "La date de référence est obligatoire (aaaa-mm-jj)")
        LocalDate dateReference) {
}
