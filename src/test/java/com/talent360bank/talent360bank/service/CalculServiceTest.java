package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PonderationSources;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.SeuilsGapCompetence;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculServiceTest {

    @Mock
    private ParametreRepository parametreRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private PotentielRepository potentielRepository;

    private CalculService calculService;

    private Collaborateur collaborateur;
    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        calculService = new CalculService(parametreRepository, performanceRepository, potentielRepository);

        collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("E001");
        collaborateur.setNom("Bennani");
        collaborateur.setPrenom("Sara");
        collaborateur.setDateEntree(LocalDate.of(2020, 1, 15));

        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        parametre = Parametre.parDefaut(trimestre);
    }

    private Performance performance() {
        return new Performance(collaborateur, trimestre,
                new BigDecimal("90"),   // objectifs      x 40%
                new BigDecimal("80"),   // competences    x 20%
                new BigDecimal("70"),   // comportement   x 20%
                new BigDecimal("60"),   // contribution   x 10%
                new BigDecimal("50"));  // developpement  x 10%
    }

    private Potentiel potentiel() {
        return new Potentiel(collaborateur, trimestre,
                new BigDecimal("90"),   // learning       x 20%
                new BigDecimal("80"),   // leadership     x 20%
                new BigDecimal("70"),   // adaptabilite   x 15%
                new BigDecimal("60"),   // complexite     x 15%
                new BigDecimal("50"),   // mobilite       x 10%
                new BigDecimal("40"),   // strategie      x 10%
                new BigDecimal("30"));  // autonomie      x 10%
    }

    @Test
    void leScoreDePerformanceAppliqueLesPoidsDuParametre() {
        // 90x40 + 80x20 + 70x20 + 60x10 + 50x10 = 7700 / 100
        BigDecimal score = calculService.calculerScorePerformance(performance(), parametre);

        assertThat(score).isEqualByComparingTo("77");
        assertThat(score.scale()).isEqualTo(CalculService.PRECISION_SCORE);
    }

    @ParameterizedTest
    @CsvSource({
            "100, EXCEPTIONNELLE",
            "90.00, EXCEPTIONNELLE",   // borne inclusive
            "89.99, ELEVEE",
            "80.00, ELEVEE",
            "79.99, SOLIDE",
            "70.00, SOLIDE",
            "69.99, A_RENFORCER",
            "60.00, A_RENFORCER",
            "59.99, INSUFFISANTE",
            "0, INSUFFISANTE"
    })
    void laCategorieDePerformanceSuitLesSeuilsBornesIncluses(String score, CategoriePerformance attendue) {
        assertThat(calculService.categoriePerformance(new BigDecimal(score),
                parametre.getSeuilsCategoriePerformance())).isEqualTo(attendue);
    }

    @Test
    void desSeuilsDeCategorieModifiesDeplacentLaFrontiere() {
        parametre.getSeuilsCategoriePerformance().setSeuilExceptionnelle(new BigDecimal("95"));

        assertThat(calculService.categoriePerformance(new BigDecimal("92"),
                parametre.getSeuilsCategoriePerformance())).isEqualTo(CategoriePerformance.ELEVEE);
    }

    @ParameterizedTest
    @CsvSource({
            // actuel, cible, seuil, statut attendu
            "5, 3, 2, MAITRISE",       // gap negatif
            "3, 3, 2, MAITRISE",       // gap nul
            "2, 3, 2, A_DEVELOPPER",   // gap 1
            "2, 3, 4, A_DEVELOPPER",
            "1, 3, 2, PRIORITAIRE",    // gap 2 au seuil par defaut
            "1, 3, 3, A_DEVELOPPER",   // gap 2 sous un seuil de 3
            "1, 4, 3, PRIORITAIRE",    // gap 3 au seuil
            "1, 4, 4, A_DEVELOPPER",
            "1, 5, 4, PRIORITAIRE"     // gap 4, le maximum
    })
    void leStatutDeGapSuitLaFormuleDuClasseur(int actuel, int cible, int seuil, StatutGapCompetence attendu) {
        assertThat(calculService.statutGap(actuel, cible, new SeuilsGapCompetence(seuil))).isEqualTo(attendu);
    }

    @Test
    void unNiveauInconnuDonneUnStatutInconnu() {
        assertThat(calculService.statutGap(null, 3, parametre.getSeuilsGapCompetence())).isNull();
        assertThat(calculService.statutGap(2, null, parametre.getSeuilsGapCompetence())).isNull();
    }

    @Test
    void leStatutDeGapExigeUnSeuil() {
        assertThatThrownBy(() -> calculService.statutGap(1, 3, null))
                .isInstanceOf(DonneesIncompletesException.class);
        assertThatThrownBy(() -> calculService.statutGap(1, 3, new SeuilsGapCompetence(null)))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void laCategorieExigeUnScoreEtDesSeuils() {
        assertThatThrownBy(() -> calculService.categoriePerformance(null, parametre.getSeuilsCategoriePerformance()))
                .isInstanceOf(DonneesIncompletesException.class);
        assertThatThrownBy(() -> calculService.categoriePerformance(new BigDecimal("80"), null))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void leScoreDePotentielAppliqueLesPoidsDuParametre() {
        // 90x20 + 80x20 + 70x15 + 60x15 + 50x10 + 40x10 + 30x10 = 6550 / 100
        BigDecimal score = calculService.calculerScorePotentiel(potentiel(), parametre);

        assertThat(score).isEqualByComparingTo("65.50");
        assertThat(score.scale()).isEqualTo(CalculService.PRECISION_SCORE);
    }

    @Test
    void changerUnPoidsChangeLeScore() {
        parametre.getPoidsPerformance().setPoidsObjectifs(new BigDecimal("100"));
        parametre.getPoidsPerformance().setPoidsCompetences(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsComportement(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsContribution(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsDeveloppement(BigDecimal.ZERO);

        assertThat(calculService.calculerScorePerformance(performance(), parametre))
                .isEqualByComparingTo("90");
    }

    @Test
    void desPoidsHorsNormeRestentRamenesSurCent() {
        // Poids doubles : le score doit rester identique, la division se faisant
        // sur la somme reelle des poids et non sur la constante 100.
        parametre.getPoidsPerformance().setPoidsObjectifs(new BigDecimal("80"));
        parametre.getPoidsPerformance().setPoidsCompetences(new BigDecimal("40"));
        parametre.getPoidsPerformance().setPoidsComportement(new BigDecimal("40"));
        parametre.getPoidsPerformance().setPoidsContribution(new BigDecimal("20"));
        parametre.getPoidsPerformance().setPoidsDeveloppement(new BigDecimal("20"));

        assertThat(calculService.calculerScorePerformance(performance(), parametre))
                .isEqualByComparingTo("77");
    }

    @Test
    void desPoidsTousAZeroSontRefuses() {
        parametre.getPoidsPerformance().setPoidsObjectifs(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsCompetences(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsComportement(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsContribution(BigDecimal.ZERO);
        parametre.getPoidsPerformance().setPoidsDeveloppement(BigDecimal.ZERO);

        assertThatThrownBy(() -> calculService.calculerScorePerformance(performance(), parametre))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void uneNoteManquanteEstRefusee() {
        Performance incomplete = performance();
        incomplete.setNoteContribution(null);

        assertThatThrownBy(() -> calculService.calculerScorePerformance(incomplete, parametre))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("Note manquante");
    }

    @Test
    void desNotesAbsentesDeLaBaseSontSignalees() {
        when(performanceRepository.findByCollaborateurAndTrimestreAndSource(any(), any(), eq(SourceEvaluation.MANAGER)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculService.calculerScorePerformance(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("E001")
                .hasMessageContaining("T1 2026");
    }

    @Test
    void unTrimestreSansParametreEstSignale() {
        when(performanceRepository.findByCollaborateurAndTrimestreAndSource(any(), any(), eq(SourceEvaluation.MANAGER)))
                .thenReturn(Optional.of(performance()));
        when(parametreRepository.findByTrimestre(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculService.calculerScorePerformance(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Aucun paramètre");
    }

    @Test
    void leCalculCompletPasseParLesDeuxDepots() {
        when(potentielRepository.findByCollaborateurAndTrimestreAndSource(any(), any(), eq(SourceEvaluation.MANAGER)))
                .thenReturn(Optional.of(potentiel()));
        when(parametreRepository.findByTrimestre(any()))
                .thenReturn(Optional.of(parametre));

        assertThat(calculService.calculerScorePotentiel(collaborateur, trimestre))
                .isEqualByComparingTo("65.50");
    }

    // --- score officiel : manager et auto-evaluation ----------------------------

    private static PonderationSources ponderation(String manager, String auto) {
        return new PonderationSources(new BigDecimal(manager), new BigDecimal(auto));
    }

    @Test
    void leScoreOfficielMelangeLesDeuxSourcesSelonLeurPoids() {
        assertThat(calculService.scoreOfficiel(new BigDecimal("77.00"), new BigDecimal("100.00"),
                ponderation("70", "30"))).isEqualTo(new BigDecimal("83.90"));
        // Arrondi a 2 decimales, comme les autres scores : 70,33 x 0,7 + 60 x 0,3 = 67,231.
        assertThat(calculService.scoreOfficiel(new BigDecimal("70.33"), new BigDecimal("60.00"),
                ponderation("70", "30"))).isEqualTo(new BigDecimal("67.23"));
    }

    @Test
    void sansAutoEvaluationOuAvecUnPoidsAutoNulLeScoreOfficielEstCeluiDuManager() {
        BigDecimal manager = new BigDecimal("77.00");
        assertThat(calculService.scoreOfficiel(manager, null, ponderation("70", "30"))).isSameAs(manager);
        assertThat(calculService.scoreOfficiel(manager, new BigDecimal("12.00"), ponderation("100", "0")))
                .isSameAs(manager);
        // Sans auto-evaluation, la ponderation n'est meme pas lue : le resultat d'avant.
        assertThat(calculService.scoreOfficiel(manager, null, null)).isSameAs(manager);
    }

    @Test
    void sansEvaluationDuManagerIlNYAPasDeScoreOfficielSaufAPoidsManagerNul() {
        assertThat(calculService.scoreOfficiel(null, new BigDecimal("80.00"), ponderation("70", "30"))).isNull();
        assertThat(calculService.scoreOfficiel(null, new BigDecimal("80.00"), ponderation("0", "100")))
                .isEqualByComparingTo("80");
        assertThat(calculService.scoreOfficiel(null, null, ponderation("70", "30"))).isNull();
    }

    @Test
    void uneAutoEvaluationAMelangerExigeLaPonderationDesSources() {
        assertThatThrownBy(() -> calculService.scoreOfficiel(new BigDecimal("77"), new BigDecimal("90"), null))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("pondération des sources");
    }
}
