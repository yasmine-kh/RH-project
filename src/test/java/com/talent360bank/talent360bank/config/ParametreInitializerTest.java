package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParametreInitializerTest {

    @Mock
    private ParametreRepository parametreRepository;

    private ParametreInitializer initializer;
    private Trimestre trimestre;

    @BeforeEach
    void init() {
        initializer = new ParametreInitializer(parametreRepository);
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
    }

    @Test
    void desBlocsAbsentsRecoiventLesValeursParDefaut() {
        // Ce que Hibernate rend pour une ligne anterieure aux blocs : les blocs a null.
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.setBaremeExperience(null);
        ancien.setBaremeCompetences(null);
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getBaremeExperience().getPointsParAnnee()).isEqualByComparingTo("8");
        assertThat(ancien.getBaremeExperience().getPlafond()).isEqualByComparingTo("100");
        assertThat(ancien.getBaremeCompetences().getPointsParNiveauManquant()).isEqualByComparingTo("20");
        assertThat(ancien.getBaremeCompetences().getNiveauParDefaut()).isEqualTo(3);
    }

    @Test
    void seulsLesChampsManquantsSontCompletes() {
        // Ligne anterieure au seul niveau par defaut, points deja ajustes par le RH.
        Parametre partiel = Parametre.parDefaut(trimestre);
        partiel.getBaremeCompetences().setPointsParNiveauManquant(new BigDecimal("25"));
        partiel.getBaremeCompetences().setNiveauParDefaut(null);
        partiel.getBaremeExperience().setPlafond(null);
        partiel.getBaremeExperience().setPointsParAnnee(new BigDecimal("10"));
        when(parametreRepository.findAll()).thenReturn(List.of(partiel));

        initializer.run(null);

        verify(parametreRepository).save(partiel);
        assertThat(partiel.getBaremeCompetences().getPointsParNiveauManquant()).isEqualByComparingTo("25");
        assertThat(partiel.getBaremeCompetences().getNiveauParDefaut()).isEqualTo(3);
        assertThat(partiel.getBaremeExperience().getPointsParAnnee()).isEqualByComparingTo("10");
        assertThat(partiel.getBaremeExperience().getPlafond()).isEqualByComparingTo("100");
    }

    @Test
    void desSeuilsDeHautPotentielAbsentsRecoiventLesValeursDuClasseur() {
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.getSeuilsTalent().setSeuilHautPotentielPotentiel(null);
        ancien.getSeuilsTalent().setSeuilHautPotentielPerformance(null);
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getSeuilsTalent().getSeuilHautPotentielPotentiel()).isEqualByComparingTo("85");
        assertThat(ancien.getSeuilsTalent().getSeuilHautPotentielPerformance()).isEqualByComparingTo("75");
        assertThat(ancien.getSeuilsTalent().getSeuilPerformance()).isEqualByComparingTo("85");
    }

    @Test
    void unSeuilDeCouvertureAbsentRecoitLaValeurDuClasseur() {
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.setSeuilsCouverture(null);
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getSeuilsCouverture().getNbMinSuccesseurs()).isEqualTo(1);
    }

    @Test
    void unAxePotentielAbsentReprendLesSeuilsDeLAxePerformanceDeLaLigne() {
        // Trimestre dont le RH avait deplace les seuils communs aux deux axes.
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.getSeuilsNeufBox().setSeuilEleve(new BigDecimal("80"));
        ancien.getSeuilsNeufBox().setSeuilMoyen(new BigDecimal("65"));
        ancien.setSeuilsNeufBoxPotentiel(null);
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getSeuilsNeufBoxPotentiel().getSeuilEleve()).isEqualByComparingTo("80");
        assertThat(ancien.getSeuilsNeufBoxPotentiel().getSeuilMoyen()).isEqualByComparingTo("65");
        assertThat(ancien.getSeuilsNeufBoxPotentiel()).isNotSameAs(ancien.getSeuilsNeufBox());
    }

    @Test
    void unAxePotentielAZeroPoseParMySqlReprendLesSeuilsDeLAxePerformance() {
        // Colonnes ajoutees NOT NULL sans defaut : MySQL a mis 0 dans les lignes existantes.
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.getSeuilsNeufBox().setSeuilEleve(new BigDecimal("80"));
        ancien.getSeuilsNeufBox().setSeuilMoyen(new BigDecimal("65"));
        ancien.getSeuilsNeufBoxPotentiel().setSeuilEleve(BigDecimal.ZERO);
        ancien.getSeuilsNeufBoxPotentiel().setSeuilMoyen(new BigDecimal("0.00"));
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getSeuilsNeufBoxPotentiel().getSeuilEleve()).isEqualByComparingTo("80");
        assertThat(ancien.getSeuilsNeufBoxPotentiel().getSeuilMoyen()).isEqualByComparingTo("65");
    }

    @Test
    void unAxePotentielRegleParLeRhNestPasEcrase() {
        Parametre regle = Parametre.parDefaut(trimestre);
        regle.getSeuilsNeufBoxPotentiel().setSeuilEleve(new BigDecimal("80"));
        regle.getSeuilsNeufBoxPotentiel().setSeuilMoyen(new BigDecimal("0"));
        when(parametreRepository.findAll()).thenReturn(List.of(regle));

        initializer.run(null);

        verify(parametreRepository, never()).save(any());
        assertThat(regle.getSeuilsNeufBoxPotentiel().getSeuilEleve()).isEqualByComparingTo("80");
        assertThat(regle.getSeuilsNeufBoxPotentiel().getSeuilMoyen()).isEqualByComparingTo("0");
    }

    @Test
    void desCategoriesDePerformanceAbsentesRecoiventLesValeursDuClasseur() {
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.setSeuilsCategoriePerformance(null);
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getSeuilsCategoriePerformance().getSeuilExceptionnelle()).isEqualByComparingTo("90");
        assertThat(ancien.getSeuilsCategoriePerformance().getSeuilARenforcer()).isEqualByComparingTo("60");
    }

    @Test
    void unSeuilDeGapAbsentRecoitLaValeurDuClasseur() {
        Parametre ancien = Parametre.parDefaut(trimestre);
        ancien.setSeuilsGapCompetence(null);
        when(parametreRepository.findAll()).thenReturn(List.of(ancien));

        initializer.run(null);

        verify(parametreRepository).save(ancien);
        assertThat(ancien.getSeuilsGapCompetence().getSeuilPrioritaire()).isEqualTo(2);
    }

    @Test
    void unSeuilDeGapSaisiNestPasEcrase() {
        Parametre regle = Parametre.parDefaut(trimestre);
        regle.getSeuilsGapCompetence().setSeuilPrioritaire(3);
        when(parametreRepository.findAll()).thenReturn(List.of(regle));

        initializer.run(null);

        verify(parametreRepository, never()).save(any());
        assertThat(regle.getSeuilsGapCompetence().getSeuilPrioritaire()).isEqualTo(3);
    }

    @Test
    void unSeuilDeCouvertureSaisiNestPasEcrase() {
        Parametre regle = Parametre.parDefaut(trimestre);
        regle.getSeuilsCouverture().setNbMinSuccesseurs(2);
        when(parametreRepository.findAll()).thenReturn(List.of(regle));

        initializer.run(null);

        verify(parametreRepository, never()).save(any());
        assertThat(regle.getSeuilsCouverture().getNbMinSuccesseurs()).isEqualTo(2);
    }

    @Test
    void unZeroEstSignaleMaisJamaisRemplace() {
        // Un 0 peut etre un reglage voulu : l'initialiseur ne le touche pas.
        Parametre aZero = Parametre.parDefaut(trimestre);
        aZero.getBaremeExperience().setPlafond(BigDecimal.ZERO);
        when(parametreRepository.findAll()).thenReturn(List.of(aZero));

        initializer.run(null);

        verify(parametreRepository, never()).save(any());
        assertThat(aZero.getBaremeExperience().getPlafond()).isEqualByComparingTo("0");
    }

    @Test
    void desReglagesCompletsNeSontPasReenregistres() {
        when(parametreRepository.findAll()).thenReturn(List.of(Parametre.parDefaut(trimestre)));

        initializer.run(null);

        verify(parametreRepository, never()).save(any());
    }
}
