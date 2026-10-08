package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PonderationSources;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
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

    /** Auto-evaluation : 100 partout en performance (score 100), 80 partout en potentiel (score 80). */
    private Performance performanceAuto(Collaborateur collaborateur) {
        BigDecimal note = new BigDecimal("100");
        Performance auto = new Performance(collaborateur, trimestre, note, note, note, note, note);
        auto.setSource(SourceEvaluation.AUTO);
        return auto;
    }

    private Potentiel potentielAuto(Collaborateur collaborateur) {
        BigDecimal note = new BigDecimal("80");
        Potentiel auto = new Potentiel(collaborateur, trimestre, note, note, note, note, note, note, note);
        auto.setSource(SourceEvaluation.AUTO);
        return auto;
    }

    /** Le trimestre avec ces notes (les deux sources melangees, comme la requete). */
    private void notesDuTrimestre(List<Performance> performances, List<Potentiel> potentiels) {
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreToutesSources(any())).thenReturn(performances);
        when(potentielRepository.findByTrimestreToutesSources(any())).thenReturn(potentiels);
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of());
    }

    private void renvoieCeQuOnLuiDonne() {
        when(scoreRepository.save(any(Score.class))).thenAnswer(appel -> appel.getArgument(0));
    }

    @Test
    void enregistreLesDeuxScoresEtLaDateDuJour() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(potentiel(collaborateur)));
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
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(potentiel(collaborateur)));
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());
        renvoieCeQuOnLuiDonne();

        assertThat(scoreService.calculerEtEnregistrer(collaborateur, trimestre).getPositionBox()).isNull();
    }

    @Test
    void leRecalculPoseLaCategorieDePerformanceMaisPasCelleDePotentiel() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(potentiel(collaborateur)));
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
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(potentiel(collaborateur)));
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
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(performance(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(potentiel(collaborateur)));
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
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> scoreService.calculerEtEnregistrer(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void leRecalculDeTrimestreScoreLesCollaborateursComplets() {
        Collaborateur premier = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur second = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(performance(premier), performance(second)));
        when(potentielRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(potentiel(premier), potentiel(second)));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(2);
        assertThat(resultat.nombreIgnores()).isZero();
    }

    /**
     * Les scores existants du trimestre sont lus en une fois et mis a jour sur
     * place : aucune recherche par collaborateur (N+1, audit initial 3.4).
     */
    @Test
    void leRecalculMetAJourLesScoresExistantsSansLesChercherUnParUn() {
        Collaborateur deja = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur nouveau = collaborateur("E002", StatutCollaborateur.ACTIF);
        Score existant = new Score();
        existant.setIdScore(42);
        existant.setCollaborateur(deja);
        existant.setTrimestre(trimestre);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(performance(deja), performance(nouveau)));
        when(potentielRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(potentiel(deja), potentiel(nouveau)));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(List.of(existant));
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.scoresEnregistres()).hasSize(2);
        assertThat(resultat.scoresEnregistres().get(0)).isSameAs(existant);
        assertThat(existant.getScorePerformance()).isNotNull();
        assertThat(resultat.scoresEnregistres().get(1).getIdScore()).isNull();
        assertThat(resultat.scoresEnregistres().get(1).getCollaborateur()).isSameAs(nouveau);
        verify(scoreRepository, never()).findByCollaborateurAndTrimestre(any(), any());
        verify(scoreRepository).supprimerSaufCeux(trimestre, List.of(42));
    }

    @Test
    void unCollaborateurSansNotesDePotentielEstIgnoreAvecSonMotif() {
        Collaborateur complet = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur incomplet = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(performance(complet), performance(incomplet)));
        when(potentielRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(potentiel(complet)));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of());
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
        when(performanceRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(performance(complet)));
        when(potentielRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(potentiel(complet), potentiel(sansPerf)));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of());
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(1);
        assertThat(resultat.ignores())
                .extracting(ResultatRecalcul.CollaborateurIgnore::matricule,
                        ResultatRecalcul.CollaborateurIgnore::motif)
                .containsExactly(org.assertj.core.api.Assertions.tuple("E002", "Notes de performance absentes"));
    }

    // --- auto-evaluation et evaluation du manager --------------------------------

    /**
     * Manager : 77 en performance, 65,50 en potentiel ; auto : 100 et 80.
     * 70 / 30 : 77 x 0,7 + 100 x 0,3 = 83,90 ; 65,50 x 0,7 + 80 x 0,3 = 69,85.
     */
    @Test
    void leMelange70_30DesDeuxSourcesDonneLeScoreOfficielAttendu() {
        parametre.setPonderationSources(new PonderationSources(new BigDecimal("70"), new BigDecimal("30")));
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        notesDuTrimestre(List.of(performance(collaborateur), performanceAuto(collaborateur)),
                List.of(potentielAuto(collaborateur), potentiel(collaborateur)));
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.recalculerTrimestre(trimestre).scoresEnregistres().get(0);

        assertThat(score.getScorePerformance()).isEqualByComparingTo("83.90");
        assertThat(score.getScorePotentiel()).isEqualByComparingTo("69.85");
        // La categorie suit le score officiel : 83,90 est entre 80 et 90.
        assertThat(score.getCategoriePerformance()).isEqualTo(CategoriePerformance.ELEVEE);
    }

    /** 100 / 0 (le defaut) : une auto-evaluation presente ne change rien au resultat d'aujourd'hui. */
    @Test
    void aCentZeroLAutoEvaluationNeChangeRienAuScoreOfficiel() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        notesDuTrimestre(List.of(performance(collaborateur), performanceAuto(collaborateur)),
                List.of(potentiel(collaborateur), potentielAuto(collaborateur)));
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.recalculerTrimestre(trimestre).scoresEnregistres().get(0);

        assertThat(score.getScorePerformance()).isEqualTo(new BigDecimal("77.00"));
        assertThat(score.getScorePotentiel()).isEqualTo(new BigDecimal("65.50"));
        assertThat(score.getCategoriePerformance()).isEqualTo(CategoriePerformance.SOLIDE);
    }

    @Test
    void uneAutoEvaluationSeuleNeDonnePasDeScoreOfficielEtLeDit() {
        parametre.setPonderationSources(new PonderationSources(new BigDecimal("70"), new BigDecimal("30")));
        Collaborateur complet = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur autoSeul = collaborateur("E002", StatutCollaborateur.ACTIF);
        notesDuTrimestre(List.of(performance(complet), performanceAuto(autoSeul)),
                List.of(potentiel(complet), potentielAuto(autoSeul)));
        renvoieCeQuOnLuiDonne();

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.scoresEnregistres()).extracting(score -> score.getCollaborateur().getIdCollaborateur())
                .containsExactly("E001");
        assertThat(resultat.ignores()).containsExactly(
                new ResultatRecalcul.CollaborateurIgnore("E002", ScoreService.MOTIF_MANAGER_ABSENT));
    }

    /** Un seul axe sans le manager suffit : pas de score officiel du tout (la 9-box exige les deux). */
    @Test
    void sansLeManagerSurUnSeulAxeLeCollaborateurEstIgnore() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        notesDuTrimestre(List.of(performance(collaborateur)), List.of(potentielAuto(collaborateur)));

        ResultatRecalcul resultat = scoreService.recalculerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isZero();
        assertThat(resultat.ignores()).extracting(ResultatRecalcul.CollaborateurIgnore::motif)
                .containsExactly(ScoreService.MOTIF_MANAGER_ABSENT);
        verify(scoreRepository, never()).save(any());
    }

    /** 0 / 100 : le client a choisi l'auto-evaluation seule, le manager n'est plus exige. */
    @Test
    void sansPoidsManagerLAutoEvaluationSeuleDonneLeScoreOfficiel() {
        parametre.setPonderationSources(new PonderationSources(new BigDecimal("0"), new BigDecimal("100")));
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        notesDuTrimestre(List.of(performanceAuto(collaborateur)), List.of(potentielAuto(collaborateur)));
        renvoieCeQuOnLuiDonne();

        Score score = scoreService.recalculerTrimestre(trimestre).scoresEnregistres().get(0);

        assertThat(score.getScorePerformance()).isEqualByComparingTo("100");
        assertThat(score.getScorePotentiel()).isEqualByComparingTo("80");
    }

    @Test
    void leCalculDUnCollaborateurSansEvaluationDuManagerEstRefuseAvecLeMotif() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(performanceAuto(collaborateur)));
        when(potentielRepository.findByCollaborateurAndTrimestreOrderBySource(any(), any()))
                .thenReturn(List.of(potentiel(collaborateur)));

        assertThatThrownBy(() -> scoreService.calculerEtEnregistrer(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining(ScoreService.MOTIF_MANAGER_ABSENT)
                .hasMessageContaining("E001");
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void unCollaborateurArchiveEstEcarteDuRecalcul() {
        Collaborateur archive = collaborateur("E003", StatutCollaborateur.ARCHIVE);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(performanceRepository.findByTrimestreToutesSources(any()))
                .thenReturn(List.of(performance(archive)));
        when(potentielRepository.findByTrimestreToutesSources(any()))
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
                .hasMessageContaining("Aucun paramètre");

        verify(scoreRepository, never()).save(any());
    }
}
