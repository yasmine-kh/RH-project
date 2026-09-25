package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;

    private TalentService talentService;

    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        talentService = new TalentService(scoreRepository, calculService);

        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        parametre = Parametre.parDefaut(trimestre);
    }

    private Employe employe(String employeeId, StatutEmploye statut) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
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

    @ParameterizedTest
    @CsvSource({
            "85.00, 85.00, true",    // les deux exactement au seuil
            "90.00, 90.00, true",
            "84.99, 90.00, false",   // performance juste en dessous
            "90.00, 84.99, false",   // potentiel juste en dessous
            "100.00, 10.00, false",  // une performance parfaite ne rattrape pas
            "10.00, 100.00, false",  // un potentiel parfait non plus
            "0.00, 0.00, false"
    })
    void laRegleEstUnEtAvecBornesIncluses(String performance, String potentiel, boolean attendu) {
        assertThat(talentService.estTalent(
                new BigDecimal(performance), new BigDecimal(potentiel), parametre.getSeuilsTalent()))
                .isEqualTo(attendu);
    }

    @Test
    void desSeuilsModifiesDeplacentLaRegle() {
        parametre.getSeuilsTalent().setSeuilPerformance(new BigDecimal("60"));
        parametre.getSeuilsTalent().setSeuilPotentiel(new BigDecimal("60"));

        assertThat(talentService.estTalent(
                new BigDecimal("65"), new BigDecimal("70"), parametre.getSeuilsTalent()))
                .isTrue();
    }

    @Test
    void lesDeuxSeuilsSontIndependants() {
        parametre.getSeuilsTalent().setSeuilPerformance(new BigDecimal("90"));
        parametre.getSeuilsTalent().setSeuilPotentiel(new BigDecimal("70"));

        assertThat(talentService.estTalent(
                new BigDecimal("92"), new BigDecimal("75"), parametre.getSeuilsTalent())).isTrue();
        assertThat(talentService.estTalent(
                new BigDecimal("88"), new BigDecimal("95"), parametre.getSeuilsTalent())).isFalse();
    }

    @Test
    void unScoreManquantEmpecheDeStatuer() {
        assertThatThrownBy(() -> talentService.estTalent(
                new BigDecimal("90"), null, parametre.getSeuilsTalent()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void unEmployeSansScoreCalculeEstSignale() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> talentService.estTalent(employe, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("E001");
    }

    @Test
    void statueSurUnEmployeDepuisSonScoreEnBase() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(scoreRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(score(employe, "88", "91")));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        assertThat(talentService.estTalent(employe, trimestre)).isTrue();
    }

    @Test
    void unScoreEnBaseSansPotentielEmpecheDeStatuer() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(scoreRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(score(employe, "90", null)));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        assertThatThrownBy(() -> talentService.estTalent(employe, trimestre))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void laDetectionNeRetientQueLesTalents() {
        Employe talent = employe("E001", StatutEmploye.ACTIF);
        Employe presque = employe("E002", StatutEmploye.ACTIF);
        Employe moyen = employe("E003", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(talent, "90", "88"),
                score(presque, "84.99", "99"),
                score(moyen, "60", "60")));

        assertThat(talentService.detecterTalents(trimestre))
                .extracting(s -> s.getEmploye().getEmployeeId())
                .containsExactly("E001");
    }

    @Test
    void lesTalentsSontTriesParPerformanceDecroissante() {
        Employe premier = employe("E001", StatutEmploye.ACTIF);
        Employe second = employe("E002", StatutEmploye.ACTIF);
        Employe troisieme = employe("E003", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(premier, "88", "90"),
                score(second, "97", "90"),
                score(troisieme, "92", "90")));

        assertThat(talentService.detecterTalents(trimestre))
                .extracting(s -> s.getEmploye().getEmployeeId())
                .containsExactly("E002", "E003", "E001");
    }

    @Test
    void unEmployeArchiveNEstJamaisTalent() {
        Employe archive = employe("E003", StatutEmploye.ARCHIVE);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any()))
                .thenReturn(List.of(score(archive, "99", "99")));

        assertThat(talentService.detecterTalents(trimestre)).isEmpty();
    }

    @Test
    void unScoreIncompletNeFaitPasEchouerTouteLaDetection() {
        Employe talent = employe("E001", StatutEmploye.ACTIF);
        Employe incomplet = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(talent, "90", "90"),
                score(incomplet, "95", null)));

        assertThat(talentService.detecterTalents(trimestre))
                .extracting(s -> s.getEmploye().getEmployeeId())
                .containsExactly("E001");
    }

    @Test
    void compterTalentsRenvoieLeMemeResultatQueLaDetection() {
        Employe talent = employe("E001", StatutEmploye.ACTIF);
        Employe moyen = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(talent, "90", "90"),
                score(moyen, "50", "50")));

        assertThat(talentService.compterTalents(trimestre)).isEqualTo(1);
    }

    @Test
    void unTrimestreSansParametreEstSignale() {
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> talentService.detecterTalents(trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Aucun parametre");
    }

    // --- haut potentiel ------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "75.00, 85.00, true",    // les deux exactement au seuil (perf 75, pot 85)
            "74.99, 90.00, false",   // performance juste en dessous
            "90.00, 84.99, false",   // potentiel juste en dessous
            "80.00, 90.00, true",    // haut potentiel sans etre talent
            "100.00, 70.00, false"   // une performance parfaite ne rattrape pas le potentiel
    })
    void leHautPotentielEstUnEtAvecBornesIncluses(String performance, String potentiel, boolean attendu) {
        assertThat(talentService.estHautPotentiel(
                new BigDecimal(performance), new BigDecimal(potentiel), parametre.getSeuilsTalent()))
                .isEqualTo(attendu);
    }

    @Test
    void lesSeuilsDeHautPotentielViennentDuParametre() {
        parametre.getSeuilsTalent().setSeuilHautPotentielPerformance(new BigDecimal("60"));
        parametre.getSeuilsTalent().setSeuilHautPotentielPotentiel(new BigDecimal("70"));

        assertThat(talentService.estHautPotentiel(
                new BigDecimal("65"), new BigDecimal("72"), parametre.getSeuilsTalent())).isTrue();
    }

    @Test
    void unScoreManquantEmpecheDeStatuerSurLeHautPotentiel() {
        assertThatThrownBy(() -> talentService.estHautPotentiel(
                null, new BigDecimal("90"), parametre.getSeuilsTalent()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void desSeuilsDeHautPotentielNonConfiguresSontSignales() {
        parametre.getSeuilsTalent().setSeuilHautPotentielPotentiel(null);

        assertThatThrownBy(() -> talentService.estHautPotentiel(
                new BigDecimal("90"), new BigDecimal("90"), parametre.getSeuilsTalent()))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("haut potentiel");
    }

    @Test
    void statueSurLeHautPotentielDUnEmployeDepuisSonScoreEnBase() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(scoreRepository.findByEmployeAndTrimestre(any(), any()))
                .thenReturn(Optional.of(score(employe, "78", "88")));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        assertThat(talentService.estHautPotentiel(employe, trimestre)).isTrue();
        assertThat(talentService.estTalent(employe, trimestre)).isFalse();
    }

    @Test
    void unEmployeSansScoreEstSignalePourLeHautPotentiel() {
        Employe employe = employe("E009", StatutEmploye.ACTIF);
        when(scoreRepository.findByEmployeAndTrimestre(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> talentService.estHautPotentiel(employe, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("E009");
    }

    @Test
    void laDetectionDesHautsPotentielsTrieEtEcarteArchivesEtIncomplets() {
        Employe hpSeul = employe("E001", StatutEmploye.ACTIF);
        Employe talent = employe("E002", StatutEmploye.ACTIF);
        Employe sousLeSeuil = employe("E003", StatutEmploye.ACTIF);
        Employe archive = employe("E004", StatutEmploye.ARCHIVE);
        Employe incomplet = employe("E005", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(hpSeul, "76", "86"),
                score(talent, "92", "90"),
                score(sousLeSeuil, "74.99", "95"),
                score(archive, "99", "99"),
                score(incomplet, "90", null)));

        assertThat(talentService.detecterHautsPotentiels(trimestre))
                .extracting(s -> s.getEmploye().getEmployeeId())
                .containsExactly("E002", "E001");
    }

    // --- vivier de releve ----------------------------------------------------

    @Test
    void leVivierReunitTalentsEtHautsPotentielsSansDoublonAvecLaRaison() {
        // Avec les valeurs par defaut (talent 85/85, HP 75/85), tout talent est
        // aussi HP. Seuils HP deplaces (perf >= 90, pot >= 80) pour couvrir les
        // trois cas : talent seul, HP seul, les deux.
        parametre.getSeuilsTalent().setSeuilHautPotentielPerformance(new BigDecimal("90"));
        parametre.getSeuilsTalent().setSeuilHautPotentielPotentiel(new BigDecimal("80"));

        Employe talentSeul = employe("E001", StatutEmploye.ACTIF);
        Employe hpSeul = employe("E002", StatutEmploye.ACTIF);
        Employe lesDeux = employe("E003", StatutEmploye.ACTIF);
        Employe aucun = employe("E004", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(talentSeul, "87", "86"),   // talent ; pas HP (perf < 90)
                score(hpSeul, "91", "84"),       // HP ; pas talent (pot < 85)
                score(lesDeux, "95", "92"),      // les deux : une seule entree
                score(aucun, "60", "60")));

        List<MembreVivierReleve> vivier = talentService.getVivierReleve(trimestre);

        assertThat(vivier)
                .extracting(m -> m.score().getEmploye().getEmployeeId(), MembreVivierReleve::talent,
                        MembreVivierReleve::hautPotentiel)
                .containsExactly(
                        tuple("E003", true, true),
                        tuple("E002", false, true),
                        tuple("E001", true, false));
    }

    @Test
    void leVivierEcarteArchivesEtScoresIncomplets() {
        Employe archive = employe("E001", StatutEmploye.ARCHIVE);
        Employe incomplet = employe("E002", StatutEmploye.ACTIF);
        Employe membre = employe("E003", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(List.of(
                score(archive, "99", "99"),
                score(incomplet, null, "99"),
                score(membre, "80", "90")));

        assertThat(talentService.getVivierReleve(trimestre))
                .extracting(m -> m.score().getEmploye().getEmployeeId())
                .containsExactly("E003");
    }
}
