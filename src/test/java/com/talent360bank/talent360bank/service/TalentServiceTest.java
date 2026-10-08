package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
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

    private Collaborateur collaborateur(String idCollaborateur, StatutCollaborateur statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(idCollaborateur);
        collaborateur.setNom("Nom" + idCollaborateur);
        collaborateur.setPrenom("Prenom" + idCollaborateur);
        collaborateur.setDateEntree(LocalDate.of(2020, 1, 15));
        collaborateur.setStatut(statut);
        return collaborateur;
    }

    private Score score(Collaborateur collaborateur, String performance, String potentiel) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
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
    void unCollaborateurSansScoreCalculeEstSignale() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> talentService.estTalent(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("E001");
    }

    @Test
    void statueSurUnCollaborateurDepuisSonScoreEnBase() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(score(collaborateur, "88", "91")));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        assertThat(talentService.estTalent(collaborateur, trimestre)).isTrue();
    }

    @Test
    void unScoreEnBaseSansPotentielEmpecheDeStatuer() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(score(collaborateur, "90", null)));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        assertThatThrownBy(() -> talentService.estTalent(collaborateur, trimestre))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void laDetectionNeRetientQueLesTalents() {
        Collaborateur talent = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur presque = collaborateur("E002", StatutCollaborateur.ACTIF);
        Collaborateur moyen = collaborateur("E003", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(talent, "90", "88"),
                score(presque, "84.99", "99"),
                score(moyen, "60", "60")));

        assertThat(talentService.detecterTalents(trimestre))
                .extracting(s -> s.getCollaborateur().getIdCollaborateur())
                .containsExactly("E001");
    }

    @Test
    void lesTalentsSontTriesParPerformanceDecroissante() {
        Collaborateur premier = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur second = collaborateur("E002", StatutCollaborateur.ACTIF);
        Collaborateur troisieme = collaborateur("E003", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(premier, "88", "90"),
                score(second, "97", "90"),
                score(troisieme, "92", "90")));

        assertThat(talentService.detecterTalents(trimestre))
                .extracting(s -> s.getCollaborateur().getIdCollaborateur())
                .containsExactly("E002", "E003", "E001");
    }

    @Test
    void unCollaborateurArchiveNEstJamaisTalent() {
        Collaborateur archive = collaborateur("E003", StatutCollaborateur.ARCHIVE);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any()))
                .thenReturn(List.of(score(archive, "99", "99")));

        assertThat(talentService.detecterTalents(trimestre)).isEmpty();
    }

    @Test
    void unScoreIncompletNeFaitPasEchouerTouteLaDetection() {
        Collaborateur talent = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur incomplet = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(talent, "90", "90"),
                score(incomplet, "95", null)));

        assertThat(talentService.detecterTalents(trimestre))
                .extracting(s -> s.getCollaborateur().getIdCollaborateur())
                .containsExactly("E001");
    }

    @Test
    void compterTalentsRenvoieLeMemeResultatQueLaDetection() {
        Collaborateur talent = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur moyen = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(talent, "90", "90"),
                score(moyen, "50", "50")));

        assertThat(talentService.compterTalents(trimestre)).isEqualTo(1);
    }

    @Test
    void unTrimestreSansParametreEstSignale() {
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> talentService.detecterTalents(trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Aucun paramètre");
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
    void statueSurLeHautPotentielDUnCollaborateurDepuisSonScoreEnBase() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any()))
                .thenReturn(Optional.of(score(collaborateur, "78", "88")));
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        assertThat(talentService.estHautPotentiel(collaborateur, trimestre)).isTrue();
        assertThat(talentService.estTalent(collaborateur, trimestre)).isFalse();
    }

    @Test
    void unCollaborateurSansScoreEstSignalePourLeHautPotentiel() {
        Collaborateur collaborateur = collaborateur("E009", StatutCollaborateur.ACTIF);
        when(scoreRepository.findByCollaborateurAndTrimestre(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> talentService.estHautPotentiel(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("E009");
    }

    @Test
    void laDetectionDesHautsPotentielsTrieEtEcarteArchivesEtIncomplets() {
        Collaborateur hpSeul = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur talent = collaborateur("E002", StatutCollaborateur.ACTIF);
        Collaborateur sousLeSeuil = collaborateur("E003", StatutCollaborateur.ACTIF);
        Collaborateur archive = collaborateur("E004", StatutCollaborateur.ARCHIVE);
        Collaborateur incomplet = collaborateur("E005", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(hpSeul, "76", "86"),
                score(talent, "92", "90"),
                score(sousLeSeuil, "74.99", "95"),
                score(archive, "99", "99"),
                score(incomplet, "90", null)));

        assertThat(talentService.detecterHautsPotentiels(trimestre))
                .extracting(s -> s.getCollaborateur().getIdCollaborateur())
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

        Collaborateur talentSeul = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur hpSeul = collaborateur("E002", StatutCollaborateur.ACTIF);
        Collaborateur lesDeux = collaborateur("E003", StatutCollaborateur.ACTIF);
        Collaborateur aucun = collaborateur("E004", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(talentSeul, "87", "86"),   // talent ; pas HP (perf < 90)
                score(hpSeul, "91", "84"),       // HP ; pas talent (pot < 85)
                score(lesDeux, "95", "92"),      // les deux : une seule entree
                score(aucun, "60", "60")));

        List<MembreVivierReleve> vivier = talentService.getVivierReleve(trimestre);

        assertThat(vivier)
                .extracting(m -> m.score().getCollaborateur().getIdCollaborateur(), MembreVivierReleve::talent,
                        MembreVivierReleve::hautPotentiel)
                .containsExactly(
                        tuple("E003", true, true),
                        tuple("E002", false, true),
                        tuple("E001", true, false));
    }

    @Test
    void leVivierEcarteArchivesEtScoresIncomplets() {
        Collaborateur archive = collaborateur("E001", StatutCollaborateur.ARCHIVE);
        Collaborateur incomplet = collaborateur("E002", StatutCollaborateur.ACTIF);
        Collaborateur membre = collaborateur("E003", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(any())).thenReturn(List.of(
                score(archive, "99", "99"),
                score(incomplet, null, "99"),
                score(membre, "80", "90")));

        assertThat(talentService.getVivierReleve(trimestre))
                .extracting(m -> m.score().getCollaborateur().getIdCollaborateur())
                .containsExactly("E003");
    }
}
