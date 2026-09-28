package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;

    private ScoreService scoreService;

    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        scoreService = new ScoreService(
                scoreRepository, calculService, performanceRepository, potentielRepository);

        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        parametre = Parametre.parDefaut(trimestre);
    }

    private Employe employe(String matricule, StatutEmploye statut) {
        Employe employe = new Employe();
        employe.setEmployeeId(matricule);
        employe.setNom("Nom" + matricule);
        employe.setPrenom("Prenom" + matricule);
        employe.setDateEntree(LocalDate.of(2020, 1, 15));
        employe.setStatut(statut);
        return employe;
    }

    private Performance performance(Employe employe) {
        return new Performance(employe, trimestre,
                new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("70"),
                new BigDecimal("60"), new BigDecimal("50"));
    }

    private Potentiel potentiel(Employe employe) {
        return new Potentiel(employe, trimestre,
                new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("70"),
                new BigDecimal("60"), new BigDecimal("50"), new BigDecimal("40"),
                new BigDecimal("30"));
    }

    private void renvoieCeQuOnLuiDonne() {
        when(scoreRepository.save(any(Score.class))).thenAnswer(appel -> appel.getArgument(0));
    }

    @Test
    void enregistreLesDeuxScoresEtLaDateDuJour() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(employe)));
        when(potentielRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(employe)));
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.calculerEtEnregistrer(employe, trimestre);

        assertThat(score.getScorePerformance()).isEqualByComparingTo("77");
        assertThat(score.getScorePotentiel()).isEqualByComparingTo("65.50");
        assertThat(score.getDateCalcul()).isEqualTo(LocalDate.now());
        assertThat(score.getEmploye()).isEqualTo(employe);
        assertThat(score.getTrimestre()).isEqualTo(trimestre);
    }

    @Test
    void laPositionBoxResteIntacteCarElleRelevedeNeufBoxService() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(employe)));
        when(potentielRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(employe)));
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        assertThat(scoreService.calculerEtEnregistrer(employe, trimestre).getPositionBox()).isNull();
    }

    @Test
    void unRecalculMetAJourLaLigneExistanteSansEnCreerUneSeconde() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        Score existant = new Score();
        existant.setIdScore(42);
        existant.setEmploye(employe);
        existant.setTrimestre(trimestre);
        existant.setScorePerformance(new BigDecimal("10.00"));

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(employe)));
        when(potentielRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(employe)));
        when(scoreRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(existant));
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.calculerEtEnregistrer(employe, trimestre);

        assertThat(score.getIdScore()).isEqualTo(42);
        assertThat(score.getScorePerformance()).isEqualByComparingTo("77");
    }

    @Test
    void desNotesAbsentesEmpechentLEnregistrement() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scoreService.calculerEtEnregistrer(employe, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void leRecalculDeTrimestreScoreLesEmployesComplets() {
        Employe premier = employe("E001", StatutEmploye.ACTIF);
        Employe second = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(performance(premier), performance(second)));
        when(potentielRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(potentiel(premier), potentiel(second)));
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(2);
        assertThat(resultat.nombreIgnores()).isZero();
    }

    @Test
    void unEmployeSansNotesDePotentielEstIgnoreAvecSonMotif() {
        Employe complet = employe("E001", StatutEmploye.ACTIF);
        Employe incomplet = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(performance(complet), performance(incomplet)));
        when(potentielRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(potentiel(complet)));
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(1);
        assertThat(resultat.ignores())
                .extracting(ResultatRecalcul.EmployeIgnore::matricule, ResultatRecalcul.EmployeIgnore::motif)
                .containsExactly(org.assertj.core.api.Assertions.tuple("E002", "Notes de potentiel absentes"));
    }

    @Test
    void unEmployeSansNotesDePerformanceEstIgnoreAvecSonMotif() {
        Employe complet = employe("E001", StatutEmploye.ACTIF);
        Employe sansPerf = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(performance(complet)));
        when(potentielRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(potentiel(complet), potentiel(sansPerf)));
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(1);
        assertThat(resultat.ignores())
                .extracting(ResultatRecalcul.EmployeIgnore::matricule, ResultatRecalcul.EmployeIgnore::motif)
                .containsExactly(org.assertj.core.api.Assertions.tuple("E002", "Notes de performance absentes"));
    }

    @Test
    void unEmployeArchiveEstEcarteDuRecalcul() {
        Employe archive = employe("E003", StatutEmploye.ARCHIVE);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(performance(archive)));
        when(potentielRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(potentiel(archive)));

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isZero();
        assertThat(resultat.ignores()).singleElement()
                .extracting(ResultatRecalcul.EmployeIgnore::motif)
                .asString().contains("ARCHIVE");
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void unTrimestreSansParametreArreteLeRecalculAvantToutEcriture() {
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scoreService.recalculerTrimestre(trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Aucun parametre");

        verify(scoreRepository, never()).save(any());
    }
}
