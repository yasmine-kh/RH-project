package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.NiveauGrille;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NeufBoxServiceTest {

    @Mock
    private Matrice9BoxRepository matriceRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;

    private NeufBoxService neufBoxService;

    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        neufBoxService = new NeufBoxService(matriceRepository, scoreRepository, calculService);

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

    private Score score(Employe employe, String performance, String potentiel) {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(potentiel == null ? null : new BigDecimal(potentiel));
        return score;
    }

    private Matrice9Box caseMatrice(int rangPerformance, int rangPotentiel, String categorie) {
        Matrice9Box case9Box = new Matrice9Box();
        case9Box.setNiveauPerformance(rangPerformance);
        case9Box.setNiveauPotentiel(rangPotentiel);
        case9Box.setCategorie(categorie);
        return case9Box;
    }

    /** Les neuf cases, libelles fictifs : le service ne doit rien supposer de leur contenu. */
    private List<Matrice9Box> matriceComplete() {
        List<Matrice9Box> cases = new ArrayList<>();
        for (int performance = 1; performance <= 3; performance++) {
            for (int potentiel = 1; potentiel <= 3; potentiel++) {
                cases.add(caseMatrice(performance, potentiel, "Case-" + performance + potentiel));
            }
        }
        return cases;
    }

    @ParameterizedTest
    @CsvSource({
            "100, ELEVE",
            "85.00, ELEVE",   // borne inclusive
            "84.99, MOYEN",
            "70.00, MOYEN",   // borne inclusive
            "69.99, FAIBLE",
            "0, FAIBLE"
    })
    void leNiveauSuitLesSeuilsBornesIncluses(String score, NiveauGrille attendu) {
        assertThat(neufBoxService.niveauPour(new BigDecimal(score), parametre.getSeuilsNeufBox()))
                .isEqualTo(attendu);
    }

    @Test
    void desSeuilsModifiesDeplacentLaFrontiere() {
        parametre.getSeuilsNeufBox().setSeuilEleve(new BigDecimal("60"));
        parametre.getSeuilsNeufBox().setSeuilMoyen(new BigDecimal("40"));

        assertThat(neufBoxService.niveauPour(new BigDecimal("65"), parametre.getSeuilsNeufBox()))
                .isEqualTo(NiveauGrille.ELEVE);
    }

    @Test
    void unScoreAbsentEstRefuse() {
        assertThatThrownBy(() -> neufBoxService.niveauPour(null, parametre.getSeuilsNeufBox()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void lePlacementCroiseLesDeuxAxes() {
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(3, 1))
                .thenReturn(Optional.of(caseMatrice(3, 1, "Expert confirme")));

        Matrice9Box case9Box = neufBoxService.placer(
                new BigDecimal("90"), new BigDecimal("50"), parametre);

        assertThat(case9Box.getCategorie()).isEqualTo("Expert confirme");
    }

    @Test
    void uneCaseManquanteDansLaReferenceEstSignalee() {
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> neufBoxService.placer(
                new BigDecimal("90"), new BigDecimal("90"), parametre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("table de reference");
    }

    @Test
    void lePlacementEcritLaCategorieDansLeScore() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        Score score = score(employe, "90", "90");

        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.of(score));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(3, 3))
                .thenReturn(Optional.of(caseMatrice(3, 3, "Etoile montante")));
        when(scoreRepository.save(any(Score.class))).thenAnswer(appel -> appel.getArgument(0));

        assertThat(neufBoxService.placerEtEnregistrer(employe, trimestre).getPositionBox())
                .isEqualTo("Etoile montante");
    }

    @Test
    void unEmployeSansScoreCalculeEstSignale() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> neufBoxService.placerEtEnregistrer(employe, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Aucun score calcule");
    }

    @Test
    void lePlacementDeTrimestrePlaceChaqueEmployeDansSaCase() {
        Employe premier = employe("E001", StatutEmploye.ACTIF);
        Employe second = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(matriceRepository.findAll()).thenReturn(matriceComplete());
        when(scoreRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(score(premier, "90", "90"), score(second, "50", "75")));
        when(scoreRepository.save(any(Score.class))).thenAnswer(appel -> appel.getArgument(0));

        ResultatRecalcul resultat = neufBoxService.placerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isEqualTo(2);
        assertThat(resultat.scoresEnregistres())
                .extracting(Score::getPositionBox)
                .containsExactly("Case-33", "Case-12");
    }

    @Test
    void unScoreIncompletEstIgnoreAvecSonMotif() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(matriceRepository.findAll()).thenReturn(matriceComplete());
        when(scoreRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(score(employe, "90", null)));

        ResultatRecalcul resultat = neufBoxService.placerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isZero();
        assertThat(resultat.ignores()).singleElement()
                .extracting(ResultatRecalcul.EmployeIgnore::motif)
                .asString().contains("Score incomplet");
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void unEmployeArchiveNEstPasPlace() {
        Employe archive = employe("E003", StatutEmploye.ARCHIVE);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(matriceRepository.findAll()).thenReturn(matriceComplete());
        when(scoreRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(score(archive, "90", "90")));

        ResultatRecalcul resultat = neufBoxService.placerTrimestre(trimestre);

        assertThat(resultat.nombreCalcules()).isZero();
        assertThat(resultat.ignores()).singleElement()
                .extracting(ResultatRecalcul.EmployeIgnore::motif)
                .asString().contains("ARCHIVE");
    }

    @Test
    void uneTableDeReferenceVideArreteLePlacement() {
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(matriceRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> neufBoxService.placerTrimestre(trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Matrice9Box est vide");

        verify(scoreRepository, never()).save(any());
    }
}
