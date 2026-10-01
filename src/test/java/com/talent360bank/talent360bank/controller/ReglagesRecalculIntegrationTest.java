package com.talent360bank.talent360bank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.controller.dto.ParametreForm;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PonderationSources;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.NineBoxCell;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurViewService;
import com.talent360bank.talent360bank.ui.service.NineBoxViewService;
import com.talent360bank.talent360bank.ui.service.VueManagerViewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changer les reglages recalcule le trimestre, de bout en bout : vraie
 * requete PUT, vrai moteur, vraie base. Apres le changement, la page 9-box
 * (case enregistree), la fiche et la vue manager (case deduite des niveaux)
 * donnent la meme case.
 *
 * <p>Equipe : MGR01 (manager, 60/60) et C01 (80/80). Aux seuils par defaut de
 * la matrice (eleve 85, moyen 70), C01 est Confirme (case 5) ; seuils eleves
 * baisses a 75, il devient Talent cle (case 9). Avec une auto-evaluation a 100
 * et un melange 70 / 30, son score officiel passe a 86 : case 9 aussi.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reglages-recalcul;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReglagesRecalculIntegrationTest {

    private static final String URL = "/api/trimestres/2026/1/parametre";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @SpyBean
    private CalculTrimestreService calculTrimestreService;

    @Autowired
    private FicheCollaborateurViewService ficheService;
    @Autowired
    private VueManagerViewService vueManagerService;
    @Autowired
    private NineBoxViewService nineBoxViewService;

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
    private ScoreRepository scoreRepository;

    private Trimestre trimestre;

    @BeforeAll
    void poserLesDonnees() {
        trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(1);
        trimestre = trimestreRepository.save(trimestre);
        parametreRepository.save(Parametre.parDefaut(trimestre));

        Entite agence = entiteRepository.save(new Entite("Agence Centre", TypeEntite.AGENCE, null));
        Collaborateur chef = collaborateur("MGR01", "Idrissi", "Omar", agence, null, "60");
        Manager manager = managerRepository.save(new Manager(chef));
        collaborateur("C01", "Bennani", "Sara", agence, manager, "80");

        calculTrimestreService.calculer(trimestre);
    }

    @AfterEach
    void remettreLeSpy() {
        reset(calculTrimestreService);
    }

    // ------------------------------------------------------------ recalcul et concordance

    @Test
    void changer_un_seuil_9_box_deplace_le_collaborateur_partout_a_la_fois() throws Exception {
        // Point de depart connu : seuils par defaut, recalcule.
        modifier(p -> {
            Parametre defauts = Parametre.parDefaut(trimestre);
            p.setSeuilsNeufBox(defauts.getSeuilsNeufBox());
            p.setSeuilsNeufBoxPotentiel(defauts.getSeuilsNeufBoxPotentiel());
        }).andExpect(status().isOk());
        verifierCasePartout(5, "Confirmé");

        modifier(p -> {
            p.getSeuilsNeufBox().setSeuilEleve(new BigDecimal("75"));
            p.getSeuilsNeufBoxPotentiel().setSeuilEleve(new BigDecimal("75"));
        })
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsNeufBox.seuilEleve").value(75))
                .andExpect(jsonPath("$.recalcul.recalcule").value(true))
                .andExpect(jsonPath("$.recalcul.nbCollaborateursScores").value(2))
                .andExpect(jsonPath("$.recalcul.nbPlaces9Box").value(2))
                .andExpect(jsonPath("$.recalcul.erreur").isEmpty());

        verifierCasePartout(9, "Talent clé");
    }

    @Test
    void des_reglages_invalides_ne_sont_ni_enregistres_ni_recalcules() throws Exception {
        String libelleAvant = parametreRepository.findByTrimestre(trimestre).orElseThrow().getLibelle();
        clearInvocations(calculTrimestreService);

        // Seuil eleve de vigilance au-dela du total des points : regle inter-blocs violee.
        modifier(p -> {
            p.setLibelle("Ne doit pas rester");
            p.getSeuilsVigilance().setSeuilEleve(new BigDecimal("500"));
        })
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("reglages_invalides"));

        assertThat(parametreRepository.findByTrimestre(trimestre).orElseThrow().getLibelle()).isEqualTo(libelleAvant);
        verify(calculTrimestreService, never()).calculer(any());
    }

    @Test
    void un_recalcul_en_echec_garde_les_reglages_et_rend_le_bilan_avec_l_erreur() throws Exception {
        doThrow(new DonneesIncompletesException("Placement impossible : table 9-box incomplete"))
                .when(calculTrimestreService).calculer(any());

        modifier(p -> p.setLibelle("Reglages du comite de septembre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Reglages du comite de septembre"))
                .andExpect(jsonPath("$.recalcul.recalcule").value(false))
                .andExpect(jsonPath("$.recalcul.erreur").value(org.hamcrest.Matchers.containsString(
                        "Placement impossible : table 9-box incomplete")));

        assertThat(parametreRepository.findByTrimestre(trimestre).orElseThrow().getLibelle())
                .isEqualTo("Reglages du comite de septembre");
    }

    @Test
    void deux_enregistrements_simultanes_du_meme_trimestre_donnent_un_409() throws Exception {
        CountDownLatch enCours = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        AtomicBoolean premierAppel = new AtomicBoolean(true);
        // Seul le premier recalcul est retenu en cours de route ; les suivants vont droit au vrai calcul.
        doAnswer(appel -> {
            if (premierAppel.getAndSet(false)) {
                enCours.countDown();
                liberer.await(10, TimeUnit.SECONDS);
            }
            return appel.callRealMethod();
        }).when(calculTrimestreService).calculer(any());

        CompletableFuture<MvcResult> premier = CompletableFuture.supplyAsync(() -> {
            try {
                return modifier(p -> p.setLibelle("Premier clic")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            assertThat(enCours.await(10, TimeUnit.SECONDS)).isTrue();

            // Double clic : refuse tout de suite, rien d'enregistre.
            modifier(p -> p.setLibelle("Second clic"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.erreur").value("recalcul_en_cours"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                            "Recalcul déjà en cours pour T1 2026")));
            // Un calcul lance a la main pendant ce temps est refuse de la meme facon.
            mockMvc.perform(ecriture(post("/api/trimestres/2026/1/calcul")))
                    .andExpect(status().isConflict());
        } finally {
            liberer.countDown();
        }

        assertThat(premier.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        assertThat(parametreRepository.findByTrimestre(trimestre).orElseThrow().getLibelle()).isEqualTo("Premier clic");
    }

    // ------------------------------------------------------------ auto-evaluation

    /**
     * C01 : manager 80 partout, auto-evaluation 100 partout. A 100 / 0, le
     * score officiel reste 80 (case 5) et les vues montrent l'ecart de 20 ; a
     * 70 / 30, le recalcul automatique donne 80 x 0,7 + 100 x 0,3 = 86 (case 9),
     * partout a la fois.
     */
    @Test
    void la_ponderation_des_sources_change_le_score_officiel_et_les_vues_comparent_auto_et_manager()
            throws Exception {
        Collaborateur c01 = collaborateurRepository.findById("C01").orElseThrow();
        BigDecimal cent = new BigDecimal("100");
        Performance performanceAuto = new Performance(c01, trimestre, cent, cent, cent, cent, cent);
        performanceAuto.setSource(SourceEvaluation.AUTO);
        performanceAuto = performanceRepository.save(performanceAuto);
        Potentiel potentielAuto = new Potentiel(c01, trimestre, cent, cent, cent, cent, cent, cent, cent);
        potentielAuto.setSource(SourceEvaluation.AUTO);
        potentielAuto = potentielRepository.save(potentielAuto);
        try {
            modifier(p -> {
                Parametre defauts = Parametre.parDefaut(trimestre);
                p.setSeuilsNeufBox(defauts.getSeuilsNeufBox());
                p.setSeuilsNeufBoxPotentiel(defauts.getSeuilsNeufBoxPotentiel());
                p.setPonderationSources(defauts.getPonderationSources());
            }).andExpect(status().isOk());
            assertThat(scoreRepository.findByCollaborateurIdCollaborateurAndTrimestre("C01", trimestre)
                    .orElseThrow().getScorePerformance()).isEqualByComparingTo("80");
            verifierCasePartout(5, "Confirmé");

            FicheCollaborateur.EvaluationAuto auto = ficheService.construire("C01", 2026, 1)
                    .autoEvaluation().performance();
            assertThat(auto.score()).isEqualByComparingTo("100");
            assertThat(auto.categorie()).isEqualTo("EXCEPTIONNELLE");
            assertThat(auto.scoreManager()).isEqualByComparingTo("80");
            assertThat(auto.ecart()).isEqualByComparingTo("20");
            assertThat(auto.criteres()).hasSize(5).allSatisfy(critere -> {
                assertThat(critere.note()).isEqualByComparingTo("100");
                assertThat(critere.noteManager()).isEqualByComparingTo("80");
                assertThat(critere.ecart()).isEqualByComparingTo("20");
            });

            VueManager.AutoVsManager equipe = vueManagerService.construire("MGR01", 2026, 1).autoVsManager();
            assertThat(equipe.membres()).singleElement().satisfies(membre -> {
                assertThat(membre.matricule()).isEqualTo("C01");
                assertThat(membre.performanceAuto()).isEqualByComparingTo("100");
                assertThat(membre.performanceManager()).isEqualByComparingTo("80");
                assertThat(membre.ecartPotentiel()).isEqualByComparingTo("20");
            });
            assertThat(equipe.ecartMoyenPerformance()).isEqualByComparingTo("20");
            assertThat(equipe.seuilEcartImportant()).isEqualByComparingTo("15");
            assertThat(equipe.ecartsImportants()).extracting(VueManager.EcartAutoManager::matricule)
                    .containsExactly("C01");

            // Seuil des reglages releve a 25 : l'ecart de 20 n'est plus signale.
            modifier(p -> p.getSeuilsAutoEvaluation().setSeuilEcartImportant(new BigDecimal("25")))
                    .andExpect(status().isOk());
            equipe = vueManagerService.construire("MGR01", 2026, 1).autoVsManager();
            assertThat(equipe.seuilEcartImportant()).isEqualByComparingTo("25");
            assertThat(equipe.ecartsImportants()).isEmpty();

            modifier(p -> p.setPonderationSources(new PonderationSources(new BigDecimal("70"), new BigDecimal("30"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ponderationSources.poidsAuto").value(30))
                    .andExpect(jsonPath("$.recalcul.recalcule").value(true));

            Score score = scoreRepository.findByCollaborateurIdCollaborateurAndTrimestre("C01", trimestre)
                    .orElseThrow();
            assertThat(score.getScorePerformance()).isEqualByComparingTo("86");
            assertThat(score.getScorePotentiel()).isEqualByComparingTo("86");
            verifierCasePartout(9, "Talent clé");
        } finally {
            performanceRepository.delete(performanceAuto);
            potentielRepository.delete(potentielAuto);
            modifier(p -> {
                p.setPonderationSources(Parametre.parDefaut(trimestre).getPonderationSources());
                p.setSeuilsAutoEvaluation(Parametre.parDefaut(trimestre).getSeuilsAutoEvaluation());
            }).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------ outils

    /** La case de C01 est la meme sur la page 9-box, dans la fiche et dans la vue manager. */
    private void verifierCasePartout(int numero, String libelle) {
        assertThat(scoreRepository.findByCollaborateurIdCollaborateurAndTrimestre("C01", trimestre)
                .orElseThrow().getPositionBox()).isEqualTo(libelle);

        NineBoxCell cellule = nineBoxViewService.buildGrid().stream()
                .filter(c -> c.getCollaborateurs().contains("Sara Bennani"))
                .findFirst().orElseThrow();
        assertThat(cellule.getCategorie()).isEqualTo(libelle);
        assertThat((cellule.getNiveauPerformance() - 1) * 3 + cellule.getNiveauPotentiel()).isEqualTo(numero);

        assertThat(ficheService.construire("C01", 2026, 1).neufBox().numero()).isEqualTo(numero);
        assertThat(ficheService.construire("C01", 2026, 1).neufBox().libelle()).isEqualTo(libelle);

        var membre = vueManagerService.construire("MGR01", 2026, 1).membres().get(0);
        assertThat(membre.matricule()).isEqualTo("C01");
        assertThat(membre.neufBox().numero()).isEqualTo(numero);
        assertThat(membre.neufBox().libelle()).isEqualTo(libelle);
    }

    /** PUT des reglages actuels modifies par {@code modification}, comme le ferait l'ecran Parametres. */
    private org.springframework.test.web.servlet.ResultActions modifier(Consumer<Parametre> modification)
            throws Exception {
        Parametre p = parametreRepository.findByTrimestre(trimestre).orElseThrow();
        modification.accept(p);
        ParametreForm form = new ParametreForm(p.getLibelle(),
                p.getPoidsPerformance(), p.getPoidsPotentiel(), p.getPoidsSuccession(), p.getPonderationSources(),
                p.getSeuilsAutoEvaluation(), p.getBaremeExperience(), p.getBaremeCompetences(), p.getSeuilsNeufBox(),
                p.getSeuilsNeufBoxPotentiel(),
                p.getSeuilsCategoriePerformance(), p.getSeuilsGapCompetence(), p.getSeuilsReadiness(),
                p.getSeuilsCouverture(), p.getSeuilsTalent(), p.getPointsVigilance(), p.getSeuilsVigilance());
        return mockMvc.perform(ecriture(put(URL))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(form)));
    }

    private static MockHttpServletRequestBuilder ecriture(MockHttpServletRequestBuilder requete) {
        return requete.header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1").with(user("rh").roles("RH"));
    }

    private Collaborateur collaborateur(String id, String nom, String prenom, Entite entite, Manager manager,
                                        String note) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom(nom);
        collaborateur.setPrenom(prenom);
        collaborateur.setEntite(entite);
        collaborateur.setManager(manager);
        collaborateur.setDateEntree(LocalDate.of(2018, 1, 1));
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        collaborateur = collaborateurRepository.save(collaborateur);
        BigDecimal n = new BigDecimal(note);
        performanceRepository.save(new Performance(collaborateur, trimestre, n, n, n, n, n));
        potentielRepository.save(new Potentiel(collaborateur, trimestre, n, n, n, n, n, n, n));
        return collaborateur;
    }
}
