package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.SuccesseurIdentifie;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
import com.talent360bank.talent360bank.repository.DeclarationVigilanceRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sans session JPA ouverte pendant la requete (spring.jpa.open-in-view=false)
 * et avec les entites LAZY : chaque page et chaque lecture de l'API repond
 * sans LazyInitializationException, et les pages principales comme le
 * recalcul lisent la base en un nombre de requetes qui ne depend pas de la
 * population.
 *
 * <p>Organigramme sur quatre niveaux (direction > departement > region >
 * agences) : la direction et le departement affiches remontent les parents,
 * c'est la que des jointures oubliees se verraient.
 *
 * <p>L'ordre des methodes compte : la derniere ajoute une seconde population,
 * plus grande, pour comparer les comptes.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sans-session-ouverte;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class SansSessionOuverteIntegrationTest {

    private static final String T = "/api/trimestres/2026/1";
    private static final String PARAMS = "?annee=2026&numero=1";

    /** Pages principales et lectures de l'API dont le nombre de requetes ne depend pas de la population. */
    private static final List<String> A_NOMBRE_FIXE = List.of(
            "/", "/9box", "/viviers", "/comite-talent", "/alertes", "/postes-critiques",
            T + "/vigilance", T + "/scores", T + "/talents", T + "/vivier-releve",
            "/api/comite-talent" + PARAMS, "/api/viviers-thematiques" + PARAMS,
            "/api/postes-critiques" + PARAMS, "/api/viviers/synthese" + PARAMS, T + "/postes-cibles",
            T + "/collaborateurs", T + "/collaborateurs?talent=false&tri=PERFORMANCE&page=1&taille=5",
            T + "/competences", T + "/competences?vivier=RELEVE&ordre=asc&top=3",
            "/api/notifications", T + "/campagne",
            "/collaborateurs", "/collaborateurs?talent=false&tri=PERFORMANCE&page=1",
            "/competences", "/competences?vivier=RELEVE&ordre=asc", "/notifications", "/campagne",
            "/campagne?entite=" + Entite.code(null, TypeEntite.DIRECTION, "Direction A"),
            "/managers", "/managers/A01", "/entites",
            "/entites?code=" + Entite.code(null, TypeEntite.DIRECTION, "Direction A"),
            T + "/campagne?entite=" + Entite.code(null, TypeEntite.DIRECTION, "Direction A"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private ScoreService scoreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private EntiteRepository entiteRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PotentielRepository potentielRepository;
    @Autowired
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private DeclarationVigilanceRepository declarationRepository;
    @Autowired
    private ValidationComiteRepository validationComiteRepository;
    @Autowired
    private CompetenceRepository competenceRepository;
    @Autowired
    private CompetenceCollaborateurRepository competenceCollaborateurRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private SuccesseurIdentifieRepository successeurRepository;

    private Trimestre trimestre;
    private Competence competence;

    @BeforeAll
    void peupler() {
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        trimestre.setDateReference(LocalDate.of(2026, 3, 31));
        trimestre = trimestreRepository.save(trimestre);
        parametreRepository.save(Parametre.parDefaut(trimestre));
        competence = new Competence();
        competence.setCompetenceId("C001");
        competence.setNom("Analyse de risque");
        competence = competenceRepository.save(competence);

        population("A", 2, 2);
        calculTrimestreService.calculer(trimestre);
    }

    // ------------------------------------------------------------ sans session ouverte

    @Test
    @Order(1)
    void chaque_page_et_chaque_lecture_de_l_api_repond_sans_session_ouverte() throws Exception {
        List<String> urls = new ArrayList<>(A_NOMBRE_FIXE);
        urls.addAll(List.of(
                "/api/collaborateurs", "/api/collaborateurs/A01", "/api/collaborateurs/A01/competences",
                "/api/collaborateurs/A01/scores", "/api/competences", "/api/imports", "/api/trimestres",
                T + "/parametre", T + "/vigilance/A01", T + "/talents/A01", T + "/hauts-potentiels",
                T + "/hauts-potentiels/A01", T + "/collaborateurs/A02/fiche", T + "/managers",
                T + "/managers/A01/vue", "/api/entites",
                T + "/entites/vue?code=" + Entite.code(null, TypeEntite.DIRECTION, "Direction A"),
                "/api/comite-talent/talents-valides" + PARAMS, "/api/viviers-thematiques" + PARAMS,
                "/api/viviers-thematiques/COMMERCIAL" + PARAMS,
                "/api/postes-critiques" + PARAMS, "/api/postes-critiques/alertes" + PARAMS,
                "/api/postes-critiques/synthese" + PARAMS, "/api/postes-critiques/PA" + PARAMS,
                "/api/postes/PA/candidats" + PARAMS, "/api/postes/PA/candidats/A02" + PARAMS,
                "/api/dashboard/synthese" + PARAMS, "/api/viviers/synthese" + PARAMS, T + "/postes-cibles",
                T + "/collaborateurs?q=a&case=1&vigilance=FAIBLE",
                T + "/competences?vivier=COMMERCIAL&poste=PA", "/api/notifications/badge"));
        for (String url : urls) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    /**
     * C8 : la liste de vigilance couvre les actifs qui ont au moins une donnee
     * de vigilance, score ou non ; un actif sans aucune donnee n'y est pas.
     */
    @Test
    @Order(2)
    void la_liste_de_vigilance_contient_un_collaborateur_avec_questionnaire_sans_score() throws Exception {
        mockMvc.perform(get(T + "/scores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idCollaborateur == 'AQ')]").isEmpty());

        mockMvc.perform(get(T + "/vigilance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.collaborateur.idCollaborateur == 'AQ')].signaux[0].code")
                        .value("ENGAGEMENT_FAIBLE"))
                .andExpect(jsonPath("$[?(@.collaborateur.idCollaborateur == 'AQ')].collaborateur.direction")
                        .value("Direction A"))
                .andExpect(jsonPath("$[?(@.collaborateur.idCollaborateur == 'AQ')].collaborateur.departement")
                        .value("Departement A"))
                .andExpect(jsonPath("$[?(@.collaborateur.idCollaborateur == 'AM')]").isEmpty());
    }

    /** La creation renvoie le collaborateur enregistre, entite et parents compris, hors transaction. */
    @Test
    @Order(2)
    void la_creation_d_un_collaborateur_rattache_a_une_agence_repond_sans_session_ouverte() throws Exception {
        Entite agence = entiteRepository.findByCode(Entite.code(
                entiteRepository.findByCode("DIR:DIRECTION_A/DEP:DEPARTEMENT_A/REG:REGION_A").orElseThrow(),
                TypeEntite.AGENCE, "Agence A1")).orElseThrow();

        mockMvc.perform(post("/api/collaborateurs")
                        .header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCollaborateur": "ANEW", "nom": "Nouveau", "prenom": "Arrivant",
                                 "dateEntree": "2025-09-01", "statut": "ACTIF", "entite": {"idEntite": %d}}
                                """.formatted(agence.getIdEntite())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCollaborateur").value("ANEW"))
                .andExpect(jsonPath("$.entite.libelle").value("Agence A1"))
                .andExpect(jsonPath("$.direction").value("Direction A"));
    }

    // ------------------------------------------------------------ nombre de requetes

    @Test
    @Order(3)
    void les_pages_principales_et_le_recalcul_lisent_la_base_en_un_nombre_fixe_de_requetes() throws Exception {
        Map<String, Long> petite = new LinkedHashMap<>();
        for (String url : A_NOMBRE_FIXE) {
            petite.put(url, requetes(url));
        }
        long recalculPetit = lecturesDuRecalcul();

        // Population B : trois fois plus de monde, d'agences et de postes.
        population("B", 3, 4);
        calculTrimestreService.calculer(trimestre);

        Map<String, Long> grande = new LinkedHashMap<>();
        for (String url : A_NOMBRE_FIXE) {
            grande.put(url, requetes(url));
        }
        long recalculGrand = lecturesDuRecalcul();

        System.out.println("REQUETES petite=" + petite + " grande=" + grande
                + " recalcul=" + recalculPetit + "/" + recalculGrand);
        assertThat(grande).isEqualTo(petite);
        assertThat(recalculGrand).isEqualTo(recalculPetit);
        // Badge de la cloche : apres un calcul des notifications, le trimestre seul (deux requetes).
        requetes("/api/notifications");
        assertThat(requetes("/api/notifications/badge")).isEqualTo(2);
        // Vue manager, auto-evaluations comprises : l'equipe de B01 (14) se lit comme celle de A01 (5).
        assertThat(requetes(T + "/managers/B01/vue")).isEqualTo(requetes(T + "/managers/A01/vue"));
    }

    // ------------------------------------------------------------ outils

    private long requetes(String url) throws Exception {
        Statistics statistiques = statistiques();
        statistiques.clear();
        mockMvc.perform(get(url)).andExpect(status().isOk());
        return statistiques.getPrepareStatementCount();
    }

    /** Lectures (et suppression groupee) du recalcul des scores : sans les insertions ni mises a jour, une par score. */
    private long lecturesDuRecalcul() {
        Statistics statistiques = statistiques();
        statistiques.clear();
        scoreService.recalculerTrimestre(trimestre);
        return statistiques.getPrepareStatementCount() - statistiques.getEntityInsertCount()
                - statistiques.getEntityUpdateCount();
    }

    private Statistics statistiques() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * Une direction a un departement et une region, {@code agences} agences de
     * {@code parAgence} personnes : le premier manage les autres et tient un
     * poste critique dont le second est successeur. Notes en escalier (des
     * talents et des profils faibles), questionnaire et faits pour chacun,
     * comite Oui pour un sur deux, auto-evaluation pour un sur trois. Plus deux actifs sans notes : prefixe+"Q"
     * avec un questionnaire seul (engagement faible), prefixe+"M" sans rien.
     */
    private void population(String prefixe, int agences, int parAgence) {
        Entite direction = entiteRepository.save(new Entite("Direction " + prefixe, TypeEntite.DIRECTION, null));
        Entite departement = entiteRepository.save(
                new Entite("Departement " + prefixe, TypeEntite.DEPARTEMENT, direction));
        Entite region = entiteRepository.save(new Entite("Region " + prefixe, TypeEntite.REGION, departement));

        List<Collaborateur> personnes = new ArrayList<>();
        Manager manager = null;
        int numero = 1;
        for (int a = 1; a <= agences; a++) {
            Entite agence = entiteRepository.save(
                    new Entite("Agence " + prefixe + a, TypeEntite.AGENCE, region));
            for (int p = 0; p < parAgence; p++) {
                Collaborateur collaborateur = collaborateur(prefixe + String.format("%02d", numero++), agence, manager);
                if (manager == null) {
                    manager = managerRepository.save(new Manager(collaborateur));
                }
                personnes.add(collaborateur);
            }
        }

        for (int i = 0; i < personnes.size(); i++) {
            Collaborateur collaborateur = personnes.get(i);
            BigDecimal note = BigDecimal.valueOf(95 - (i % 5) * 10L);
            performanceRepository.save(new Performance(collaborateur, trimestre, note, note, note, note, note));
            potentielRepository.save(new Potentiel(collaborateur, trimestre, note, note, note, note, note, note, note));
            if (i % 3 == 0) {
                // Auto-evaluation pour un sur trois : lue avec l'evaluation du manager, sans requete de plus.
                BigDecimal auto = note.add(BigDecimal.TEN).min(BigDecimal.valueOf(100));
                Performance performanceAuto = new Performance(collaborateur, trimestre, auto, auto, auto, auto, auto);
                performanceAuto.setSource(SourceEvaluation.AUTO);
                performanceRepository.save(performanceAuto);
                Potentiel potentielAuto = new Potentiel(collaborateur, trimestre, auto, auto, auto, auto, auto, auto,
                        auto);
                potentielAuto.setSource(SourceEvaluation.AUTO);
                potentielRepository.save(potentielAuto);
            }
            questionnaire(collaborateur, i % 2 == 0 ? "80.00" : "40.00");
            DeclarationVigilance declaration = new DeclarationVigilance(collaborateur, trimestre);
            declaration.setSansMobilite4Ans(i % 3 == 0);
            declarationRepository.save(declaration);
            validationComiteRepository.save(new ValidationComite(collaborateur, trimestre,
                    i % 2 == 0 ? StatutValidationComite.OUI : StatutValidationComite.EN_ATTENTE));
            CompetenceCollaborateur skill = new CompetenceCollaborateur();
            skill.setCollaborateur(collaborateur);
            skill.setCompetence(competence);
            skill.setNiveauActuel(1 + i % 5);
            skill.setNiveauCible(4);
            competenceCollaborateurRepository.save(skill);
        }

        Poste poste = new Poste();
        poste.setPosteId("P" + prefixe);
        poste.setNomPoste("Directeur " + prefixe);
        poste.setEntite(direction);
        poste.setPosteCritique("Oui");
        poste.setCriticite("Elevee");
        poste.setCompetenceRequise1(competence);
        poste.setNiveau1(3);
        poste.setTitulaireId(personnes.get(0).getIdCollaborateur());
        poste.setTitulaireNom(personnes.get(0).getNomComplet());
        posteRepository.save(poste);
        successeurRepository.save(new SuccesseurIdentifie(poste, personnes.get(1)));

        questionnaire(collaborateur(prefixe + "Q", region, manager), "20.00");
        collaborateur(prefixe + "M", region, manager);
    }

    private Collaborateur collaborateur(String id, Entite entite, Manager manager) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom("Nom" + id);
        collaborateur.setPrenom("Prenom" + id);
        collaborateur.setFonction("Conseiller");
        collaborateur.setDateEntree(LocalDate.of(2018, 1, 1));
        collaborateur.setEntite(entite);
        collaborateur.setManager(manager);
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        return collaborateurRepository.save(collaborateur);
    }

    private void questionnaire(Collaborateur collaborateur, String score) {
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(collaborateur);
        reponse.setTrimestre(trimestre);
        reponse.setScoreEngagement(new BigDecimal(score));
        reponse.setDateReponse(LocalDate.of(2026, 3, 1));
        questionnaireRepository.save(reponse);
    }
}
