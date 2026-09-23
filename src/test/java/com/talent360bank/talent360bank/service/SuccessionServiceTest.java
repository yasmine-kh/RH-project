package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.NiveauReadiness;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuccessionServiceTest {

    @Mock
    private PosteRepository posteRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private PotentielRepository potentielRepository;
    @Mock
    private EmployeeSkillRepository employeeSkillRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;

    private SuccessionService successionService;

    private Trimestre trimestre;
    private Parametre parametre;
    private Competence competenceA;
    private Competence competenceB;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        successionService = new SuccessionService(posteRepository, scoreRepository,
                potentielRepository, employeeSkillRepository, calculService);

        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        parametre = Parametre.parDefaut(trimestre);

        competenceA = competence("C001", "Analyse de risque");
        competenceB = competence("C002", "Management d'equipe");
    }

    private Competence competence(String id, String nom) {
        Competence competence = new Competence();
        competence.setCompetenceId(id);
        competence.setNom(nom);
        return competence;
    }

    private Employe employe(String employeeId, StatutEmploye statut, int anneesAnciennete) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
        employe.setDateEntree(LocalDate.now().minusYears(anneesAnciennete));
        employe.setStatut(statut);
        return employe;
    }

    /** Poste exigeant C001 au niveau 4 et C002 au niveau 3. */
    private Poste poste(String posteId) {
        Poste poste = new Poste();
        poste.setPosteId(posteId);
        poste.setNomPoste("Directeur d'agence");
        poste.setCompetenceRequise1(competenceA);
        poste.setNiveau1(4);
        poste.setCompetenceRequise2(competenceB);
        poste.setNiveau2(3);
        return poste;
    }

    private EmployeeSkill skill(Employe employe, Competence competence, Integer niveauActuel) {
        EmployeeSkill employeeSkill = new EmployeeSkill();
        employeeSkill.setEmploye(employe);
        employeeSkill.setCompetence(competence);
        employeeSkill.setNiveauActuel(niveauActuel);
        return employeeSkill;
    }

    private Score score(Employe employe, String performance, String potentiel) {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(potentiel == null ? null : new BigDecimal(potentiel));
        return score;
    }

    private Potentiel potentiel(Employe employe, String leadership, String mobilite) {
        Potentiel potentiel = new Potentiel();
        potentiel.setEmploye(employe);
        potentiel.setTrimestre(trimestre);
        potentiel.setNoteLeadership(new BigDecimal(leadership));
        potentiel.setNoteMobilite(new BigDecimal(mobilite));
        return potentiel;
    }

    // --- readiness -----------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "100.00, READY_NOW",
            "90.00, READY_NOW",          // exactement au seuil
            "89.99, MOINS_1_AN",
            "80.00, MOINS_1_AN",         // exactement au seuil
            "79.99, ENTRE_1_ET_2_ANS",
            "65.00, ENTRE_1_ET_2_ANS",   // exactement au seuil
            "64.99, PLUS_2_ANS",
            "0.00, PLUS_2_ANS"
    })
    void readiness_suit_les_seuils_bornes_inclusives(String scoreMatching, NiveauReadiness attendu) {
        assertThat(successionService.readinessPour(
                new BigDecimal(scoreMatching), parametre.getSeuilsReadiness())).isEqualTo(attendu);
    }

    @Test
    void readiness_refuse_un_score_absent() {
        assertThatThrownBy(() -> successionService.readinessPour(null, parametre.getSeuilsReadiness()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void readiness_refuse_des_seuils_non_configures() {
        parametre.getSeuilsReadiness().setSeuilReadyNow(null);

        assertThatThrownBy(() -> successionService.readinessPour(
                new BigDecimal("90.00"), parametre.getSeuilsReadiness()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    // --- couverture des competences ------------------------------------------

    @Test
    void competences_au_niveau_exige_donnent_cent() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 5);

        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 4),
                skill(candidat, competenceB, 3))))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void un_niveau_superieur_a_l_exigence_ne_rapporte_pas_de_bonus() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 5);

        // C001 a 8 pour un niveau 4 exige : plafonne a 100, il ne compense pas C002 a 0.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 8))))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void une_competence_absente_compte_pour_zero() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 5);

        // C001 a 2 sur 4 exige = 50, C002 absente = 0, moyenne 25.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 2))))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void le_rapprochement_se_fait_par_identifiant_pas_par_libelle() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 5);

        // Meme identifiant C001, libelle different : la competence doit etre reconnue.
        Competence memeIdAutreLibelle = competence("C001", "Analyse des risques (v2)");

        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, memeIdAutreLibelle, 4),
                skill(candidat, competenceB, 3))))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void un_poste_sans_competence_exigee_rend_le_critere_non_applicable() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 5);
        Poste poste = new Poste();
        poste.setPosteId("P002");

        assertThat(successionService.scoreCompetences(poste, List.of(
                skill(candidat, competenceA, 4)))).isNull();
    }

    @Test
    void une_exigence_sans_niveau_chiffre_est_ignoree() {
        Poste poste = new Poste();
        poste.setPosteId("P003");
        poste.setCompetenceRequise1(competenceA);
        poste.setNiveau1(null);

        assertThat(successionService.scoreCompetences(poste, List.of())).isNull();
    }

    // --- experience ----------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "0, 0.00",
            "5, 50.00",
            "10, 100.00",
            "15, 100.00"    // plafonne a la reference de dix ans
    })
    void experience_est_l_anciennete_ramenee_sur_cent(int annees, String attendu) {
        assertThat(successionService.scoreExperience(employe("E001", StatutEmploye.ACTIF, annees)))
                .isEqualByComparingTo(attendu);
    }

    @Test
    void experience_est_non_applicable_sans_date_d_entree() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 5);
        candidat.setDateEntree(null);

        assertThat(successionService.scoreExperience(candidat)).isNull();
    }

    // --- score de matching ---------------------------------------------------

    @Test
    void le_matching_applique_les_six_poids_du_parametre() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);
        Poste poste = poste("P001");

        // competences 100, performance 90, potentiel 80, experience 100,
        // leadership 70, mobilite 60
        // (100*25 + 90*20 + 80*20 + 100*15 + 70*10 + 60*10) / 100 = 87.00
        ResultatMatching resultat = successionService.evaluer(candidat, poste,
                score(candidat, "90.00", "80.00"),
                potentiel(candidat, "70.00", "60.00"),
                List.of(skill(candidat, competenceA, 4), skill(candidat, competenceB, 3)),
                parametre);

        assertThat(resultat.scoreMatching()).isEqualByComparingTo("87.00");
        assertThat(resultat.readiness()).isEqualTo(NiveauReadiness.MOINS_1_AN);
        assertThat(resultat.candidat()).isSameAs(candidat);
    }

    @Test
    void le_detail_des_six_criteres_est_restitue() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);

        ResultatMatching resultat = successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"),
                potentiel(candidat, "70.00", "60.00"),
                List.of(skill(candidat, competenceA, 4), skill(candidat, competenceB, 3)),
                parametre);

        assertThat(resultat.detail().competences()).isEqualByComparingTo("100.00");
        assertThat(resultat.detail().performance()).isEqualByComparingTo("90.00");
        assertThat(resultat.detail().potentiel()).isEqualByComparingTo("80.00");
        assertThat(resultat.detail().experience()).isEqualByComparingTo("100.00");
        assertThat(resultat.detail().leadership()).isEqualByComparingTo("70.00");
        assertThat(resultat.detail().mobilite()).isEqualByComparingTo("60.00");
    }

    @Test
    void un_critere_non_evaluable_est_ecarte_et_non_compte_pour_zero() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);

        // Sans Potentiel : leadership et mobilite sortent de la moyenne.
        // (100*25 + 90*20 + 80*20 + 100*15) / (25+20+20+15) = 7400 / 80 = 92.50
        ResultatMatching resultat = successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"),
                null,
                List.of(skill(candidat, competenceA, 4), skill(candidat, competenceB, 3)),
                parametre);

        assertThat(resultat.scoreMatching()).isEqualByComparingTo("92.50");
        assertThat(resultat.readiness()).isEqualTo(NiveauReadiness.READY_NOW);
        assertThat(resultat.detail().leadership()).isNull();
        assertThat(resultat.detail().mobilite()).isNull();
    }

    @Test
    void un_poste_sans_competence_exigee_repartit_le_poids_sur_les_autres_criteres() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);
        Poste poste = new Poste();
        poste.setPosteId("P002");

        // (90*20 + 80*20 + 100*15 + 70*10 + 60*10) / (20+20+15+10+10) = 6200 / 75 = 82.67
        ResultatMatching resultat = successionService.evaluer(candidat, poste,
                score(candidat, "90.00", "80.00"),
                potentiel(candidat, "70.00", "60.00"),
                List.of(),
                parametre);

        assertThat(resultat.scoreMatching()).isEqualByComparingTo("82.67");
    }

    @Test
    void le_matching_refuse_des_poids_non_configures() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);
        parametre.setPoidsSuccession(null);

        assertThatThrownBy(() -> successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"), null, List.of(), parametre))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("poids");
    }

    @Test
    void le_matching_refuse_un_poids_manquant_dans_le_bloc() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);
        PoidsSuccession poids = parametre.getPoidsSuccession();
        poids.setPoidsExperience(null);

        assertThatThrownBy(() -> successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"), null, List.of(), parametre))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("experience");
    }

    // --- classement ----------------------------------------------------------

    @Test
    void le_classement_va_du_meilleur_matching_au_moins_bon() {
        Employe fort = employe("E001", StatutEmploye.ACTIF, 10);
        Employe faible = employe("E002", StatutEmploye.ACTIF, 1);

        preparerClassement(
                List.of(score(fort, "95.00", "90.00"), score(faible, "60.00", "55.00")),
                List.of(potentiel(fort, "90.00", "85.00"), potentiel(faible, "50.00", "40.00")),
                List.of(skill(fort, competenceA, 4), skill(fort, competenceB, 3),
                        skill(faible, competenceA, 1)));

        List<ResultatMatching> classement = successionService.classerCandidats("P001", trimestre);

        assertThat(classement).extracting(resultat -> resultat.candidat().getEmployeeId())
                .containsExactly("E001", "E002");
        assertThat(classement.get(0).scoreMatching())
                .isGreaterThan(classement.get(1).scoreMatching());
    }

    @Test
    void le_titulaire_du_poste_n_est_pas_son_propre_successeur() {
        Employe titulaire = employe("E001", StatutEmploye.ACTIF, 10);
        Employe autre = employe("E002", StatutEmploye.ACTIF, 10);

        Poste poste = poste("P001");
        poste.setTitulaireId("E001");

        when(posteRepository.findByIdAvecCompetences("P001")).thenReturn(Optional.of(poste));
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(
                List.of(score(titulaire, "95.00", "90.00"), score(autre, "70.00", "70.00")));
        when(potentielRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(List.of());
        when(employeeSkillRepository.findByEmployeIdsAvecCompetence(anyCollection()))
                .thenReturn(List.of());

        assertThat(successionService.classerCandidats("P001", trimestre))
                .extracting(resultat -> resultat.candidat().getEmployeeId())
                .containsExactly("E002");
    }

    @Test
    void les_employes_hors_perimetre_et_les_scores_incomplets_sont_ecartes() {
        Employe actif = employe("E001", StatutEmploye.ACTIF, 10);
        Employe inactif = employe("E002", StatutEmploye.INACTIF, 10);
        Employe archive = employe("E003", StatutEmploye.ARCHIVE, 10);
        Employe incomplet = employe("E004", StatutEmploye.ACTIF, 10);

        preparerClassement(
                List.of(score(actif, "90.00", "80.00"),
                        score(inactif, "95.00", "95.00"),
                        score(archive, "99.00", "99.00"),
                        score(incomplet, null, "80.00")),
                List.of(),
                List.of());

        assertThat(successionService.classerCandidats("P001", trimestre))
                .extracting(resultat -> resultat.candidat().getEmployeeId())
                .containsExactly("E001");
    }

    @Test
    void a_egalite_de_score_l_employee_id_departage() {
        Employe premier = employe("E001", StatutEmploye.ACTIF, 10);
        Employe second = employe("E002", StatutEmploye.ACTIF, 10);

        // Memes notes des deux cotes : seul l'identifiant peut trancher.
        preparerClassement(
                List.of(score(second, "90.00", "80.00"), score(premier, "90.00", "80.00")),
                List.of(potentiel(second, "70.00", "60.00"), potentiel(premier, "70.00", "60.00")),
                List.of(skill(premier, competenceA, 4), skill(premier, competenceB, 3),
                        skill(second, competenceA, 4), skill(second, competenceB, 3)));

        List<ResultatMatching> classement = successionService.classerCandidats("P001", trimestre);

        assertThat(classement).extracting(resultat -> resultat.candidat().getEmployeeId())
                .containsExactly("E001", "E002");
        assertThat(classement.get(0).scoreMatching())
                .isEqualByComparingTo(classement.get(1).scoreMatching());
    }

    @Test
    void les_competences_d_un_candidat_ne_profitent_pas_a_un_autre() {
        Employe avecCompetences = employe("E001", StatutEmploye.ACTIF, 10);
        Employe sansCompetences = employe("E002", StatutEmploye.ACTIF, 10);

        preparerClassement(
                List.of(score(avecCompetences, "90.00", "80.00"),
                        score(sansCompetences, "90.00", "80.00")),
                List.of(),
                List.of(skill(avecCompetences, competenceA, 4),
                        skill(avecCompetences, competenceB, 3)));

        List<ResultatMatching> classement = successionService.classerCandidats("P001", trimestre);

        assertThat(classement.get(0).candidat().getEmployeeId()).isEqualTo("E001");
        assertThat(classement.get(0).detail().competences()).isEqualByComparingTo("100.00");
        assertThat(classement.get(1).detail().competences()).isEqualByComparingTo("0.00");
    }

    @Test
    void la_short_list_ne_rend_que_les_meilleurs() {
        Employe fort = employe("E001", StatutEmploye.ACTIF, 10);
        Employe moyen = employe("E002", StatutEmploye.ACTIF, 5);
        Employe faible = employe("E003", StatutEmploye.ACTIF, 1);

        preparerClassement(
                List.of(score(fort, "95.00", "95.00"), score(moyen, "75.00", "75.00"),
                        score(faible, "50.00", "50.00")),
                List.of(),
                List.of());

        assertThat(successionService.classerCandidats("P001", trimestre, 2))
                .extracting(resultat -> resultat.candidat().getEmployeeId())
                .containsExactly("E001", "E002");
    }

    @Test
    void la_short_list_tolere_une_limite_superieure_au_nombre_de_candidats() {
        Employe seul = employe("E001", StatutEmploye.ACTIF, 10);

        preparerClassement(List.of(score(seul, "90.00", "80.00")), List.of(), List.of());

        assertThat(successionService.classerCandidats("P001", trimestre, 10)).hasSize(1);
    }

    @Test
    void un_poste_inconnu_est_signale() {
        when(posteRepository.findByIdAvecCompetences("INCONNU")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> successionService.classerCandidats("INCONNU", trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("INCONNU");
    }

    @Test
    void un_candidat_sans_score_sur_le_trimestre_est_signale() {
        Employe candidat = employe("E001", StatutEmploye.ACTIF, 10);
        when(posteRepository.findByIdAvecCompetences("P001")).thenReturn(Optional.of(poste("P001")));
        when(scoreRepository.findByEmployeAndTrimestre(candidat, trimestre))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> successionService.evaluer(candidat, "P001", trimestre))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("E001");
    }

    @Test
    void la_limite_negative_est_refusee() {
        assertThatThrownBy(() -> successionService.classerCandidats("P001", trimestre, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void preparerClassement(List<Score> scores, List<Potentiel> potentiels,
                                    List<EmployeeSkill> competences) {
        when(posteRepository.findByIdAvecCompetences("P001")).thenReturn(Optional.of(poste("P001")));
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(new ArrayList<>(scores));
        when(potentielRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(potentiels);
        when(employeeSkillRepository.findByEmployeIdsAvecCompetence(anyCollection()))
                .thenReturn(competences);
    }
}
