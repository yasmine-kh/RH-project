package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VivierThematiqueServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;

    private TalentService talentService;
    private CalculService calculService;
    private VivierThematiqueService service;
    private Trimestre trimestre;

    @BeforeEach
    void init() {
        calculService = new CalculService(parametreRepository, performanceRepository, potentielRepository);
        talentService = new TalentService(scoreRepository, calculService);
        ViviersThematiquesEnMemoire rattachement = new ViviersThematiquesEnMemoire()
                .rattacher(VivierThematique.COMMERCIAL, "Reseau Retail", "Corporate Banking")
                .rattacher(VivierThematique.MANAGEMENT, "RH");
        service = new VivierThematiqueService(talentService, calculService, scoreRepository, rattachement);

        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
    }

    @Test
    void chaque_employe_est_range_dans_le_vivier_de_sa_direction_avec_ses_statuts() {
        donnerLesScores(
                score("BP001", "Reseau Retail", "60", "65"),      // ni talent ni HP : membre quand meme
                score("BP005", "Reseau Retail", "95", "92"),      // talent et HP
                score("BP026", "Corporate Banking", "80", "90"),  // HP seul
                score("BP083", "RH", "78", "88"));                // HP seul

        ResultatViviersThematiques resultat = service.getViviersThematiques(trimestre);

        // Deux directions, un vivier, du meilleur au moins bon en performance.
        assertThat(resultat.membresDe(VivierThematique.COMMERCIAL))
                .extracting(m -> m.score().getEmploye().getEmployeeId(),
                        MembreVivierThematique::talent, MembreVivierThematique::hautPotentiel)
                .containsExactly(
                        tuple("BP005", true, true),
                        tuple("BP026", false, true),
                        tuple("BP001", false, false));
        assertThat(resultat.membresDe(VivierThematique.MANAGEMENT))
                .extracting(m -> m.score().getEmploye().getEmployeeId()).containsExactly("BP083");
        assertThat(resultat.nonClasses()).isEmpty();
    }

    @Test
    void chaque_vivier_est_present_meme_vide() {
        donnerLesScores(score("BP083", "RH", "78", "88"));

        ResultatViviersThematiques resultat = service.getViviersThematiques(trimestre);

        assertThat(resultat.membres()).containsOnlyKeys(VivierThematique.values());
        assertThat(resultat.membresDe(VivierThematique.DIGITAL)).isEmpty();
    }

    @Test
    void une_direction_sans_vivier_est_rendue_a_part() {
        donnerLesScores(
                score("BP050", "Direction inconnue", "90", "90"),
                score("BP051", null, "90", "90"),
                score("BP052", "  RH  ", "70", "70"));   // espaces ignores

        ResultatViviersThematiques resultat = service.getViviersThematiques(trimestre);

        assertThat(resultat.nonClasses()).extracting(Employe::getEmployeeId).containsExactly("BP050", "BP051");
        assertThat(resultat.membresDe(VivierThematique.MANAGEMENT))
                .extracting(m -> m.score().getEmploye().getEmployeeId()).containsExactly("BP052");
    }

    @Test
    void les_scores_incomplets_restent_dans_le_vivier_sans_statut() {
        donnerLesScores(score("BP010", "RH", null, "95"), score("BP011", "RH", "90", "90"));

        List<MembreVivierThematique> management = service.getVivier(VivierThematique.MANAGEMENT, trimestre);

        // Scores incomplets en dernier, sans statut de talent ni de HP.
        assertThat(management)
                .extracting(m -> m.score().getEmploye().getEmployeeId(),
                        MembreVivierThematique::talent, MembreVivierThematique::hautPotentiel)
                .containsExactly(tuple("BP011", true, true), tuple("BP010", false, false));
    }

    @Test
    void les_employes_hors_perimetre_sont_ignores() {
        Score parti = score("BP099", "RH", "95", "95");
        parti.getEmploye().setStatut(StatutEmploye.INACTIF);
        donnerLesScores(parti);

        ResultatViviersThematiques resultat = service.getViviersThematiques(trimestre);

        assertThat(resultat.membresDe(VivierThematique.MANAGEMENT)).isEmpty();
        assertThat(resultat.nonClasses()).isEmpty();
    }

    @Test
    void sans_source_declaree_personne_n_est_classe() {
        VivierThematiqueService sansSource = new VivierThematiqueService(talentService, calculService,
                scoreRepository, new StaticListableBeanFactory().getBeanProvider(VivierThematiqueSource.class));
        donnerLesScores(score("BP005", "Reseau Retail", "95", "92"));

        ResultatViviersThematiques resultat = sansSource.getViviersThematiques(trimestre);

        assertThat(resultat.membres().values()).allSatisfy(membres -> assertThat(membres).isEmpty());
        assertThat(resultat.nonClasses()).extracting(Employe::getEmployeeId).containsExactly("BP005");
    }

    @Test
    void sans_reglages_du_trimestre_le_calcul_echoue() {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getViviersThematiques(trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // --- outils --------------------------------------------------------------

    private void donnerLesScores(Score... scores) {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(Parametre.parDefaut(trimestre)));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(List.of(scores));
    }

    private Score score(String employeeId, String direction, String performance, String potentiel) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
        employe.setDirection(direction);
        employe.setStatut(StatutEmploye.ACTIF);
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(potentiel == null ? null : new BigDecimal(potentiel));
        return score;
    }
}
