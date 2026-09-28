package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosteCritiqueServiceTest {

    @Mock
    private PosteRepository posteRepository;
    @Mock
    private EmployeRepository employeRepository;
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
    private CalculService calculService;
    private SuccesseursIdentifiesEnMemoire successeurs;
    private PosteCritiqueService service;

    private Trimestre trimestre;
    private Parametre parametre;
    private Competence competence;

    @BeforeEach
    void init() {
        calculService = new CalculService(parametreRepository, performanceRepository, potentielRepository);
        successionService = new SuccessionService(posteRepository, scoreRepository,
                potentielRepository, employeeSkillRepository, calculService);
        successeurs = new SuccesseursIdentifiesEnMemoire();
        service = new PosteCritiqueService(posteRepository, employeRepository, scoreRepository,
                potentielRepository, employeeSkillRepository, successionService, calculService, successeurs);

        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        parametre = Parametre.parDefaut(trimestre);

        competence = new Competence();
        competence.setCompetenceId("C001");
        competence.setNom("Cybersecurite");
    }

    // --- niveau de couverture ------------------------------------------------

    @ParameterizedTest(name = "{0} successeur(s), meilleur matching {1} -> {2}")
    @CsvSource({
            "0, , ALERTE",
            "0, 95, ALERTE",
            "1, 95, READY_NOW",
            "1, 90, READY_NOW",
            "1, 89.99, MOINS_1_AN",
            "1, 80, MOINS_1_AN",
            "1, 79.99, PARTIELLE",
            "3, 40, PARTIELLE",
            "2, , PARTIELLE"
    })
    void le_niveau_de_couverture_suit_les_seuils_du_parametre(int nb, BigDecimal meilleur,
                                                              NiveauCouverture attendu) {
        assertThat(service.couverturePour(nb, meilleur, parametre)).isEqualTo(attendu);
    }

    @Test
    void le_minimum_de_successeurs_vient_du_parametre() {
        parametre.getSeuilsCouverture().setNbMinSuccesseurs(2);

        assertThat(service.couverturePour(1, new BigDecimal("95"), parametre)).isEqualTo(NiveauCouverture.ALERTE);
        assertThat(service.couverturePour(2, new BigDecimal("95"), parametre)).isEqualTo(NiveauCouverture.READY_NOW);
    }

    @Test
    void les_seuils_de_couverture_suivent_ceux_de_readiness() {
        parametre.getSeuilsReadiness().setSeuilReadyNow(new BigDecimal("95"));

        assertThat(service.couverturePour(1, new BigDecimal("92"), parametre))
                .isEqualTo(NiveauCouverture.MOINS_1_AN);
    }

    @Test
    void sans_seuil_de_couverture_le_calcul_echoue() {
        parametre.setSeuilsCouverture(null);

        assertThatThrownBy(() -> service.couverturePour(1, new BigDecimal("95"), parametre))
                .isInstanceOf(DonneesIncompletesException.class);
    }

    // --- evaluation d'un poste -----------------------------------------------

    @Test
    void le_meilleur_successeur_fixe_la_couverture_et_le_classement_est_decroissant() {
        Employe fort = employe("BP010", StatutEmploye.ACTIF);
        Employe faible = employe("BP020", StatutEmploye.ACTIF);

        CouverturePoste couverture = service.evaluerCouverture(poste("PST13", "Oui", "BP075"),
                List.of("BP020", "BP010"), index(fort, faible),
                scores(score(fort, "95"), score(faible, "60")),
                potentiels(potentiel(fort, "95"), potentiel(faible, "60")),
                Map.of("BP010", List.of(skill(fort, 5)), "BP020", List.of(skill(faible, 3))), parametre);

        assertThat(couverture.nbSuccesseurs()).isEqualTo(2);
        assertThat(couverture.successeurs()).extracting(r -> r.candidat().getEmployeeId())
                .containsExactly("BP010", "BP020");
        assertThat(couverture.meilleurMatching())
                .isEqualByComparingTo(couverture.successeurs().get(0).scoreMatching());
        assertThat(couverture.niveau()).isEqualTo(NiveauCouverture.READY_NOW);
        assertThat(couverture.ignores()).isEmpty();
    }

    @Test
    void sans_successeur_identifie_le_poste_est_en_alerte() {
        CouverturePoste couverture = service.evaluerCouverture(poste("PST13", "Oui", "BP075"), List.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), parametre);

        assertThat(couverture.nbSuccesseurs()).isZero();
        assertThat(couverture.estEnAlerte()).isTrue();
        assertThat(couverture.meilleurSuccesseur()).isNull();
        assertThat(couverture.meilleurMatching()).isNull();
    }

    @Test
    void titulaire_inconnu_et_hors_perimetre_ne_couvrent_pas_le_poste() {
        Employe titulaire = employe("BP075", StatutEmploye.ACTIF);
        Employe parti = employe("BP030", StatutEmploye.ARCHIVE);

        CouverturePoste couverture = service.evaluerCouverture(poste("PST13", "Oui", "BP075"),
                List.of("BP075", "BP999", "BP030"), index(titulaire, parti),
                scores(score(titulaire, "95"), score(parti, "95")), Map.of(), Map.of(), parametre);

        assertThat(couverture.nbSuccesseurs()).isZero();
        assertThat(couverture.estEnAlerte()).isTrue();
        assertThat(couverture.ignores()).extracting(CouverturePoste.SuccesseurIgnore::employeeId)
                .containsExactly("BP075", "BP999", "BP030");
    }

    @Test
    void un_successeur_sans_score_compte_mais_ne_fixe_pas_le_matching() {
        Employe nouveau = employe("BP040", StatutEmploye.ACTIF);

        CouverturePoste couverture = service.evaluerCouverture(poste("PST13", "Oui", "BP075"),
                List.of("BP040"), index(nouveau), Map.of(), Map.of(), Map.of(), parametre);

        assertThat(couverture.nbSuccesseurs()).isEqualTo(1);
        assertThat(couverture.successeurs()).isEmpty();
        assertThat(couverture.niveau()).isEqualTo(NiveauCouverture.PARTIELLE);
        assertThat(couverture.ignores()).extracting(CouverturePoste.SuccesseurIgnore::motif)
                .containsExactly("Score absent ou incomplet sur le trimestre");
    }

    @Test
    void un_successeur_identifie_deux_fois_ne_compte_qu_une_fois() {
        Employe candidat = employe("BP010", StatutEmploye.ACTIF);

        CouverturePoste couverture = service.evaluerCouverture(poste("PST13", "Oui", "BP075"),
                List.of("BP010", "BP010"), index(candidat), scores(score(candidat, "95")),
                Map.of(), Map.of(), parametre);

        assertThat(couverture.nbSuccesseurs()).isEqualTo(1);
        assertThat(couverture.successeurs()).hasSize(1);
    }

    // --- taux de couverture --------------------------------------------------

    @Test
    void le_taux_de_couverture_est_la_part_des_postes_hors_alerte() {
        CouverturePoste couvert = couverture(NiveauCouverture.READY_NOW);
        CouverturePoste partiel = couverture(NiveauCouverture.PARTIELLE);
        CouverturePoste alerte = couverture(NiveauCouverture.ALERTE);

        assertThat(service.tauxCouverture(List.of(couvert, partiel, alerte))).isEqualByComparingTo("66.67");
        assertThat(service.tauxCouverture(List.of(couvert))).isEqualByComparingTo("100");
        assertThat(service.tauxCouverture(List.of())).isNull();
    }

    // --- acces base ----------------------------------------------------------

    @Test
    void seuls_les_postes_indiques_critiques_sont_suivis_et_les_alertes_detectees() {
        Employe candidat = employe("BP010", StatutEmploye.ACTIF);
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(posteRepository.findAll()).thenReturn(List.of(
                poste("PST02", "oui ", "BP001"), poste("PST01", "Oui", "BP002"), poste("PST04", "Non", "BP003")));
        successeurs.identifier("PST01", "BP010");
        when(employeRepository.findAllById(any())).thenReturn(List.of(candidat));
        when(employeeSkillRepository.findByEmployeIdsAvecCompetence(any())).thenReturn(List.of(skill(candidat, 5)));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(List.of(score(candidat, "95")));
        when(potentielRepository.findByTrimestreAvecEmploye(trimestre))
                .thenReturn(List.of(potentiel(candidat, "95")));

        List<CouverturePoste> couvertures = service.listerPostesCritiques(trimestre);

        assertThat(couvertures).extracting(c -> c.poste().getPosteId()).containsExactly("PST01", "PST02");
        assertThat(couvertures.get(0).nbSuccesseurs()).isEqualTo(1);
        assertThat(couvertures.get(0).niveau()).isEqualTo(NiveauCouverture.READY_NOW);
        assertThat(couvertures.get(1).estEnAlerte()).isTrue();
        assertThat(service.detecterAlertes(trimestre)).extracting(c -> c.poste().getPosteId())
                .containsExactly("PST02");
    }

    @Test
    void un_poste_non_critique_n_a_pas_de_couverture() {
        when(posteRepository.findByIdAvecCompetences("PST04"))
                .thenReturn(Optional.of(poste("PST04", "Non", "BP003")));

        assertThatThrownBy(() -> service.evaluerCouverture("PST04", trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void sans_source_declaree_aucun_successeur_n_est_identifie() {
        PosteCritiqueService sansSource = new PosteCritiqueService(posteRepository, employeRepository,
                scoreRepository, potentielRepository, employeeSkillRepository, successionService, calculService,
                new StaticListableBeanFactory().getBeanProvider(SuccesseurIdentifieSource.class));
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(posteRepository.findAll()).thenReturn(List.of(poste("PST01", "Oui", "BP002")));

        assertThat(sansSource.detecterAlertes(trimestre)).hasSize(1);
    }

    // --- outils --------------------------------------------------------------

    private Poste poste(String posteId, String critique, String titulaireId) {
        Poste poste = new Poste();
        poste.setPosteId(posteId);
        poste.setNomPoste("Poste " + posteId);
        poste.setPosteCritique(critique);
        poste.setTitulaireId(titulaireId);
        poste.setCompetenceRequise1(competence);
        poste.setNiveau1(5);
        return poste;
    }

    private Employe employe(String employeeId, StatutEmploye statut) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
        employe.setDateEntree(LocalDate.now().minusYears(15));
        employe.setStatut(statut);
        return employe;
    }

    private Score score(Employe employe, String valeur) {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal(valeur));
        score.setScorePotentiel(new BigDecimal(valeur));
        return score;
    }

    private Potentiel potentiel(Employe employe, String valeur) {
        Potentiel potentiel = new Potentiel();
        potentiel.setEmploye(employe);
        potentiel.setTrimestre(trimestre);
        potentiel.setNoteLeadership(new BigDecimal(valeur));
        potentiel.setNoteMobilite(new BigDecimal(valeur));
        return potentiel;
    }

    private EmployeeSkill skill(Employe employe, int niveau) {
        EmployeeSkill skill = new EmployeeSkill();
        skill.setEmploye(employe);
        skill.setCompetence(competence);
        skill.setNiveauActuel(niveau);
        return skill;
    }

    private CouverturePoste couverture(NiveauCouverture niveau) {
        return new CouverturePoste(poste("PST01", "Oui", "BP001"), 0, List.of(), List.of(), niveau);
    }

    private static Map<String, Employe> index(Employe... employes) {
        Map<String, Employe> index = new HashMap<>();
        for (Employe employe : employes) {
            index.put(employe.getEmployeeId(), employe);
        }
        return index;
    }

    private static Map<String, Score> scores(Score... scores) {
        Map<String, Score> index = new HashMap<>();
        for (Score score : scores) {
            index.put(score.getEmploye().getEmployeeId(), score);
        }
        return index;
    }

    private static Map<String, Potentiel> potentiels(Potentiel... potentiels) {
        Map<String, Potentiel> index = new HashMap<>();
        for (Potentiel potentiel : potentiels) {
            index.put(potentiel.getEmploye().getEmployeeId(), potentiel);
        }
        return index;
    }
}
