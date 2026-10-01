package com.talent360bank.talent360bank.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void desSeuils9BoxDePotentielInversesSontRejetes() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsNeufBoxPotentiel().setSeuilMoyen(new BigDecimal("90"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsNeufBoxPotentiel.ordreValide")
                .doesNotContain("seuilsNeufBox.ordreValide");
    }

    @Test
    void desAxesAuxSeuilsDifferentsSontValides() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsNeufBoxPotentiel().setSeuilEleve(new BigDecimal("80"));
        parametre.getSeuilsNeufBoxPotentiel().setSeuilMoyen(new BigDecimal("60"));

        assertThat(validator.validate(parametre)).isEmpty();
    }

    @Test
    void desCategoriesDePerformanceNonDecroissantesSontRejetees() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsCategoriePerformance().setSeuilARenforcer(new BigDecimal("70"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsCategoriePerformance.ordreValide");
    }

    @Test
    void unSeuilDeCategorieAuDelaDeCentEstRejete() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsCategoriePerformance().setSeuilExceptionnelle(new BigDecimal("101"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsCategoriePerformance.seuilExceptionnelle");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void unSeuilDeGapHorsDe2A4EstRejete(int seuil) {
        Parametre parametre = parametreValide();
        parametre.getSeuilsGapCompetence().setSeuilPrioritaire(seuil);

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsGapCompetence.seuilPrioritaire");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void unSeuilDeGapDe2A4EstValide(int seuil) {
        Parametre parametre = parametreValide();
        parametre.getSeuilsGapCompetence().setSeuilPrioritaire(seuil);

        assertThat(validator.validate(parametre)).isEmpty();
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
    void unNiveauParDefautHorsEchelleEstRejete() {
        Parametre parametre = parametreValide();
        parametre.getBaremeCompetences().setNiveauParDefaut(6);

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("baremeCompetences.niveauParDefaut");
    }

    @Test
    void unMinimumDeSuccesseursNulEstRejete() {
        // A 0, aucun poste critique ne pourrait jamais etre en alerte.
        Parametre parametre = parametreValide();
        parametre.getSeuilsCouverture().setNbMinSuccesseurs(0);

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsCouverture.nbMinSuccesseurs");
    }

    @Test
    void unSeuilDeHautPotentielAuDelaDeCentEstRejete() {
        Parametre parametre = parametreValide();
        parametre.getSeuilsTalent().setSeuilHautPotentielPerformance(new BigDecimal("101"));

        assertThat(validator.validate(parametre))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("seuilsTalent.seuilHautPotentielPerformance");
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
