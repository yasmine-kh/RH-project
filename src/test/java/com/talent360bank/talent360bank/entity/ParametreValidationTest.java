package com.talent360bank.talent360bank.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ParametreValidationTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Parametre parametreValide() {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        return Parametre.parDefaut(trimestre);
    }

    @Test
    void lesReglagesParDefautSontValides() {
        assertThat(validator.validate(parametreValide())).isEmpty();
    }

    @Test
    void unBlocDePoidsQuiNeFaitPas100EstRejete() {
        Parametre parametre = parametreValide();
        parametre.getPoidsPerformance().setPoidsObjectifs(new BigDecimal("50"));

        Set<ConstraintViolation<Parametre>> violations = validator.validate(parametre);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("poidsPerformance.sommeValide");
    }

    @Test
    void desSeuils9BoxInversesSontRejetes() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsNeufBox().setSeuilEleve(new BigDecimal("60"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsNeufBox.ordreValide");
    }

    @Test
    void unPlafondDExperienceAuDelaDeCentEstRejete() {
        Parametre parametre = parametreValide();
        parametre.getBaremeExperience().setPlafond(new BigDecimal("120"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("baremeExperience.plafond");
    }

    @Test
    void desPointsParNiveauManquantNegatifsSontRejetes() {
        Parametre parametre = parametreValide();
        parametre.getBaremeCompetences().setPointsParNiveauManquant(new BigDecimal("-5"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("baremeCompetences.pointsParNiveauManquant");
    }

    @Test
    void unSeuilDeVigilanceInatteignableEstRejete() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsVigilance().setSeuilEleve(new BigDecimal("500"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilVigilanceAtteignable");
    }
}
