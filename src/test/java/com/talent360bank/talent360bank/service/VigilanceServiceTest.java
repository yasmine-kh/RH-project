package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.talent360bank.talent360bank.entity.SourceEvaluation.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VigilanceServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Mock
    private TrimestreRepository trimestreRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;
    @Mock
    private CollaborateurRepository collaborateurRepository;

    private VigilanceService vigilanceService;
    private FaitsVigilanceEnMemoire faits;

    private Trimestre trimestre;
    private Trimestre trimestrePrecedent;
    private Parametre parametre;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        faits = new FaitsVigilanceEnMemoire();
        vigilanceService = new VigilanceService(scoreRepository, questionnaireRepository,
                trimestreRepository, collaborateurRepository, performanceRepository, potentielRepository,
                calculService, faits);

        trimestre = trimestre(2, 2026);
        trimestrePrecedent = trimestre(1, 2026);

        parametre = Parametre.parDefaut(trimestre);
    }

    private Trimestre trimestre(int numero, int annee) {
        Trimestre cree = new Trimestre();
        cree.setNumero(numero);
        cree.setAnnee(annee);
        return cree;
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

    private Score score(Collaborateur collaborateur, Trimestre trimestreDuScore, String performance) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setTrimestre(trimestreDuScore);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal("70.00"));
        return score;
    }

    private QuestionnaireEngagement engagement(Collaborateur collaborateur, String scoreEngagement) {
        QuestionnaireEngagement questionnaire = new QuestionnaireEngagement();
        questionnaire.setCollaborateur(collaborateur);
        questionnaire.setTrimestre(trimestre);
        questionnaire.setScoreEngagement(scoreEngagement == null ? null : new BigDecimal(scoreEngagement));
        return questionnaire;
    }

    // --- bareme des points ---------------------------------------------------

    @Test
    void aucun_signal_donne_un_indice_nul() {
        assertThat(vigilanceService.calculerIndice(
                EnumSet.noneOf(SignalVigilance.class), parametre.getPointsVigilance()))
                .isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @CsvSource({
            "ENGAGEMENT_FAIBLE, 25.00",
            "SANS_MOBILITE_4_ANS, 20.00",
            "MOBILITE_NON_TRAITEE, 15.00",
            "SANS_DEVELOPPEMENT_RECENT, 15.00",
            "BAISSE_PERFORMANCE, 10.00",
            "FAIBLE_RECONNAISSANCE, 10.00",
            "FORMATION_NON_FAITE, 5.00"
    })
    void chaque_signal_vaut_ses_points_du_parametre(SignalVigilance signal, String attendu) {
        assertThat(vigilanceService.calculerIndice(
                EnumSet.of(signal), parametre.getPointsVigilance()))
                .isEqualByComparingTo(attendu);
    }

    @Test
    void les_points_des_signaux_declenches_s_additionnent() {
        // 25 + 20 + 15 = 60
        assertThat(vigilanceService.calculerIndice(
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE,
                        SignalVigilance.SANS_MOBILITE_4_ANS,
                        SignalVigilance.MOBILITE_NON_TRAITEE),
                parametre.getPointsVigilance()))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void les_sept_signaux_reunis_saturent_l_indice_a_cent() {
        assertThat(vigilanceService.calculerIndice(
                EnumSet.allOf(SignalVigilance.class), parametre.getPointsVigilance()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void l_indice_refuse_des_points_non_configures() {
        assertThatThrownBy(() -> vigilanceService.calculerIndice(
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE), null))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void l_indice_refuse_un_point_manquant_pour_un_signal_declenche() {
        parametre.getPointsVigilance().setPointBaissePerformance(null);

        assertThatThrownBy(() -> vigilanceService.calculerIndice(
                EnumSet.of(SignalVigilance.BAISSE_PERFORMANCE), parametre.getPointsVigilance()))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("BAISSE_PERFORMANCE");
    }

    @Test
    void un_point_manquant_pour_un_signal_non_declenche_ne_gene_pas() {
        parametre.getPointsVigilance().setPointBaissePerformance(null);

        assertThat(vigilanceService.calculerIndice(
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE), parametre.getPointsVigilance()))
                .isEqualByComparingTo("25.00");
    }

    // --- seuils --------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "0.00, FAIBLE",
            "29.99, FAIBLE",
            "30.00, MODEREE",   // borne basse inclusive
            "59.99, MODEREE",
            "60.00, ELEVEE",    // borne basse inclusive
            "100.00, ELEVEE"
    })
    void le_niveau_suit_les_seuils_du_parametre(String indice, NiveauVigilance attendu) {
        assertThat(vigilanceService.niveauPour(
                new BigDecimal(indice), parametre.getSeuilsVigilance())).isEqualTo(attendu);
    }

    @Test
    void le_niveau_refuse_un_indice_absent() {
        assertThatThrownBy(() -> vigilanceService.niveauPour(null, parametre.getSeuilsVigilance()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void le_niveau_refuse_des_seuils_non_configures() {
        parametre.getSeuilsVigilance().setSeuilEleve(null);

        assertThatThrownBy(() -> vigilanceService.niveauPour(
                new BigDecimal("60.00"), parametre.getSeuilsVigilance()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    // --- detection -----------------------------------------------------------

    @Test
    void un_engagement_sous_le_seuil_leve_le_signal() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(null, null,
                engagement(collaborateur, "59.99"), parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
    }

    @Test
    void un_engagement_au_seuil_ne_leve_pas_le_signal() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(null, null,
                engagement(collaborateur, "60.00"), parametre.getSeuilsVigilance()))
                .isEmpty();
    }

    @Test
    void le_seuil_d_engagement_vient_du_parametre_pas_du_code() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        QuestionnaireEngagement questionnaire = engagement(collaborateur, "55.00");

        // 55 est sous le seuil par defaut de 60 : le signal se leve.
        assertThat(vigilanceService.detecterSignaux(null, null, questionnaire,
                parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);

        // Le RH abaisse le seuil a 50 : le meme score ne declenche plus rien.
        parametre.getSeuilsVigilance().setSeuilEngagementFaible(new BigDecimal("50"));

        assertThat(vigilanceService.detecterSignaux(null, null, questionnaire,
                parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void un_seuil_d_engagement_non_configure_est_refuse() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        parametre.getSeuilsVigilance().setSeuilEngagementFaible(null);

        assertThatThrownBy(() -> vigilanceService.detecterSignaux(null, null,
                engagement(collaborateur, "10.00"), parametre.getSeuilsVigilance()))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("engagement");
    }

    @Test
    void un_seuil_d_engagement_non_configure_ne_gene_pas_sans_questionnaire() {
        // Rien a comparer : l'absence de seuil ne doit pas faire echouer le lot.
        parametre.getSeuilsVigilance().setSeuilEngagementFaible(null);

        assertThat(vigilanceService.detecterSignaux(null, null, null,
                parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void un_questionnaire_absent_ne_leve_pas_le_signal() {
        // Sans reponse on ne sait pas si l'engagement est faible, on ne le suppose pas.
        assertThat(vigilanceService.detecterSignaux(null, null, null, parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void un_questionnaire_sans_score_ne_leve_pas_le_signal() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(null, null, engagement(collaborateur, null),
                parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void un_recul_de_performance_leve_le_signal() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "69.99"),
                score(collaborateur, trimestrePrecedent, "70.00"),
                null, parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.BAISSE_PERFORMANCE);
    }

    @Test
    void une_performance_stable_ou_en_hausse_ne_leve_pas_le_signal() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "70.00"),
                score(collaborateur, trimestrePrecedent, "70.00"), null, parametre.getSeuilsVigilance())).isEmpty();
        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "80.00"),
                score(collaborateur, trimestrePrecedent, "70.00"), null, parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void sans_trimestre_precedent_la_baisse_est_indetectable() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "10.00"), null, null, parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void les_deux_signaux_detectables_peuvent_se_cumuler() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "60.00"),
                score(collaborateur, trimestrePrecedent, "70.00"),
                engagement(collaborateur, "30.00"), parametre.getSeuilsVigilance()))
                .containsExactlyInAnyOrder(SignalVigilance.ENGAGEMENT_FAIBLE,
                        SignalVigilance.BAISSE_PERFORMANCE);
    }

    @Test
    void la_detection_automatique_ne_leve_que_les_signaux_declares_detectables() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        Set<SignalVigilance> signaux = vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "60.00"),
                score(collaborateur, trimestrePrecedent, "70.00"),
                engagement(collaborateur, "10.00"), parametre.getSeuilsVigilance());

        assertThat(signaux).allMatch(SignalVigilance::estDetectable);
    }

    @ParameterizedTest
    @EnumSource(SignalVigilance.class)
    void chaque_signal_sait_lire_ses_points(SignalVigilance signal) {
        assertThat(signal.pointsDans(parametre.getPointsVigilance())).isNotNull();
        assertThat(signal.getLibelle()).isNotBlank();
    }

    // --- faits importes --------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = SignalVigilance.class, names = {"SANS_MOBILITE_4_ANS", "MOBILITE_NON_TRAITEE",
            "SANS_DEVELOPPEMENT_RECENT", "FAIBLE_RECONNAISSANCE", "FORMATION_NON_FAITE"})
    void chaque_fait_importe_a_oui_leve_son_signal(SignalVigilance signal) {
        FaitsVigilance declares = FaitsVigilance.builder().declarer(signal, true).build();

        assertThat(vigilanceService.detecterSignaux(null, null, null, declares, parametre.getSeuilsVigilance()))
                .containsExactly(signal);
    }

    @ParameterizedTest
    @EnumSource(value = SignalVigilance.class, names = "ENGAGEMENT_FAIBLE", mode = EnumSource.Mode.EXCLUDE)
    void un_fait_importe_a_non_ou_inconnu_ne_leve_rien(SignalVigilance signal) {
        FaitsVigilance aNon = FaitsVigilance.builder().declarer(signal, false).build();
        FaitsVigilance inconnu = FaitsVigilance.builder().declarer(signal, null).build();

        assertThat(vigilanceService.detecterSignaux(null, null, null, aNon, parametre.getSeuilsVigilance()))
                .isEmpty();
        assertThat(vigilanceService.detecterSignaux(null, null, null, inconnu, parametre.getSeuilsVigilance()))
                .isEmpty();
    }

    @Test
    void des_faits_absents_valent_aucun_fait() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(score(collaborateur, trimestre, "70.00"), null,
                null, null, parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void sans_score_precedent_la_baisse_importee_decide() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        FaitsVigilance baisse = FaitsVigilance.builder().baissePerformance(true).build();

        assertThat(vigilanceService.detecterSignaux(score(collaborateur, trimestre, "70.00"), null,
                null, baisse, parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.BAISSE_PERFORMANCE);
        // Un score precedent sans performance ne permet pas non plus de mesurer.
        assertThat(vigilanceService.detecterSignaux(score(collaborateur, trimestre, "70.00"),
                score(collaborateur, trimestrePrecedent, null), null, baisse, parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.BAISSE_PERFORMANCE);
    }

    @Test
    void avec_un_score_precedent_l_historique_prime_sur_la_baisse_importee() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "70.00"), score(collaborateur, trimestrePrecedent, "70.00"), null,
                FaitsVigilance.builder().baissePerformance(true).build(), parametre.getSeuilsVigilance()))
                .isEmpty();
        assertThat(vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "60.00"), score(collaborateur, trimestrePrecedent, "70.00"), null,
                FaitsVigilance.builder().baissePerformance(false).build(), parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.BAISSE_PERFORMANCE);
    }

    @Test
    void les_faits_importes_permettent_d_atteindre_le_niveau_eleve() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        faits.declarer("E001", FaitsVigilance.builder()
                .sansMobilite4Ans(true).mobiliteNonTraitee(true)
                .sansDeveloppementRecent(true).faibleReconnaissance(true).build());

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any())).thenReturn(List.of());

        ResultatVigilance resultat = vigilanceService.evaluer(collaborateur, trimestre);

        // 20 + 15 + 15 + 10 = 60 : au seuil eleve.
        assertThat(resultat.indice()).isEqualByComparingTo("60.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.ELEVEE);
    }

    @Test
    void le_lot_applique_a_chaque_collaborateur_ses_propres_faits() {
        Collaborateur declare = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur inconnu = collaborateur("E002", StatutCollaborateur.ACTIF);
        faits.declarer("E001", FaitsVigilance.builder().formationNonFaite(true).baissePerformance(true).build());

        preparerLotSansPrecedent(List.of(score(inconnu, trimestre, "70.00"), score(declare, trimestre, "70.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot.get(0).collaborateur().getIdCollaborateur()).isEqualTo("E001");
        assertThat(lot.get(0).signaux()).containsExactlyInAnyOrder(
                SignalVigilance.FORMATION_NON_FAITE, SignalVigilance.BAISSE_PERFORMANCE);
        assertThat(lot.get(0).indice()).isEqualByComparingTo("15.00");
        assertThat(lot.get(1).signaux()).isEmpty();
    }

    @Test
    void sans_source_declaree_les_faits_importes_ne_levent_rien() {
        VigilanceService sansSource = new VigilanceService(scoreRepository, questionnaireRepository,
                trimestreRepository, collaborateurRepository, performanceRepository, potentielRepository,
                new CalculService(parametreRepository, performanceRepository, potentielRepository),
                new DefaultListableBeanFactory().getBeanProvider(FaitsVigilanceSource.class));
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        preparerLotSansPrecedent(List.of(score(collaborateur, trimestre, "70.00")));

        assertThat(sansSource.evaluerTrimestre(trimestre).get(0).signaux()).isEmpty();
    }

    @Test
    void une_source_qui_rend_null_vaut_aucun_fait() {
        VigilanceService sourceMuette = new VigilanceService(scoreRepository, questionnaireRepository,
                trimestreRepository, collaborateurRepository, performanceRepository, potentielRepository,
                new CalculService(parametreRepository, performanceRepository, potentielRepository),
                trimestreDemande -> null);
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        preparerLotSansPrecedent(List.of(score(collaborateur, trimestre, "70.00")));

        assertThat(sourceMuette.evaluerTrimestre(trimestre).get(0).signaux()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(SignalVigilance.class)
    void chaque_signal_a_desormais_une_source(SignalVigilance signal) {
        assertThat(signal.estDetectable()).isTrue();
    }

    // --- evaluation ----------------------------------------------------------

    @Test
    void l_evaluation_rend_l_indice_le_niveau_et_les_signaux() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        ResultatVigilance resultat = vigilanceService.evaluer(collaborateur,
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.SANS_MOBILITE_4_ANS),
                parametre);

        assertThat(resultat.indice()).isEqualByComparingTo("45.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.MODEREE);
        assertThat(resultat.signaux()).containsExactlyInAnyOrder(
                SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.SANS_MOBILITE_4_ANS);
        assertThat(resultat.estARisque()).isTrue();
        assertThat(resultat.collaborateur()).isSameAs(collaborateur);
    }

    @Test
    void le_resultat_ne_bouge_pas_si_l_appelant_reutilise_son_ensemble_de_signaux() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        Set<SignalVigilance> signaux = EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE);

        ResultatVigilance resultat = vigilanceService.evaluer(collaborateur, signaux, parametre);
        signaux.add(SignalVigilance.SANS_MOBILITE_4_ANS);

        assertThat(resultat.signaux()).containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
        assertThat(resultat.indice()).isEqualByComparingTo("25.00");
    }

    @Test
    void un_collaborateur_sans_signal_est_a_vigilance_faible() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        ResultatVigilance resultat = vigilanceService.evaluer(
                collaborateur, EnumSet.noneOf(SignalVigilance.class), parametre);

        assertThat(resultat.indice()).isEqualByComparingTo("0.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.FAIBLE);
        assertThat(resultat.estARisque()).isFalse();
    }

    @Test
    void des_signaux_peuvent_etre_fournis_a_la_main() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        // 25 + 20 + 15 = 60 : au seuil eleve.
        ResultatVigilance resultat = vigilanceService.evaluer(collaborateur,
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE,
                        SignalVigilance.SANS_MOBILITE_4_ANS,
                        SignalVigilance.MOBILITE_NON_TRAITEE),
                parametre);

        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.ELEVEE);
    }

    @Test
    void sans_faits_importes_la_detection_plafonne_a_l_engagement_et_la_baisse() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        // Engagement et baisse mesuree valent 25 + 10 = 35, sous le seuil de 60.
        Set<SignalVigilance> signaux = vigilanceService.detecterSignaux(
                score(collaborateur, trimestre, "60.00"), score(collaborateur, trimestrePrecedent, "70.00"),
                engagement(collaborateur, "10.00"), parametre.getSeuilsVigilance());

        ResultatVigilance resultat = vigilanceService.evaluer(collaborateur, signaux, parametre);

        assertThat(resultat.indice()).isEqualByComparingTo("35.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.MODEREE);
    }

    @Test
    void l_evaluation_d_un_collaborateur_charge_les_donnees_du_trimestre() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any()))
                .thenReturn(List.of(trimestrePrecedent));
        when(scoreRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre))
                .thenReturn(Optional.of(score(collaborateur, trimestre, "60.00")));
        when(scoreRepository.findByCollaborateurAndTrimestre(collaborateur, trimestrePrecedent))
                .thenReturn(Optional.of(score(collaborateur, trimestrePrecedent, "70.00")));
        when(questionnaireRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre))
                .thenReturn(Optional.of(engagement(collaborateur, "20.00")));

        ResultatVigilance resultat = vigilanceService.evaluer(collaborateur, trimestre);

        assertThat(resultat.signaux()).containsExactlyInAnyOrder(
                SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.BAISSE_PERFORMANCE);
        assertThat(resultat.indice()).isEqualByComparingTo("35.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.MODEREE);
    }

    @Test
    void un_trimestre_sans_parametre_est_signale() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vigilanceService.evaluer(collaborateur, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // --- lot du trimestre ----------------------------------------------------

    @Test
    void le_lot_va_du_plus_a_risque_au_moins_a_risque() {
        Collaborateur aRisque = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur serein = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any()))
                .thenReturn(List.of(trimestrePrecedent));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestrePrecedent)).thenReturn(
                List.of(score(aRisque, trimestrePrecedent, "80.00"),
                        score(serein, trimestrePrecedent, "60.00")));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(
                List.of(score(serein, trimestre, "70.00"), score(aRisque, trimestre, "50.00")));
        population(List.of(score(serein, trimestre, "70.00"), score(aRisque, trimestre, "50.00")));
        when(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre))
                .thenReturn(List.of(engagement(aRisque, "20.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot).extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E001", "E002");
        assertThat(lot.get(0).indice()).isEqualByComparingTo("35.00");
        assertThat(lot.get(1).indice()).isEqualByComparingTo("0.00");
    }

    /** Les scores d'inactifs ou d'archives ne font pas entrer leur titulaire dans la liste. */
    @Test
    void le_lot_ecarte_les_collaborateurs_hors_perimetre() {
        Collaborateur actif = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur inactif = collaborateur("E002", StatutCollaborateur.INACTIF);
        Collaborateur archive = collaborateur("E003", StatutCollaborateur.ARCHIVE);

        preparerLotSansPrecedent(List.of(score(actif, trimestre, "70.00"),
                score(inactif, trimestre, "70.00"), score(archive, trimestre, "70.00")));

        assertThat(vigilanceService.evaluerTrimestre(trimestre))
                .extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E001");
    }

    @Test
    void un_actif_avec_un_questionnaire_mais_sans_score_est_dans_le_lot() {
        Collaborateur score = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur sansScore = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any())).thenReturn(List.of());
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre))
                .thenReturn(List.of(score(score, trimestre, "70.00")));
        when(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre))
                .thenReturn(List.of(engagement(sansScore, "20.00")));
        when(collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF))
                .thenReturn(List.of(score, sansScore));
        when(performanceRepository.findMatriculesEvaluesDuTrimestre(trimestre, MANAGER)).thenReturn(List.of("E001"));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot).extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E002", "E001");
        assertThat(lot.get(0).signaux()).containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
        assertThat(lot.get(0).indice()).isEqualByComparingTo("25.00");
    }

    @Test
    void des_faits_declares_suffisent_sans_score_ni_questionnaire() {
        Collaborateur declare = collaborateur("E001", StatutCollaborateur.ACTIF);
        faits.declarer("E001", FaitsVigilance.builder().sansMobilite4Ans(true).baissePerformance(true).build());

        preparerTrimestreSansPrecedent(List.of(), List.of());
        when(collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF)).thenReturn(List.of(declare));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        // Sans score precedent ni courant, la baisse vient du drapeau importe.
        assertThat(lot).singleElement().satisfies(resultat -> {
            assertThat(resultat.signaux()).containsExactlyInAnyOrder(
                    SignalVigilance.SANS_MOBILITE_4_ANS, SignalVigilance.BAISSE_PERFORMANCE);
            assertThat(resultat.indice()).isEqualByComparingTo("30.00");
        });
    }

    @Test
    void un_actif_note_en_potentiel_seulement_est_dans_le_lot() {
        Collaborateur potentielSeul = collaborateur("E001", StatutCollaborateur.ACTIF);

        preparerTrimestreSansPrecedent(List.of(), List.of());
        when(collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF))
                .thenReturn(List.of(potentielSeul));
        when(potentielRepository.findMatriculesEvaluesDuTrimestre(trimestre, MANAGER)).thenReturn(List.of("E001"));

        assertThat(vigilanceService.evaluerTrimestre(trimestre))
                .extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E001");
    }

    /**
     * Ni questionnaire (ou un questionnaire sans score), ni faits, ni notes :
     * pas d'indice, comme dans les vues (EntreesVigilance), plutot qu'un 0 qui
     * ne dit rien du risque.
     */
    @Test
    void un_actif_sans_aucune_donnee_n_est_pas_dans_le_lot() {
        Collaborateur note = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur muet = collaborateur("E002", StatutCollaborateur.ACTIF);
        Collaborateur questionnaireVide = collaborateur("E003", StatutCollaborateur.ACTIF);

        preparerTrimestreSansPrecedent(List.of(score(note, trimestre, "70.00")),
                List.of(engagement(questionnaireVide, null)));
        when(collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF))
                .thenReturn(List.of(note, muet, questionnaireVide));
        when(performanceRepository.findMatriculesEvaluesDuTrimestre(trimestre, MANAGER)).thenReturn(List.of("E001"));

        assertThat(vigilanceService.evaluerTrimestre(trimestre))
                .extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E001");
    }

    @Test
    void sans_trimestre_precedent_le_lot_ne_leve_aucune_baisse() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF);

        preparerLotSansPrecedent(List.of(score(collaborateur, trimestre, "10.00")));

        assertThat(vigilanceService.evaluerTrimestre(trimestre).get(0).signaux()).isEmpty();
    }

    @Test
    void a_egalite_d_indice_l_id_collaborateur_departage() {
        Collaborateur premier = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur second = collaborateur("E002", StatutCollaborateur.ACTIF);

        preparerLotSansPrecedent(List.of(score(second, trimestre, "70.00"),
                score(premier, trimestre, "70.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot).extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E001", "E002");
        assertThat(lot.get(0).indice()).isEqualByComparingTo(lot.get(1).indice());
    }

    @Test
    void l_engagement_d_un_collaborateur_ne_profite_pas_a_un_autre() {
        Collaborateur repondant = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur muet = collaborateur("E002", StatutCollaborateur.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any())).thenReturn(List.of());
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(
                List.of(score(repondant, trimestre, "70.00"), score(muet, trimestre, "70.00")));
        population(List.of(score(repondant, trimestre, "70.00"), score(muet, trimestre, "70.00")));
        when(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre))
                .thenReturn(List.of(engagement(repondant, "10.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot.get(0).collaborateur().getIdCollaborateur()).isEqualTo("E001");
        assertThat(lot.get(0).signaux()).containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
        assertThat(lot.get(1).signaux()).isEmpty();
    }

    @Test
    void le_lot_filtre_sur_un_niveau_minimum() {
        Collaborateur aRisque = collaborateur("E001", StatutCollaborateur.ACTIF);
        Collaborateur serein = collaborateur("E002", StatutCollaborateur.ACTIF);

        // Engagement faible (25) + baisse de performance (10) = 35, soit MODEREE.
        // L'engagement seul resterait a 25, sous le seuil de 30.
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any()))
                .thenReturn(List.of(trimestrePrecedent));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestrePrecedent))
                .thenReturn(List.of(score(aRisque, trimestrePrecedent, "80.00")));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(
                List.of(score(aRisque, trimestre, "70.00"), score(serein, trimestre, "70.00")));
        population(List.of(score(aRisque, trimestre, "70.00"), score(serein, trimestre, "70.00")));
        when(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre))
                .thenReturn(List.of(engagement(aRisque, "10.00")));

        assertThat(vigilanceService.evaluerTrimestre(trimestre, NiveauVigilance.MODEREE))
                .extracting(resultat -> resultat.collaborateur().getIdCollaborateur())
                .containsExactly("E001");
        // Sans faits importes, ELEVEE reste hors d'atteinte.
        assertThat(vigilanceService.evaluerTrimestre(trimestre, NiveauVigilance.ELEVEE)).isEmpty();
    }

    private void preparerLotSansPrecedent(List<Score> scores) {
        preparerTrimestreSansPrecedent(scores, List.of());
        population(scores);
    }

    /** Reglages, pas de trimestre precedent, scores et questionnaires du trimestre ; population a poser. */
    private void preparerTrimestreSansPrecedent(List<Score> scores, List<QuestionnaireEngagement> engagements) {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any())).thenReturn(List.of());
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(scores);
        when(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(engagements);
    }

    /**
     * Population du lot telle que la base la rendrait : les actifs parmi les
     * collaborateurs des scores, tous notes en performance sur le trimestre.
     */
    private void population(List<Score> scoresDuTrimestre) {
        when(collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF)).thenReturn(
                scoresDuTrimestre.stream().map(Score::getCollaborateur).filter(Collaborateur::estCalculable).toList());
        when(performanceRepository.findMatriculesEvaluesDuTrimestre(trimestre, MANAGER)).thenReturn(
                scoresDuTrimestre.stream().map(score -> score.getCollaborateur().getIdCollaborateur()).toList());
    }
}
