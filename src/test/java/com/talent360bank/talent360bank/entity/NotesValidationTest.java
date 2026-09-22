package com.talent360bank.talent360bank.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NotesValidationTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Employe employe() {
        Employe employe = new Employe();
        employe.setMatricule("E001");
        employe.setNom("Bennani");
        employe.setPrenom("Sara");
        employe.setDateEntree(java.time.LocalDate.of(2020, 1, 15));
        return employe;
    }

    private Trimestre trimestre() {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        return trimestre;
    }

    private Performance performanceValide() {
        return new Performance(employe(), trimestre(), new BigDecimal("80"),
                new BigDecimal("75"), new BigDecimal("90"),
                new BigDecimal("70"), new BigDecimal("60"));
    }

    private Potentiel potentielValide() {
        return new Potentiel(employe(), trimestre(), new BigDecimal("80"),
                new BigDecimal("75"), new BigDecimal("90"), new BigDecimal("70"),
                new BigDecimal("60"), new BigDecimal("85"), new BigDecimal("65"));
    }

    @Test
    void unePerformanceCompleteEstValide() {
        assertThat(validator.validate(performanceValide())).isEmpty();
    }

    @Test
    void unPotentielCompletEstValide() {
        assertThat(validator.validate(potentielValide())).isEmpty();
    }

    @Test
    void uneNoteHorsBorneEstRejetee() {
        Performance performance = performanceValide();
        performance.setNoteObjectifs(new BigDecimal("120"));

        assertThat(validator.validate(performance))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("noteObjectifs");
    }

    @Test
    void uneNoteNegativeEstRejetee() {
        Potentiel potentiel = potentielValide();
        potentiel.setNoteLearning(new BigDecimal("-1"));

        assertThat(validator.validate(potentiel))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("noteLearning");
    }

    @Test
    void uneNoteManquanteEstRejetee() {
        Performance performance = performanceValide();
        performance.setNoteDeveloppement(null);

        assertThat(validator.validate(performance))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("noteDeveloppement");
    }
}
