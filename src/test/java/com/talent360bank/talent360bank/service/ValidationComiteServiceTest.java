package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationComiteServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;

    private TalentService talentService;
    private ValidationsComiteEnMemoire decisions;
    private ValidationComiteService service;
    private Trimestre trimestre;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        talentService = new TalentService(scoreRepository, calculService);
        decisions = new ValidationsComiteEnMemoire();
        service = new ValidationComiteService(talentService, decisions);

        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
    }

    @ParameterizedTest(name = "propose={0}, comite={1} -> valide={2}")
    @CsvSource({
            "true,  OUI,        true",
            "true,  NON,        false",
            "true,  EN_ATTENTE, false",
            // 10_TALENTS : le comite dit Oui pour presque tout le monde, cela
            // ne fait pas d'un non-talent un talent valide.
            "false, OUI,        false",
            "false, NON,        false",
    })
    void un_talent_valide_est_propose_et_retenu_par_le_comite(boolean propose, StatutValidationComite statut,
                                                              boolean attendu) {
        assertThat(service.estTalentValide(propose, statut)).isEqualTo(attendu);
    }

    @Test
    void chaque_talent_propose_recoit_la_decision_du_comite() {
        donnerLesScores(
                score("BP005", "95", "92"),   // talent, valide
                score("BP019", "90", "88"),   // talent, en attente (BP019 du classeur)
                score("BP040", "88", "86"),   // talent, refuse
                score("BP001", "60", "95"));  // pas talent : ignore meme si Oui
        decisions.decider("BP005", StatutValidationComite.OUI)
                .decider("BP040", StatutValidationComite.NON)
                .decider("BP001", StatutValidationComite.OUI);

        List<DecisionComite> resultat = service.getDecisionsComite(trimestre);

        assertThat(resultat)
                .extracting(d -> d.score().getCollaborateur().getIdCollaborateur(), DecisionComite::statut,
                        DecisionComite::talentValide)
                .containsExactly(
                        tuple("BP005", StatutValidationComite.OUI, true),
                        tuple("BP019", StatutValidationComite.EN_ATTENTE, false),
                        tuple("BP040", StatutValidationComite.NON, false));
        assertThat(service.getTalentsValides(trimestre))
                .extracting(s -> s.getCollaborateur().getIdCollaborateur()).containsExactly("BP005");
    }

    @Test
    void une_source_qui_rend_null_vaut_en_attente() {
        ValidationComiteService sourceIncomplete = new ValidationComiteService(talentService,
                (idCollaborateur, t) -> null);

        List<DecisionComite> resultat = sourceIncomplete.deciderPour(List.of(score("BP005", "95", "92")), trimestre);

        assertThat(resultat).singleElement()
                .extracting(DecisionComite::statut).isEqualTo(StatutValidationComite.EN_ATTENTE);
    }

    @Test
    void sans_source_declaree_aucun_talent_n_est_valide() {
        ValidationComiteService sansSource = new ValidationComiteService(talentService,
                new StaticListableBeanFactory().getBeanProvider(ValidationComiteSource.class));
        donnerLesScores(score("BP005", "95", "92"));

        assertThat(sansSource.getDecisionsComite(trimestre)).singleElement()
                .extracting(DecisionComite::statut).isEqualTo(StatutValidationComite.EN_ATTENTE);
        assertThat(sansSource.getTalentsValides(trimestre)).isEmpty();
    }

    // --- outils --------------------------------------------------------------

    private void donnerLesScores(Score... scores) {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(Parametre.parDefaut(trimestre)));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(List.of(scores));
    }

    private Score score(String idCollaborateur, String performance, String potentiel) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(idCollaborateur);
        collaborateur.setNom("Nom" + idCollaborateur);
        collaborateur.setPrenom("Prenom" + idCollaborateur);
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal(potentiel));
        return score;
    }
}
