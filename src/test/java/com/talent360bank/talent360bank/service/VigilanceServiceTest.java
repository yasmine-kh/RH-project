package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    private VigilanceService vigilanceService;

    private Trimestre trimestre;
    private Trimestre trimestrePrecedent;
    private Parametre parametre;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        vigilanceService = new VigilanceService(scoreRepository, questionnaireRepository,
                trimestreRepository, calculService);

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

    private Employe employe(String employeeId, StatutEmploye statut) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
        employe.setDateEntree(LocalDate.of(2020, 1, 15));
        employe.setStatut(statut);
        return employe;
    }

    private Score score(Employe employe, Trimestre trimestreDuScore, String performance) {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestreDuScore);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal("70.00"));
        return score;
    }

    private QuestionnaireEngagement engagement(Employe employe, String scoreEngagement) {
        QuestionnaireEngagement questionnaire = new QuestionnaireEngagement();
        questionnaire.setEmploye(employe);
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
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(null, null,
                engagement(employe, "59.99"), parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
    }

    @Test
    void un_engagement_au_seuil_ne_leve_pas_le_signal() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(null, null,
                engagement(employe, "60.00"), parametre.getSeuilsVigilance()))
                .isEmpty();
    }

    @Test
    void le_seuil_d_engagement_vient_du_parametre_pas_du_code() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        QuestionnaireEngagement questionnaire = engagement(employe, "55.00");

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
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        parametre.getSeuilsVigilance().setSeuilEngagementFaible(null);

        assertThatThrownBy(() -> vigilanceService.detecterSignaux(null, null,
                engagement(employe, "10.00"), parametre.getSeuilsVigilance()))
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
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(null, null, engagement(employe, null), parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void un_recul_de_performance_leve_le_signal() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(employe, trimestre, "69.99"),
                score(employe, trimestrePrecedent, "70.00"),
                null, parametre.getSeuilsVigilance()))
                .containsExactly(SignalVigilance.BAISSE_PERFORMANCE);
    }

    @Test
    void une_performance_stable_ou_en_hausse_ne_leve_pas_le_signal() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(employe, trimestre, "70.00"),
                score(employe, trimestrePrecedent, "70.00"), null, parametre.getSeuilsVigilance())).isEmpty();
        assertThat(vigilanceService.detecterSignaux(
                score(employe, trimestre, "80.00"),
                score(employe, trimestrePrecedent, "70.00"), null, parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void sans_trimestre_precedent_la_baisse_est_indetectable() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(employe, trimestre, "10.00"), null, null, parametre.getSeuilsVigilance())).isEmpty();
    }

    @Test
    void les_deux_signaux_detectables_peuvent_se_cumuler() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        assertThat(vigilanceService.detecterSignaux(
                score(employe, trimestre, "60.00"),
                score(employe, trimestrePrecedent, "70.00"),
                engagement(employe, "30.00"), parametre.getSeuilsVigilance()))
                .containsExactlyInAnyOrder(SignalVigilance.ENGAGEMENT_FAIBLE,
                        SignalVigilance.BAISSE_PERFORMANCE);
    }

    @Test
    void la_detection_automatique_ne_leve_que_les_signaux_declares_detectables() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        Set<SignalVigilance> signaux = vigilanceService.detecterSignaux(
                score(employe, trimestre, "60.00"),
                score(employe, trimestrePrecedent, "70.00"),
                engagement(employe, "10.00"), parametre.getSeuilsVigilance());

        assertThat(signaux).allMatch(SignalVigilance::estDetectable);
    }

    @ParameterizedTest
    @EnumSource(SignalVigilance.class)
    void chaque_signal_sait_lire_ses_points(SignalVigilance signal) {
        assertThat(signal.pointsDans(parametre.getPointsVigilance())).isNotNull();
        assertThat(signal.getLibelle()).isNotBlank();
    }

    // --- evaluation ----------------------------------------------------------

    @Test
    void l_evaluation_rend_l_indice_le_niveau_et_les_signaux() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        ResultatVigilance resultat = vigilanceService.evaluer(employe,
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.SANS_MOBILITE_4_ANS),
                parametre);

        assertThat(resultat.indice()).isEqualByComparingTo("45.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.MODEREE);
        assertThat(resultat.signaux()).containsExactlyInAnyOrder(
                SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.SANS_MOBILITE_4_ANS);
        assertThat(resultat.estARisque()).isTrue();
        assertThat(resultat.employe()).isSameAs(employe);
    }

    @Test
    void le_resultat_ne_bouge_pas_si_l_appelant_reutilise_son_ensemble_de_signaux() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        Set<SignalVigilance> signaux = EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE);

        ResultatVigilance resultat = vigilanceService.evaluer(employe, signaux, parametre);
        signaux.add(SignalVigilance.SANS_MOBILITE_4_ANS);

        assertThat(resultat.signaux()).containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
        assertThat(resultat.indice()).isEqualByComparingTo("25.00");
    }

    @Test
    void un_employe_sans_signal_est_a_vigilance_faible() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        ResultatVigilance resultat = vigilanceService.evaluer(
                employe, EnumSet.noneOf(SignalVigilance.class), parametre);

        assertThat(resultat.indice()).isEqualByComparingTo("0.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.FAIBLE);
        assertThat(resultat.estARisque()).isFalse();
    }

    @Test
    void les_signaux_non_detectables_peuvent_etre_fournis_a_la_main() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        // 25 + 20 + 15 = 60 : le niveau ELEVEE n'est atteignable qu'ainsi
        // tant que mobilite et developpement ne sont pas modelises.
        ResultatVigilance resultat = vigilanceService.evaluer(employe,
                EnumSet.of(SignalVigilance.ENGAGEMENT_FAIBLE,
                        SignalVigilance.SANS_MOBILITE_4_ANS,
                        SignalVigilance.MOBILITE_NON_TRAITEE),
                parametre);

        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.ELEVEE);
    }

    @Test
    void la_detection_automatique_seule_ne_peut_pas_atteindre_le_niveau_eleve() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        // Les deux seuls signaux detectables valent 25 + 10 = 35, sous le seuil de 60.
        Set<SignalVigilance> detectables = EnumSet.of(
                SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.BAISSE_PERFORMANCE);

        ResultatVigilance resultat = vigilanceService.evaluer(employe, detectables, parametre);

        assertThat(resultat.indice()).isEqualByComparingTo("35.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.MODEREE);
    }

    @Test
    void l_evaluation_d_un_employe_charge_les_donnees_du_trimestre() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any()))
                .thenReturn(List.of(trimestrePrecedent));
        when(scoreRepository.findByEmployeAndTrimestre(employe, trimestre))
                .thenReturn(Optional.of(score(employe, trimestre, "60.00")));
        when(scoreRepository.findByEmployeAndTrimestre(employe, trimestrePrecedent))
                .thenReturn(Optional.of(score(employe, trimestrePrecedent, "70.00")));
        when(questionnaireRepository.findByEmployeAndTrimestre(employe, trimestre))
                .thenReturn(Optional.of(engagement(employe, "20.00")));

        ResultatVigilance resultat = vigilanceService.evaluer(employe, trimestre);

        assertThat(resultat.signaux()).containsExactlyInAnyOrder(
                SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.BAISSE_PERFORMANCE);
        assertThat(resultat.indice()).isEqualByComparingTo("35.00");
        assertThat(resultat.niveau()).isEqualTo(NiveauVigilance.MODEREE);
    }

    @Test
    void un_trimestre_sans_parametre_est_signale() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vigilanceService.evaluer(employe, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // --- lot du trimestre ----------------------------------------------------

    @Test
    void le_lot_va_du_plus_a_risque_au_moins_a_risque() {
        Employe aRisque = employe("E001", StatutEmploye.ACTIF);
        Employe serein = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any()))
                .thenReturn(List.of(trimestrePrecedent));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestrePrecedent)).thenReturn(
                List.of(score(aRisque, trimestrePrecedent, "80.00"),
                        score(serein, trimestrePrecedent, "60.00")));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(
                List.of(score(serein, trimestre, "70.00"), score(aRisque, trimestre, "50.00")));
        when(questionnaireRepository.findByTrimestreAvecEmploye(trimestre))
                .thenReturn(List.of(engagement(aRisque, "20.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot).extracting(resultat -> resultat.employe().getEmployeeId())
                .containsExactly("E001", "E002");
        assertThat(lot.get(0).indice()).isEqualByComparingTo("35.00");
        assertThat(lot.get(1).indice()).isEqualByComparingTo("0.00");
    }

    @Test
    void le_lot_ecarte_les_employes_hors_perimetre() {
        Employe actif = employe("E001", StatutEmploye.ACTIF);
        Employe inactif = employe("E002", StatutEmploye.INACTIF);
        Employe archive = employe("E003", StatutEmploye.ARCHIVE);

        preparerLotSansPrecedent(List.of(score(actif, trimestre, "70.00"),
                score(inactif, trimestre, "70.00"), score(archive, trimestre, "70.00")));

        assertThat(vigilanceService.evaluerTrimestre(trimestre))
                .extracting(resultat -> resultat.employe().getEmployeeId())
                .containsExactly("E001");
    }

    @Test
    void sans_trimestre_precedent_le_lot_ne_leve_aucune_baisse() {
        Employe employe = employe("E001", StatutEmploye.ACTIF);

        preparerLotSansPrecedent(List.of(score(employe, trimestre, "10.00")));

        assertThat(vigilanceService.evaluerTrimestre(trimestre).get(0).signaux()).isEmpty();
    }

    @Test
    void a_egalite_d_indice_l_employee_id_departage() {
        Employe premier = employe("E001", StatutEmploye.ACTIF);
        Employe second = employe("E002", StatutEmploye.ACTIF);

        preparerLotSansPrecedent(List.of(score(second, trimestre, "70.00"),
                score(premier, trimestre, "70.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot).extracting(resultat -> resultat.employe().getEmployeeId())
                .containsExactly("E001", "E002");
        assertThat(lot.get(0).indice()).isEqualByComparingTo(lot.get(1).indice());
    }

    @Test
    void l_engagement_d_un_employe_ne_profite_pas_a_un_autre() {
        Employe repondant = employe("E001", StatutEmploye.ACTIF);
        Employe muet = employe("E002", StatutEmploye.ACTIF);

        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any())).thenReturn(List.of());
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(
                List.of(score(repondant, trimestre, "70.00"), score(muet, trimestre, "70.00")));
        when(questionnaireRepository.findByTrimestreAvecEmploye(trimestre))
                .thenReturn(List.of(engagement(repondant, "10.00")));

        List<ResultatVigilance> lot = vigilanceService.evaluerTrimestre(trimestre);

        assertThat(lot.get(0).employe().getEmployeeId()).isEqualTo("E001");
        assertThat(lot.get(0).signaux()).containsExactly(SignalVigilance.ENGAGEMENT_FAIBLE);
        assertThat(lot.get(1).signaux()).isEmpty();
    }

    @Test
    void le_lot_filtre_sur_un_niveau_minimum() {
        Employe aRisque = employe("E001", StatutEmploye.ACTIF);
        Employe serein = employe("E002", StatutEmploye.ACTIF);

        // Engagement faible (25) + baisse de performance (10) = 35, soit MODEREE.
        // L'engagement seul resterait a 25, sous le seuil de 30.
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any()))
                .thenReturn(List.of(trimestrePrecedent));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestrePrecedent))
                .thenReturn(List.of(score(aRisque, trimestrePrecedent, "80.00")));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(
                List.of(score(aRisque, trimestre, "70.00"), score(serein, trimestre, "70.00")));
        when(questionnaireRepository.findByTrimestreAvecEmploye(trimestre))
                .thenReturn(List.of(engagement(aRisque, "10.00")));

        assertThat(vigilanceService.evaluerTrimestre(trimestre, NiveauVigilance.MODEREE))
                .extracting(resultat -> resultat.employe().getEmployeeId())
                .containsExactly("E001");
        // Plafond de la detection automatique : ELEVEE reste hors d'atteinte.
        assertThat(vigilanceService.evaluerTrimestre(trimestre, NiveauVigilance.ELEVEE)).isEmpty();
    }

    private void preparerLotSansPrecedent(List<Score> scores) {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(trimestreRepository.findPrecedents(eq(2026), eq(2), any())).thenReturn(List.of());
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(scores);
        when(questionnaireRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(List.of());
    }
}
