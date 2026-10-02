package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.BaremeCompetences;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.EcartExigence;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
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

import static com.talent360bank.talent360bank.entity.SourceEvaluation.MANAGER;
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
    private CompetenceCollaborateurRepository competenceCollaborateurRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;

    private SuccessionService successionService;

    private Trimestre trimestre;

    /** Date de reference du trimestre de test : toutes les anciennetes y sont mesurees. */
    private static final LocalDate REFERENCE = LocalDate.of(2026, 3, 31);
    private Parametre parametre;
    private Competence competenceA;
    private Competence competenceB;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(
                parametreRepository, performanceRepository, potentielRepository);
        successionService = new SuccessionService(posteRepository, scoreRepository,
                potentielRepository, competenceCollaborateurRepository, calculService);

        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        trimestre.setDateReference(REFERENCE);

        parametre = Parametre.parDefaut(trimestre);

        competenceA = competence("C001", "Analyse de risque");
        competenceB = competence("C002", "Management d'equipe");
    }

    private BaremeCompetences bareme() {
        return parametre.getBaremeCompetences();
    }

    private Competence competence(String id, String nom) {
        Competence competence = new Competence();
        competence.setCompetenceId(id);
        competence.setNom(nom);
        return competence;
    }

    private Collaborateur collaborateur(String idCollaborateur, StatutCollaborateur statut, int anneesAnciennete) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(idCollaborateur);
        collaborateur.setNom("Nom" + idCollaborateur);
        collaborateur.setPrenom("Prenom" + idCollaborateur);
        collaborateur.setDateEntree(REFERENCE.minusYears(anneesAnciennete));
        collaborateur.setStatut(statut);
        return collaborateur;
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

    private CompetenceCollaborateur skill(Collaborateur collaborateur, Competence competence, Integer niveauActuel) {
        CompetenceCollaborateur competenceCollaborateur = new CompetenceCollaborateur();
        competenceCollaborateur.setCollaborateur(collaborateur);
        competenceCollaborateur.setCompetence(competence);
        competenceCollaborateur.setNiveauActuel(niveauActuel);
        return competenceCollaborateur;
    }

    private Score score(Collaborateur collaborateur, String performance, String potentiel) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setTrimestre(trimestre);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(potentiel == null ? null : new BigDecimal(potentiel));
        return score;
    }

    private Potentiel potentiel(Collaborateur collaborateur, String leadership, String mobilite) {
        Potentiel potentiel = new Potentiel();
        potentiel.setCollaborateur(collaborateur);
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

    // --- plus grand gap (09_SUCCESSION!M) ---------------------------------------

    @Test
    void le_plus_grand_gap_est_la_competence_qui_manque_le_plus_de_niveaux() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // C001 : 3 sur 4 exige (1 niveau) ; C002 : 1 sur 3 exige (2 niveaux).
        PlusGrandGap gap = successionService.plusGrandGap(poste("P001"), List.of(
                skill(candidat, competenceA, 3), skill(candidat, competenceB, 1)), bareme());

        assertThat(gap).isEqualTo(new PlusGrandGap("C002", "Management d'equipe", 3, 1, 2));
        assertThat(gap.aUnEcart()).isTrue();
    }

    @Test
    void a_egalite_le_plus_grand_gap_est_la_premiere_competence_du_poste() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // Un niveau manquant de chaque cote : MATCH du classeur garde la premiere.
        PlusGrandGap gap = successionService.plusGrandGap(poste("P001"), List.of(
                skill(candidat, competenceA, 3), skill(candidat, competenceB, 2)), bareme());

        assertThat(gap.competenceId()).isEqualTo("C001");
        assertThat(gap.ecart()).isEqualTo(1);
    }

    @Test
    void sans_ecart_le_plus_grand_gap_est_la_premiere_competence_avec_un_ecart_nul() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // Comme le classeur (BP019 sur PST01 -> "Leadership") : un niveau au-dessus de
        // l'exigence ne donne pas d'ecart negatif qui ferait gagner une autre competence.
        PlusGrandGap gap = successionService.plusGrandGap(poste("P001"), List.of(
                skill(candidat, competenceA, 5), skill(candidat, competenceB, 3)), bareme());

        assertThat(gap.competenceId()).isEqualTo("C001");
        assertThat(gap.ecart()).isZero();
        assertThat(gap.aUnEcart()).isFalse();
    }

    @Test
    void une_competence_absente_compte_au_niveau_par_defaut_dans_le_plus_grand_gap() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // C001 absente, supposee au niveau 3 (bareme) pour 4 exige : 1 niveau.
        PlusGrandGap gap = successionService.plusGrandGap(poste("P001"), List.of(
                skill(candidat, competenceB, 3)), bareme());

        assertThat(gap).isEqualTo(new PlusGrandGap("C001", "Analyse de risque", 4, 3, 1));
    }

    @Test
    void les_ecarts_par_exigence_suivent_l_ordre_du_poste_avec_le_niveau_par_defaut() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // C001 absente (niveau par defaut 3) sur 4 exige ; C002 a 5 sur 3 exige : pas d'ecart negatif.
        assertThat(successionService.ecartsExigences(poste("P001"), List.of(skill(candidat, competenceB, 5)),
                bareme())).containsExactly(
                new EcartExigence("C001", "Analyse de risque", 4, 3, 1),
                new EcartExigence("C002", "Management d'equipe", 3, 5, 0));
    }

    @Test
    void un_poste_sans_competence_chiffree_n_a_pas_de_plus_grand_gap() {
        Poste poste = new Poste();
        poste.setPosteId("P002");

        assertThat(successionService.plusGrandGap(poste, List.of(), bareme())).isNull();
    }

    @Test
    void le_matching_porte_le_plus_grand_gap_sans_changer_son_score() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        List<CompetenceCollaborateur> competences = List.of(
                skill(candidat, competenceA, 2), skill(candidat, competenceB, 3));

        ResultatMatching resultat = successionService.evaluer(candidat, poste("P001"),
                score(candidat, "80.00", "75.00"), potentiel(candidat, "70", "60"), competences, parametre,
                REFERENCE);

        assertThat(resultat.plusGrandGap()).isEqualTo(new PlusGrandGap("C001", "Analyse de risque", 4, 2, 2));
        assertThat(resultat.detail().competences()).isEqualByComparingTo(
                successionService.scoreCompetences(poste("P001"), competences, bareme()));
    }

    // --- couverture des competences ------------------------------------------

    @Test
    void competences_au_niveau_exige_donnent_cent() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 4),
                skill(candidat, competenceB, 3)), bareme()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void un_niveau_superieur_a_l_exigence_ne_rapporte_pas_de_bonus() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // C001 a 8 pour un niveau 4 exige : plafonne a 100, il ne compense pas
        // C002 a 1 sur 3 exige = 100 - 20 x 2 = 60. Moyenne 80.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 8),
                skill(candidat, competenceB, 1)), bareme()))
                .isEqualByComparingTo("80.00");
    }

    @Test
    void une_competence_absente_est_supposee_au_niveau_par_defaut() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // Comme IFERROR(..., 3) dans 09_SUCCESSION : C001 a 2 sur 4 exige = 60,
        // C002 absente supposee au niveau 3 pour 3 exige = 100. Moyenne 80.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 2)), bareme()))
                .isEqualByComparingTo("80.00");
    }

    @Test
    void le_niveau_par_defaut_vient_du_parametre_pas_du_code() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        bareme().setNiveauParDefaut(1);

        // C001 au niveau = 100, C002 absente supposee au niveau 1 pour 3 exige = 60.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 4)), bareme()))
                .isEqualByComparingTo("80.00");
    }

    @Test
    void les_competences_refusent_un_niveau_par_defaut_non_configure() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        bareme().setNiveauParDefaut(null);

        assertThatThrownBy(() -> successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 4)), bareme()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void chaque_niveau_manquant_retire_vingt_points() {
        // BP058 sur PST08 dans 09_SUCCESSION : exigences 4/3/3/3/3, niveaux 3/3/3/1/2
        // -> 80, 100, 100, 60, 80 : moyenne 84.
        Competence conformite = competence("C11", "Conformite");
        Competence juridique = competence("C12", "Juridique bancaire");
        Competence audit = competence("C13", "Audit");
        Competence management = competence("C14", "Management d'equipe");
        Competence decision = competence("C15", "Prise de decision");

        Poste pst08 = new Poste();
        pst08.setPosteId("PST08");
        pst08.setCompetenceRequise1(conformite);
        pst08.setNiveau1(4);
        pst08.setCompetenceRequise2(juridique);
        pst08.setNiveau2(3);
        pst08.setCompetenceRequise3(audit);
        pst08.setNiveau3(3);
        pst08.setCompetenceRequise4(management);
        pst08.setNiveau4(3);
        pst08.setCompetenceRequise5(decision);
        pst08.setNiveau5(3);

        Collaborateur bp058 = collaborateur("BP058", StatutCollaborateur.ACTIF, 5);

        assertThat(successionService.scoreCompetences(pst08, List.of(
                skill(bp058, conformite, 3),
                skill(bp058, juridique, 3),
                skill(bp058, audit, 3),
                skill(bp058, management, 1),
                skill(bp058, decision, 2)), bareme()))
                .isEqualByComparingTo("84.00");
    }

    @Test
    void une_competence_tres_en_dessous_ne_descend_pas_sous_zero() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        bareme().setPointsParNiveauManquant(new BigDecimal("40"));

        // C001 a 1 sur 4 exige : 100 - 40 x 3 = -20, ramene a 0 ; C002 au niveau = 100.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 1),
                skill(candidat, competenceB, 3)), bareme()))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void les_points_par_niveau_manquant_viennent_du_parametre_pas_du_code() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        bareme().setPointsParNiveauManquant(new BigDecimal("25"));

        // C001 a 2 sur 4 exige : 100 - 25 x 2 = 50 ; C002 au niveau = 100.
        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 2),
                skill(candidat, competenceB, 3)), bareme()))
                .isEqualByComparingTo("75.00");
    }

    @Test
    void les_competences_refusent_un_bareme_non_configure() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        bareme().setPointsParNiveauManquant(null);

        assertThatThrownBy(() -> successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, competenceA, 4)), bareme()))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    @Test
    void le_rapprochement_se_fait_par_identifiant_pas_par_libelle() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);

        // Meme identifiant C001, libelle different : la competence doit etre reconnue.
        Competence memeIdAutreLibelle = competence("C001", "Analyse des risques (v2)");

        assertThat(successionService.scoreCompetences(poste("P001"), List.of(
                skill(candidat, memeIdAutreLibelle, 4),
                skill(candidat, competenceB, 3)), bareme()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void un_poste_sans_competence_exigee_rend_le_critere_non_applicable() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        Poste poste = new Poste();
        poste.setPosteId("P002");

        assertThat(successionService.scoreCompetences(poste, List.of(
                skill(candidat, competenceA, 4)), bareme())).isNull();
    }

    @Test
    void une_exigence_sans_niveau_chiffre_est_ignoree() {
        Poste poste = new Poste();
        poste.setPosteId("P003");
        poste.setCompetenceRequise1(competenceA);
        poste.setNiveau1(null);

        assertThat(successionService.scoreCompetences(poste, List.of(), bareme())).isNull();
    }

    // --- experience ----------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "0, 0.00",
            "5, 40.00",
            "12, 96.00",
            "13, 100.00",   // 104 plafonne a 100
            "20, 100.00"
    })
    void experience_vaut_huit_points_par_annee_plafonnes_a_cent(int annees, String attendu) {
        assertThat(successionService.scoreExperience(
                collaborateur("E001", StatutCollaborateur.ACTIF, annees), parametre.getBaremeExperience(), REFERENCE))
                .isEqualByComparingTo(attendu);
    }

    @Test
    void experience_compte_l_anciennete_au_dixieme_d_annee() {
        // 1205 jours / 365.25 = 3.299 -> 3.3 ans, comme 01_COLLABORATEURS ; 3.3 x 8 = 26.4
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 0);
        candidat.setDateEntree(REFERENCE.minusDays(1205));

        assertThat(successionService.scoreExperience(candidat, parametre.getBaremeExperience(), REFERENCE))
                .isEqualByComparingTo("26.40");
    }

    @Test
    void le_bareme_d_experience_vient_du_parametre_pas_du_code() {
        parametre.getBaremeExperience().setPointsParAnnee(new BigDecimal("10"));
        parametre.getBaremeExperience().setPlafond(new BigDecimal("90"));

        assertThat(successionService.scoreExperience(
                collaborateur("E001", StatutCollaborateur.ACTIF, 5), parametre.getBaremeExperience(), REFERENCE))
                .isEqualByComparingTo("50.00");
        assertThat(successionService.scoreExperience(
                collaborateur("E001", StatutCollaborateur.ACTIF, 12), parametre.getBaremeExperience(), REFERENCE))
                .isEqualByComparingTo("90.00");
    }

    @Test
    void experience_est_non_applicable_sans_date_d_entree() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        candidat.setDateEntree(null);

        assertThat(successionService.scoreExperience(candidat, parametre.getBaremeExperience(), REFERENCE))
                .isNull();
    }

    @Test
    void experience_refuse_un_bareme_non_configure() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 5);
        parametre.getBaremeExperience().setPointsParAnnee(null);

        assertThatThrownBy(() -> successionService.scoreExperience(
                candidat, parametre.getBaremeExperience(), REFERENCE))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    // --- date de reference (C4) : jamais la date du jour ----------------------

    /**
     * Dates choisies loin d'aujourd'hui, dans les deux sens : mesuree a la date
     * du jour, l'anciennete serait tout autre (environ 11 ans dans le premier
     * cas, negative donc 0 point dans le second).
     */
    @ParameterizedTest
    @CsvSource({
            "2015-06-30, 2020-06-30, 40.00",   // 5.0 ans dans le passe
            "2030-12-31, 2040-12-31, 80.00"    // 10.0 ans dans le futur
    })
    void l_experience_se_mesure_a_la_date_de_reference_pas_a_aujourd_hui(LocalDate entree, LocalDate reference,
                                                                         String attendu) {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 0);
        candidat.setDateEntree(entree);

        assertThat(successionService.scoreExperience(candidat, parametre.getBaremeExperience(), reference))
                .isEqualByComparingTo(attendu);
    }

    @Test
    void le_classement_mesure_l_anciennete_a_la_date_de_reference_du_trimestre() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 0);
        candidat.setDateEntree(LocalDate.of(2010, 1, 1));
        preparerClassement(List.of(score(candidat, "90.00", "80.00")), List.of(), List.of());

        // 2010-01-01 -> 2015-01-01 : 1826 jours / 365.25 = 5.0 ans -> 40 points.
        trimestre.setDateReference(LocalDate.of(2015, 1, 1));
        assertThat(successionService.classerCandidats("P001", trimestre).get(0).detail().experience())
                .isEqualByComparingTo("40.00");

        // Meme trimestre, date de reference changee par le RH : 2010 -> 2012, 2.0 ans -> 16 points.
        trimestre.setDateReference(LocalDate.of(2012, 1, 1));
        assertThat(successionService.classerCandidats("P001", trimestre).get(0).detail().experience())
                .isEqualByComparingTo("16.00");
    }

    @Test
    void sans_date_de_reference_enregistree_le_trimestre_vaut_son_dernier_jour() {
        Trimestre t3 = new Trimestre();
        t3.setAnnee(2026);
        t3.setNumero(3);
        assertThat(t3.getDateReference()).isEqualTo(LocalDate.of(2026, 9, 30));

        for (int numero = 1; numero <= 4; numero++) {
            assertThat(Trimestre.dernierJour(2024, numero)).isEqualTo(
                    List.of(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 6, 30), LocalDate.of(2024, 9, 30),
                            LocalDate.of(2024, 12, 31)).get(numero - 1));
        }
    }

    @Test
    void l_anciennete_d_un_collaborateur_se_mesure_a_la_date_donnee() {
        Collaborateur collaborateur = collaborateur("E001", StatutCollaborateur.ACTIF, 0);
        collaborateur.setDateEntree(LocalDate.of(2015, 6, 30));

        assertThat(collaborateur.getAnciennete(LocalDate.of(2020, 6, 29))).isEqualTo(4);
        assertThat(collaborateur.getAnciennete(LocalDate.of(2020, 6, 30))).isEqualTo(5);
        assertThat(collaborateur.getAnciennete(null)).isNull();
    }

    // --- score de matching ---------------------------------------------------

    @Test
    void le_matching_applique_les_six_poids_du_parametre() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 13);
        Poste poste = poste("P001");

        // competences 100, performance 90, potentiel 80, experience 100,
        // leadership 70, mobilite 60
        // (100*25 + 90*20 + 80*20 + 100*15 + 70*10 + 60*10) / 100 = 87.00
        ResultatMatching resultat = successionService.evaluer(candidat, poste,
                score(candidat, "90.00", "80.00"),
                potentiel(candidat, "70.00", "60.00"),
                List.of(skill(candidat, competenceA, 4), skill(candidat, competenceB, 3)),
                parametre, REFERENCE);

        assertThat(resultat.scoreMatching()).isEqualByComparingTo("87.00");
        assertThat(resultat.readiness()).isEqualTo(NiveauReadiness.MOINS_1_AN);
        assertThat(resultat.candidat()).isSameAs(candidat);
    }

    @Test
    void le_detail_des_six_criteres_est_restitue() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 13);

        ResultatMatching resultat = successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"),
                potentiel(candidat, "70.00", "60.00"),
                List.of(skill(candidat, competenceA, 4), skill(candidat, competenceB, 3)),
                parametre, REFERENCE);

        assertThat(resultat.detail().competences()).isEqualByComparingTo("100.00");
        assertThat(resultat.detail().performance()).isEqualByComparingTo("90.00");
        assertThat(resultat.detail().potentiel()).isEqualByComparingTo("80.00");
        assertThat(resultat.detail().experience()).isEqualByComparingTo("100.00");
        assertThat(resultat.detail().leadership()).isEqualByComparingTo("70.00");
        assertThat(resultat.detail().mobilite()).isEqualByComparingTo("60.00");
    }

    @Test
    void un_critere_non_evaluable_compte_pour_zero_sans_redistribuer_son_poids() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 13);

        // Sans Potentiel : leadership et mobilite comptent pour zero, comme une
        // cellule vide dans 09_SUCCESSION.
        // (100*25 + 90*20 + 80*20 + 100*15 + 0*10 + 0*10) / 100 = 74.00
        ResultatMatching resultat = successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"),
                null,
                List.of(skill(candidat, competenceA, 4), skill(candidat, competenceB, 3)),
                parametre, REFERENCE);

        assertThat(resultat.scoreMatching()).isEqualByComparingTo("74.00");
        assertThat(resultat.readiness()).isEqualTo(NiveauReadiness.ENTRE_1_ET_2_ANS);
        assertThat(resultat.detail().leadership()).isNull();
        assertThat(resultat.detail().mobilite()).isNull();
    }

    @Test
    void un_poste_sans_competence_exigee_compte_zero_au_critere_competences() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 13);
        Poste poste = new Poste();
        poste.setPosteId("P002");

        // (0*25 + 90*20 + 80*20 + 100*15 + 70*10 + 60*10) / 100 = 62.00
        ResultatMatching resultat = successionService.evaluer(candidat, poste,
                score(candidat, "90.00", "80.00"),
                potentiel(candidat, "70.00", "60.00"),
                List.of(),
                parametre, REFERENCE);

        assertThat(resultat.scoreMatching()).isEqualByComparingTo("62.00");
    }

    @Test
    void le_matching_refuse_des_poids_non_configures() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        parametre.setPoidsSuccession(null);

        assertThatThrownBy(() -> successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"), null, List.of(), parametre, REFERENCE))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("poids");
    }

    @Test
    void le_matching_refuse_un_poids_manquant_dans_le_bloc() {
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        PoidsSuccession poids = parametre.getPoidsSuccession();
        poids.setPoidsExperience(null);

        assertThatThrownBy(() -> successionService.evaluer(candidat, poste("P001"),
                score(candidat, "90.00", "80.00"), null, List.of(), parametre, REFERENCE))
                .isInstanceOf(DonneesIncompletesException.class)
                .hasMessageContaining("experience");
    }

    // --- classement ----------------------------------------------------------

    @Test
    void le_classement_va_du_meilleur_matching_au_moins_bon() {
        Collaborateur fort = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        Collaborateur faible = collaborateur("E002", StatutCollaborateur.ACTIF, 1);

        preparerClassement(
                List.of(score(fort, "95.00", "90.00"), score(faible, "60.00", "55.00")),
                List.of(potentiel(fort, "90.00", "85.00"), potentiel(faible, "50.00", "40.00")),
                List.of(skill(fort, competenceA, 4), skill(fort, competenceB, 3),
                        skill(faible, competenceA, 1)));

        List<ResultatMatching> classement = successionService.classerCandidats("P001", trimestre);

        assertThat(classement).extracting(resultat -> resultat.candidat().getIdCollaborateur())
                .containsExactly("E001", "E002");
        assertThat(classement.get(0).scoreMatching())
                .isGreaterThan(classement.get(1).scoreMatching());
    }

    @Test
    void le_titulaire_du_poste_n_est_pas_son_propre_successeur() {
        Collaborateur titulaire = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        Collaborateur autre = collaborateur("E002", StatutCollaborateur.ACTIF, 10);

        Poste poste = poste("P001");
        poste.setTitulaireId("E001");

        when(posteRepository.findByIdAvecCompetences("P001")).thenReturn(Optional.of(poste));
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(
                List.of(score(titulaire, "95.00", "90.00"), score(autre, "70.00", "70.00")));
        when(potentielRepository.findByTrimestreAvecCollaborateur(trimestre, MANAGER)).thenReturn(List.of());
        when(competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(anyCollection()))
                .thenReturn(List.of());

        assertThat(successionService.classerCandidats("P001", trimestre))
                .extracting(resultat -> resultat.candidat().getIdCollaborateur())
                .containsExactly("E002");
    }

    @Test
    void les_collaborateurs_hors_perimetre_et_les_scores_incomplets_sont_ecartes() {
        Collaborateur actif = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        Collaborateur inactif = collaborateur("E002", StatutCollaborateur.INACTIF, 10);
        Collaborateur archive = collaborateur("E003", StatutCollaborateur.ARCHIVE, 10);
        Collaborateur incomplet = collaborateur("E004", StatutCollaborateur.ACTIF, 10);

        preparerClassement(
                List.of(score(actif, "90.00", "80.00"),
                        score(inactif, "95.00", "95.00"),
                        score(archive, "99.00", "99.00"),
                        score(incomplet, null, "80.00")),
                List.of(),
                List.of());

        assertThat(successionService.classerCandidats("P001", trimestre))
                .extracting(resultat -> resultat.candidat().getIdCollaborateur())
                .containsExactly("E001");
    }

    @Test
    void a_egalite_de_score_l_id_collaborateur_departage() {
        Collaborateur premier = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        Collaborateur second = collaborateur("E002", StatutCollaborateur.ACTIF, 10);

        // Memes notes des deux cotes : seul l'identifiant peut trancher.
        preparerClassement(
                List.of(score(second, "90.00", "80.00"), score(premier, "90.00", "80.00")),
                List.of(potentiel(second, "70.00", "60.00"), potentiel(premier, "70.00", "60.00")),
                List.of(skill(premier, competenceA, 4), skill(premier, competenceB, 3),
                        skill(second, competenceA, 4), skill(second, competenceB, 3)));

        List<ResultatMatching> classement = successionService.classerCandidats("P001", trimestre);

        assertThat(classement).extracting(resultat -> resultat.candidat().getIdCollaborateur())
                .containsExactly("E001", "E002");
        assertThat(classement.get(0).scoreMatching())
                .isEqualByComparingTo(classement.get(1).scoreMatching());
    }

    @Test
    void les_competences_d_un_candidat_ne_profitent_pas_a_un_autre() {
        Collaborateur avecCompetences = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        Collaborateur sansCompetences = collaborateur("E002", StatutCollaborateur.ACTIF, 10);

        preparerClassement(
                List.of(score(avecCompetences, "90.00", "80.00"),
                        score(sansCompetences, "90.00", "80.00")),
                List.of(),
                List.of(skill(avecCompetences, competenceA, 4),
                        skill(avecCompetences, competenceB, 3)));

        List<ResultatMatching> classement = successionService.classerCandidats("P001", trimestre);

        assertThat(classement.get(0).candidat().getIdCollaborateur()).isEqualTo("E001");
        assertThat(classement.get(0).detail().competences()).isEqualByComparingTo("100.00");
        // E002 n'a rien : niveau par defaut 3 partout, C001 = 80 et C002 = 100.
        // Avec les competences de E001, il aurait 100.
        assertThat(classement.get(1).detail().competences()).isEqualByComparingTo("90.00");
    }

    @Test
    void la_short_list_ne_rend_que_les_meilleurs() {
        Collaborateur fort = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        Collaborateur moyen = collaborateur("E002", StatutCollaborateur.ACTIF, 5);
        Collaborateur faible = collaborateur("E003", StatutCollaborateur.ACTIF, 1);

        preparerClassement(
                List.of(score(fort, "95.00", "95.00"), score(moyen, "75.00", "75.00"),
                        score(faible, "50.00", "50.00")),
                List.of(),
                List.of());

        assertThat(successionService.classerCandidats("P001", trimestre, 2))
                .extracting(resultat -> resultat.candidat().getIdCollaborateur())
                .containsExactly("E001", "E002");
    }

    @Test
    void la_short_list_tolere_une_limite_superieure_au_nombre_de_candidats() {
        Collaborateur seul = collaborateur("E001", StatutCollaborateur.ACTIF, 10);

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
        Collaborateur candidat = collaborateur("E001", StatutCollaborateur.ACTIF, 10);
        when(posteRepository.findByIdAvecCompetences("P001")).thenReturn(Optional.of(poste("P001")));
        when(scoreRepository.findByCollaborateurAndTrimestre(candidat, trimestre))
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
                                    List<CompetenceCollaborateur> competences) {
        when(posteRepository.findByIdAvecCompetences("P001")).thenReturn(Optional.of(poste("P001")));
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre)).thenReturn(new ArrayList<>(scores));
        when(potentielRepository.findByTrimestreAvecCollaborateur(trimestre, MANAGER)).thenReturn(potentiels);
        when(competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(anyCollection()))
                .thenReturn(competences);
    }
}
