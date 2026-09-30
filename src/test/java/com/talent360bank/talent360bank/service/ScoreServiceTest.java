package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
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

    private Collaborateur collaborateur(String matricule, StatutCollaborateur statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(matricule);
        collaborateur.setNom("Nom" + matricule);
        collaborateur.setPrenom("Prenom" + matricule);
        collaborateur.setDateEntree(LocalDate.of(2020, 1, 15));
        collaborateur.setStatut(statut);
        return collaborateur;
    }

    private Performance performance(Collaborateur collaborateur) {
        return new Performance(collaborateur, trimestre,
                new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("70"),
                new BigDecimal("60"), new BigDecimal("50"));
    }

    private Potentiel potentiel(Collaborateur collaborateur) {
        return new Potentiel(collaborateur, trimestre,
                new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("70"),
                new BigDecimal("60"), new BigDecimal("50"), new BigDecimal("40"),
                new BigDecimal("30"));
    }

    private void renvoieCeQuOnLuiDonne() {
        when(scoreRepository.save(any(Score.class))).thenAnswer(appel -> appel.getArgument(0));
    }

    @Test
    void enregistreLesDeuxScoresEtLaDateDuJour() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(collaborateur)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.calculerEtEnregistrer(collaborateur, trimestre);

        assertThat(score.getScorePerformance()).isEqualByComparingTo("77");
        assertThat(score.getScorePotentiel()).isEqualByComparingTo("65.50");
        assertThat(score.getDateCalcul()).isEqualTo(LocalDate.now());
        assertThat(score.getCollaborateur()).isEqualTo(collaborateur);
        assertThat(score.getTrimestre()).isEqualTo(trimestre);
    }

    @Test
    void laPositionBoxResteIntacteCarElleRelevedeNeufBoxService() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(collaborateur)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        assertThat(scoreService.calculerEtEnregistrer(collaborateur, trimestre).getPositionBox()).isNull();
    }

    @Test
    void leRecalculPoseLaCategorieDePerformanceMaisPasCelleDePotentiel() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(collaborateur)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.calculerEtEnregistrer(collaborateur, trimestre);

        // 77 : entre 70 et 80.
        assertThat(score.getCategoriePerformance()).isEqualTo(CategoriePerformance.SOLIDE);
        // Pose par le placement 9-box, qui porte les seuils de l'axe potentiel.
        assertThat(score.getCategoriePotentiel()).isNull();
    }

    @Test
    void desSeuilsDeCategorieModifiesChangentLaCategorieAuRecalcul() {
        parametre.getSeuilsCategoriePerformance().setSeuilSolide(new BigDecimal("78"));
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(collaborateur)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        assertThat(scoreService.calculerEtEnregistrer(collaborateur, trimestre).getCategoriePerformance())
                .isEqualTo(CategoriePerformance.A_RENFORCER);
    }

    @Test
    void unRecalculMetAJourLaLigneExistanteSansEnCreerUneSeconde() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        Score existant = new Score();
        existant.setIdScore(42);
        existant.setCollaborateur(collaborateur);
        existant.setTrimestre(trimestre);
        existant.setScorePerformance(new BigDecimal("10.00"));

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(potentiel(collaborateur)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(existant));
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.calculerEtEnregistrer(collaborateur, trimestre);

        assertThat(score.getIdScore()).isEqualTo(42);
        assertThat(score.getScorePerformance()).isEqualByComparingTo("77");
    }

    @Test
    void desNotesAbsentesEmpechentLEnregistrement() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scoreService.calculerEtEnregistrer(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void leRecalculDeTrimestreScoreLesCollaborateursComplets() {
        Collaborateur premier = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur second = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(performance(premier), performance(second)));
        when(potentielRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(potentiel(premier), potentiel(second)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(2);
        assertThat(resultat.nombreIgnores()).isZero();
    }

    @Test
    void unCollaborateurSansNotesDePotentielEstIgnoreAvecSonMotif() {
        Collaborateur complet = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur incomplet = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(performance(complet), performance(incomplet)));
        when(potentielRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(potentiel(complet)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(1);
        assertThat(resultat.ignores())
                .extracting(ResultatRecalcul.CollaborateurIgnore::matricule,
                        ResultatRecalcul.CollaborateurIgnore::motif)
                .containsExactly(org.assertj.core.api.Assertions.tuple("E002", "Notes de potentiel absentes"));
    }

    @Test
    void unCollaborateurSansNotesDePerformanceEstIgnoreAvecSonMotif() {
        Collaborateur complet = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur sansPerf = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(performance(complet)));
        when(potentielRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(potentiel(complet), potentiel(sansPerf)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(1);
        assertThat(resultat.ignores())
                .extracting(ResultatRecalcul.CollaborateurIgnore::matricule,
                        ResultatRecalcul.CollaborateurIgnore::motif)
                .containsExactly(org.assertj.core.api.Assertions.tuple("E002", "Notes de performance absentes"));
    }

    @Test
    void unCollaborateurArchiveEstEcarteDuRecalcul() {
        Collaborateur archive = collaborateur("E003", StatutCollaborateur.ARCHIVE);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(performance(archive)));
        when(potentielRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(potentiel(archive)));

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isZero();
        assertThat(resultat.ignores()).singleElement()
                .extracting(ResultatRecalcul.CollaborateurIgnore::motif)
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
