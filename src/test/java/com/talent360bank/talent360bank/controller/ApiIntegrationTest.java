package com.talent360bank.talent360bank.controller;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.talent360bank.talent360bank.controller.dto.ParametreForm;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import com.talent360bank.talent360bank.securite.ConnexionHttpDeTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-test de bout en bout de l'API : vrai contexte Spring, vrai serveur
 * HTTP, vraie base, vraie serialisation.
 *
 * <p>Les tests @WebMvcTest mockent tout ce qui est sous le controleur : ils
 * prouvent le routage et les codes d'erreur, jamais que les requetes @Query
 * ramenent des lignes, que les proxies LAZY survivent a la serialisation, ou
 * que @Transactional est bien applique sur une methode de controleur. C'est
 * exactement ce que ce test couvre.
 *
 * <p>L'ordre des methodes est significatif : le placement 9-box lit les scores
 * que le recalcul vient d'ecrire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private EntiteRepository entiteRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PotentielRepository potentielRepository;
    @Autowired
    private ScoreRepository scoreRepository;
    @Autowired
    private CompetenceRepository competenceRepository;
    @Autowired
    private CompetenceCollaborateurRepository competenceCollaborateurRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Trimestre courant;
    private Trimestre precedent;
    private Entite reseau;

    /**
     * Le client de test ecrit comme un ecran : avec l'en-tete exige sur les
     * ecritures (voir ProtectionRequetesFilter).
     */
    private static final ClientHttpRequestInterceptor EN_TETE_ECRITURE = (requete, corps, execution) -> {
        requete.getHeaders().add(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1");
        return execution.execute(requete, corps);
    };

    @LocalServerPort
    private int port;

    /** Cookie de la session RH ouverte au debut, par le vrai formulaire de connexion. */
    private String sessionRh;

    @BeforeAll
    void poserLesDonnees() {
        restTemplate.getRestTemplate().getInterceptors().add(EN_TETE_ECRITURE);
        sessionRh = ConnexionHttpDeTest.connecterRh(restTemplate, utilisateurRepository, passwordEncoder);
        precedent = trimestre(4, 2025);
        courant = trimestre(1, 2026);
        parametreRepository.save(Parametre.parDefaut(courant));
        reseau = entiteRepository.findByCode(Entite.code(null, TypeEntite.DIRECTION, "Reseau"))
                .orElseGet(() -> entiteRepository.save(new Entite("Reseau", TypeEntite.DIRECTION, null)));

        Competence analyse = competence("C001", "Analyse de risque");
        Competence management = competence("C002", "Management d'equipe");

        // Anciennete 12 ans : au plafond du critere experience.
        Collaborateur forte = collaborateur("E001", "Bennani", "Sara", LocalDate.of(2014, 3, 1));
        // Anciennete 2 ans, engagement bas : deux signaux de vigilance.
        Collaborateur fragile = collaborateur("E002", "Alaoui", "Karim", LocalDate.of(2024, 1, 15));
        // Titulaire du poste cible : score mais exclu du classement succession.
        Collaborateur titulaire = collaborateur("E003", "Tazi", "Nadia", LocalDate.of(2018, 9, 1));

        notes(forte, "90", "90");
        notes(fragile, "50", "50");
        notes(titulaire, "95", "95");

        // Score du trimestre precedent, superieur : le recalcul du trimestre
        // courant doit faire apparaitre une baisse de performance sur E001.
        scorePrecedent(forte, "95.00", "92.00");

        questionnaire(fragile, "20.00");

        competenceCollaborateurRepository.save(skill(forte, analyse, 4, 4));
        competenceCollaborateurRepository.save(skill(forte, management, 3, 3));
        competenceCollaborateurRepository.save(skill(fragile, analyse, 1, 4));

        Poste poste = new Poste();
        poste.setPosteId("P001");
        poste.setNomPoste("Directeur d'agence");
        poste.setCompetenceRequise1(analyse);
        poste.setNiveau1(4);
        poste.setCompetenceRequise2(management);
        poste.setNiveau2(3);
        poste.setTitulaireId("E003");
        posteRepository.save(poste);
    }

    private Trimestre trimestre(int numero, int annee) {
        Trimestre cree = new Trimestre();
        cree.setNumero(numero);
        cree.setAnnee(annee);
        return trimestreRepository.save(cree);
    }

    private Competence competence(String id, String nom) {
        Competence competence = new Competence();
        competence.setCompetenceId(id);
        competence.setNom(nom);
        return competenceRepository.save(competence);
    }

    private Collaborateur collaborateur(String id, String nom, String prenom, LocalDate entree) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom(nom);
        collaborateur.setPrenom(prenom);
        collaborateur.setDateEntree(entree);
        collaborateur.setEntite(reseau);
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        return collaborateurRepository.save(collaborateur);
    }

    private void notes(Collaborateur collaborateur, String performance, String potentiel) {
        BigDecimal p = new BigDecimal(performance);
        BigDecimal q = new BigDecimal(potentiel);
        performanceRepository.save(new Performance(collaborateur, courant, p, p, p, p, p));
        potentielRepository.save(new Potentiel(collaborateur, courant, q, q, q, q, q, q, q));
    }

    private void scorePrecedent(Collaborateur collaborateur, String performance, String potentiel) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setTrimestre(precedent);
        score.setScorePerformance(new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal(potentiel));
        scoreRepository.save(score);
    }

    private void questionnaire(Collaborateur collaborateur, String scoreEngagement) {
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(collaborateur);
        reponse.setTrimestre(courant);
        reponse.setScoreEngagement(new BigDecimal(scoreEngagement));
        reponse.setDateReponse(LocalDate.of(2026, 2, 1));
        questionnaireRepository.save(reponse);
    }

    private CompetenceCollaborateur skill(Collaborateur collaborateur, Competence competence, int actuel, int cible) {
        CompetenceCollaborateur skill = new CompetenceCollaborateur();
        skill.setCollaborateur(collaborateur);
        skill.setCompetence(competence);
        skill.setNiveauActuel(actuel);
        skill.setNiveauCible(cible);
        return skill;
    }

    private DocumentContext json(ResponseEntity<String> reponse) {
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JsonPath.parse(reponse.getBody());
    }

    private ResponseEntity<String> get(String url) {
        return restTemplate.getForEntity(url, String.class);
    }

    @Test
    @Order(1)
    void le_recalcul_des_scores_repond_et_enregistre() {
        DocumentContext corps = json(restTemplate.postForEntity(
                "/api/trimestres/2026/1/scores/recalcul", null, String.class));

        assertThat(corps.read("$.nombreCalcules", Integer.class)).isEqualTo(3);
        assertThat(corps.read("$.nombreIgnores", Integer.class)).isZero();
        assertThat(corps.read("$.scores[*].idCollaborateur", List.class))
                .containsExactlyInAnyOrder("E001", "E002", "E003");
    }

    @Test
    @Order(2)
    void les_scores_sortent_avec_le_nom_complet_de_l_collaborateur() {
        // Traverse la relation Score -> Collaborateur apres serialisation : c'est
        // ici qu'une LazyInitializationException se manifesterait.
        DocumentContext corps = json(get("/api/trimestres/2026/1/scores"));

        assertThat(corps.read("$.length()", Integer.class)).isEqualTo(3);
        assertThat(corps.read("$[?(@.idCollaborateur == 'E001')].nomComplet", List.class))
                .containsExactly("Sara Bennani");
        assertThat(corps.read("$[?(@.idCollaborateur == 'E001')].scorePerformance", List.class))
                .containsExactly(90.00);
    }

    @Test
    @Order(3)
    void le_placement_9box_repond_et_pose_la_categorie() {
        DocumentContext corps = json(restTemplate.postForEntity(
                "/api/trimestres/2026/1/9box/placement", null, String.class));

        assertThat(corps.read("$.nombreCalcules", Integer.class)).isEqualTo(3);

        // La categorie vient de la table de reference posee au demarrage par
        // Matrice9BoxInitializer : le test verifie aussi que ce runner tourne.
        List<String> cases = json(get("/api/trimestres/2026/1/scores"))
                .read("$[*].positionBox", List.class);
        assertThat(cases).doesNotContainNull().hasSize(3);
    }

    @Test
    @Order(4)
    void les_talents_sont_detectes_sur_les_scores_reels() {
        DocumentContext corps = json(get("/api/trimestres/2026/1/talents"));

        // Seuils par defaut a 85/85 : seul E003 (95/95) passe, E001 est a 90
        // en performance mais 90 en potentiel, donc talent aussi.
        assertThat(corps.read("$[*].idCollaborateur", List.class))
                .containsExactlyInAnyOrder("E001", "E003");
    }

    @Test
    @Order(5)
    void l_historique_d_un_collaborateur_couvre_les_deux_trimestres() {
        DocumentContext corps = json(get("/api/collaborateurs/E001/scores"));

        assertThat(corps.read("$.length()", Integer.class)).isEqualTo(2);
    }

    @Test
    @Order(6)
    void la_vigilance_leve_les_signaux_sur_donnees_reelles() {
        DocumentContext corps = json(get("/api/trimestres/2026/1/vigilance"));

        assertThat(corps.read("$.length()", Integer.class)).isEqualTo(3);

        // E002 : engagement 20 sous le seuil de 60, aucun score precedent.
        assertThat(corps.read("$[?(@.collaborateur.idCollaborateur == 'E002')].signaux[*].code", List.class))
                .containsExactly("ENGAGEMENT_FAIBLE");

        // E001 : 90 au trimestre courant contre 95 au precedent.
        assertThat(corps.read("$[?(@.collaborateur.idCollaborateur == 'E001')].signaux[*].code", List.class))
                .containsExactly("BAISSE_PERFORMANCE");
    }

    @Test
    @Order(7)
    void le_filtre_de_vigilance_par_niveau_fonctionne() {
        // Plafond de la detection automatique : personne n'atteint ELEVEE.
        assertThat(json(get("/api/trimestres/2026/1/vigilance?minimum=ELEVEE"))
                .read("$.length()", Integer.class)).isZero();
    }

    @Test
    @Order(8)
    void le_classement_succession_charge_les_competences_du_poste() {
        // Traverse les cinq ManyToOne LAZY de Poste via le join fetch : sans
        // lui, cet appel leverait une LazyInitializationException.
        DocumentContext corps = json(get("/api/postes/P001/candidats?annee=2026&numero=1"));

        // E003 est titulaire du poste : exclu du classement.
        assertThat(corps.read("$[*].candidat.idCollaborateur", List.class))
                .containsExactly("E001", "E002");
        assertThat(corps.read("$[0].detail.competences", Double.class)).isEqualTo(100.00);
        assertThat(corps.read("$[0].readiness", String.class)).isNotBlank();
    }

    @Test
    @Order(9)
    void la_short_list_succession_est_bornee() {
        assertThat(json(get("/api/postes/P001/candidats?annee=2026&numero=1&limite=1"))
                .read("$.length()", Integer.class)).isEqualTo(1);
    }

    @Test
    @Order(10)
    void le_matching_d_un_candidat_seul_repond() {
        DocumentContext corps = json(get("/api/postes/P001/candidats/E001?annee=2026&numero=1"));

        assertThat(corps.read("$.candidat.idCollaborateur", String.class)).isEqualTo("E001");
        assertThat(corps.read("$.detail.competences", Double.class)).isEqualTo(100.00);
    }

    @Test
    @Order(11)
    void les_reglages_se_lisent_avec_le_trimestre_aplati() {
        // Parametre.trimestre est LAZY : sans @Transactional sur la methode de
        // controleur, cette lecture echouerait a la serialisation.
        DocumentContext corps = json(get("/api/trimestres/2026/1/parametre"));

        assertThat(corps.read("$.annee", Integer.class)).isEqualTo(2026);
        assertThat(corps.read("$.numero", Integer.class)).isEqualTo(1);
        assertThat(corps.read("$.seuilsVigilance.seuilEngagementFaible", Double.class)).isEqualTo(60.0);
    }

    @Test
    @Order(12)
    void les_reglages_se_modifient_et_le_changement_est_relu() {
        Parametre voulu = Parametre.parDefaut(courant);
        voulu.getSeuilsTalent().setSeuilPerformance(new BigDecimal("80"));

        ParametreForm form = new ParametreForm("Revu au comite",
                voulu.getPoidsPerformance(), voulu.getPoidsPotentiel(),
                voulu.getPoidsSuccession(), voulu.getBaremeExperience(), voulu.getBaremeCompetences(),
                voulu.getSeuilsNeufBox(), voulu.getSeuilsNeufBoxPotentiel(),
                voulu.getSeuilsCategoriePerformance(), voulu.getSeuilsGapCompetence(),
                voulu.getSeuilsReadiness(), voulu.getSeuilsCouverture(),
                voulu.getSeuilsTalent(), voulu.getPointsVigilance(), voulu.getSeuilsVigilance());

        ResponseEntity<String> reponse = restTemplate.exchange(
                "/api/trimestres/2026/1/parametre", HttpMethod.PUT,
                new HttpEntity<>(form), String.class);

        assertThat(json(reponse).read("$.seuilsTalent.seuilPerformance", Double.class)).isEqualTo(80.0);
        assertThat(json(get("/api/trimestres/2026/1/parametre"))
                .read("$.libelle", String.class)).isEqualTo("Revu au comite");
    }

    @Test
    @Order(13)
    void les_reglages_par_defaut_se_creent_sur_un_trimestre_neuf() {
        ResponseEntity<String> creation = restTemplate.postForEntity(
                "/api/trimestres/2025/4/parametre", null, String.class);

        assertThat(creation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(JsonPath.parse(creation.getBody())
                .read("$.poidsPerformance.poidsObjectifs", Double.class)).isEqualTo(40.0);

        // Rejouer l'appel doit rendre 409, pas ecraser les reglages en place.
        assertThat(restTemplate.postForEntity(
                "/api/trimestres/2025/4/parametre", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(14)
    void un_trimestre_inconnu_rend_404_avec_le_corps_d_erreur() {
        ResponseEntity<String> reponse = get("/api/trimestres/2099/3/scores");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JsonPath.parse(reponse.getBody()).read("$.erreur", String.class))
                .isEqualTo("ressource_introuvable");
    }

    @Test
    @Order(15)
    void un_trimestre_sans_reglages_rend_404_sur_un_calcul() {
        // Le trimestre existe, ses reglages non. Le 404 porte alors sur le
        // Parametre absent, pas sur le trimestre : c'est chargerParametre qui
        // leve, et son message nomme la ressource reellement manquante.
        trimestre(2, 2026);

        ResponseEntity<String> reponse = get("/api/trimestres/2026/2/talents");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JsonPath.parse(reponse.getBody()).read("$.erreur", String.class))
                .isEqualTo("ressource_introuvable");
    }

    @Test
    @Order(16)
    void une_ecriture_sans_l_en_tete_est_refusee_sans_rien_executer() {
        // Client sans l'intercepteur : ce qu'enverrait un formulaire d'un autre site.
        TestRestTemplate sansEnTete = new TestRestTemplate();

        ResponseEntity<String> reponse = sansEnTete.postForEntity(
                "http://localhost:" + port + "/api/trimestres/2026/1/9box/placement", null, String.class);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(JsonPath.parse(reponse.getBody()).read("$.erreur", String.class))
                .isEqualTo("en_tete_manquant");
    }

    @Test
    @Order(17)
    void une_lecture_sans_l_en_tete_reste_permise() {
        // Client sans l'intercepteur d'en-tete, avec seulement le cookie de session.
        HttpHeaders entetes = new HttpHeaders();
        entetes.add(HttpHeaders.COOKIE, sessionRh);
        ResponseEntity<String> reponse = new TestRestTemplate().exchange(
                "http://localhost:" + port + "/api/trimestres/2026/1/scores", HttpMethod.GET,
                new HttpEntity<>(entetes), String.class);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(18)
    void une_lecture_sans_session_rend_401() {
        ResponseEntity<String> reponse = new TestRestTemplate().getForEntity(
                "http://localhost:" + port + "/api/trimestres/2026/1/scores", String.class);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(JsonPath.parse(reponse.getBody()).read("$.erreur", String.class))
                .isEqualTo("non_authentifie");
    }
}
