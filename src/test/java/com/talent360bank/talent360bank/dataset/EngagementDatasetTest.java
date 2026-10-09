package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEvenement;
import com.talent360bank.talent360bank.repository.JournalEvenementRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ReponseQuestionnaireRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.Engagement;
import com.talent360bank.talent360bank.ui.model.Engagement.Nature;
import com.talent360bank.talent360bank.ui.model.Engagement.Question;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engagement &amp; Fidelisation sur le vrai classeur (T3 2026) et le fichier de reponses de test
 * (BP001 a BP003, src/test/resources/questionnaire) : cartes, chaque question, par direction ; puis
 * dimensions et eNPS une fois regles dans Parametres, "Non configuré" avant.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:engagement-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class EngagementDatasetTest {

    private static final Path CLASSEUR = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final Path REPONSES = Path.of(
            "src/test/resources/questionnaire/questionnaire-reponses-BP001-BP003.xlsx");
    private static final String T3 = "2026-3";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreService trimestreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private ReponseQuestionnaireRepository reponseRepository;
    @Autowired
    private JournalEvenementRepository journalRepository;

    private Trimestre t3;

    static boolean classeurPresent() {
        return Files.exists(CLASSEUR);
    }

    @BeforeAll
    void importer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", CLASSEUR.getFileName().toString(), null,
                Files.readAllBytes(CLASSEUR));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        trimestreService.modifierDateReference(t3, LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(t3);
    }

    /** Par la page Import, comme le RH (connecte : @WithMockUser ne vaut pas pour @BeforeAll). */
    @Test
    @Order(0)
    void le_fichier_de_reponses_s_importe() throws Exception {
        importerReponses();
        assertThat(reponseRepository.findDuCollaborateur("BP001", t3)).hasSize(16);
    }

    private void importerReponses() throws Exception {
        mockMvc.perform(multipart("/import/questionnaire")
                        .file(new MockMultipartFile("fichier", REPONSES.getFileName().toString(), null,
                                Files.readAllBytes(REPONSES)))
                        .param("trimestre", T3).with(csrf()))
                .andExpect(status().isOk());
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
    }

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Engagement vue() throws Exception {
        return (Engagement) page("/engagement?trimestre=" + T3).getModelAndView().getModel().get("vue");
    }

    private static Question question(Engagement vue, String code) {
        return vue.questions().stream().filter(q -> q.code().equals(code)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------ avant reglage

    @Test
    @Order(1)
    void les_cartes_viennent_des_reponses_et_du_score_d_engagement() throws Exception {
        Engagement vue = vue();
        // 100 actifs, 3 repondants (BP001 a BP003).
        assertThat(vue.population()).isEqualTo(100);
        assertThat(vue.nbRepondants()).isEqualTo(3);
        assertThat(vue.tauxReponse()).isEqualByComparingTo("3.0");
        // Engagement moyen : la moyenne des scores importes (12_VIGILANCE) des actifs, comme le tableau de bord.
        List<BigDecimal> scores = questionnaireRepository.findByTrimestreAvecCollaborateur(t3).stream()
                .filter(q -> q.getCollaborateur().estCalculable())
                .map(QuestionnaireEngagement::getScoreEngagement).filter(Objects::nonNull).toList();
        assertThat(vue.nbScores()).isEqualTo(scores.size()).isEqualTo(100);
        assertThat(vue.engagementMoyen()).isEqualByComparingTo(scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
        // Sans reglage : ni eNPS ni dimensions, dit tel quel.
        assertThat(vue.enps()).isNull();
        assertThat(vue.dimensionsConfigurees()).isFalse();
        String page = html(page("/engagement?trimestre=" + T3));
        assertThat(page).contains("id=\"kpi-enps\">Non configuré</div>", "Non configuré : rattachez chaque question",
                        "Non configuré : choisissez la question notée de 0 à 10", "<div class=\"lbl\">Taux de réponse (3/100)</div>")
                .doesNotContain("en cours de développement");
    }

    @Test
    @Order(2)
    void chaque_question_dans_l_ordre_du_fichier_avec_sa_nature() throws Exception {
        Engagement vue = vue();
        // Colonnes Q15 a Q18 vides dans le fichier : pas de reponse, pas de question affichee.
        assertThat(vue.questions()).extracting(Question::code).containsExactly("Q01", "Q02", "Q03", "Q04", "Q05",
                "Q06", "Q07", "Q08", "Q09", "Q10", "Q11", "Q12", "Q13", "Q14", "Q19", "Q20");
        Question q03 = question(vue, "Q03");
        assertThat(q03.texte()).isEqualTo("Mon manager me donne un retour régulier et utile sur mon travail.");
        assertThat(q03.nature()).isEqualTo(Nature.NUMERIQUE);
        assertThat(q03.nbReponses()).isEqualTo(3);
        assertThat(q03.moyenne()).isEqualByComparingTo("2.33");
        assertThat(q03.distribution()).extracting(Engagement.Valeur::valeur, Engagement.Valeur::nombre)
                .containsExactly(tuple("2", 2), tuple("3", 1));
        assertThat(q03.distribution().get(0).pourcentage()).isEqualByComparingTo("66.7");
        assertThat(question(vue, "Q04").moyenne()).isEqualByComparingTo("3.00");
        assertThat(question(vue, "Q13").moyenne()).isEqualByComparingTo("3.67");
        assertThat(question(vue, "Q20").moyenne()).isEqualByComparingTo("4.00");
        // Direction : une seule valeur repetee, un choix.
        Question q01 = question(vue, "Q01");
        assertThat(q01.nature()).isEqualTo(Nature.CHOIX);
        assertThat(q01.distribution()).extracting(Engagement.Valeur::valeur, Engagement.Valeur::nombre)
                .containsExactly(tuple("Reseau Retail", 3));
        // Reponses libres : comptees et listees avec leur auteur, lien vers sa fiche.
        Question q14 = question(vue, "Q14");
        assertThat(q14.nature()).isEqualTo(Nature.TEXTE);
        assertThat(q14.nbReponses()).isEqualTo(3);
        assertThat(q14.textes()).extracting(Engagement.Texte::matricule).containsExactlyInAnyOrder("BP001", "BP002", "BP003");
        assertThat(q14.textes()).filteredOn(t -> t.matricule().equals("BP001")).singleElement()
                .satisfies(t -> assertThat(t.texte()).startsWith("Plus de visibilité sur les postes ouverts"));
        String page = html(page("/engagement?trimestre=" + T3));
        assertThat(page).contains("id=\"question-Q14\"", "href=\"/fiche-collaborateur?matricule=BP001&amp;trimestre=2026-3\"",
                "Réponse libre", "Échelle numérique");
    }

    @Test
    @Order(3)
    void par_direction_le_taux_de_reponse_et_l_engagement_moyen() throws Exception {
        Engagement vue = vue();
        assertThat(vue.entites().stream().mapToInt(Engagement.ParEntite::effectif).sum()).isEqualTo(100);
        Engagement.ParEntite retail = vue.entites().stream().filter(e -> e.direction().equals("Reseau Retail"))
                .findFirst().orElseThrow();
        assertThat(retail.repondants()).isEqualTo(3);
        assertThat(retail.tauxReponse()).isEqualByComparingTo(BigDecimal.valueOf(300)
                .divide(BigDecimal.valueOf(retail.effectif()), 1, RoundingMode.HALF_UP));
        assertThat(vue.entites()).filteredOn(e -> !e.direction().equals("Reseau Retail"))
                .allSatisfy(e -> assertThat(e.repondants()).isZero());
        assertThat(vue.entites()).allSatisfy(e -> assertThat(e.engagementMoyen()).isNotNull());
    }

    // ------------------------------------------------------------ reglage RH

    @Test
    @Order(4)
    void un_reglage_invalide_est_refuse_et_rien_n_est_enregistre() throws Exception {
        MvcResult refus = mockMvc.perform(post("/parametres/questionnaire").with(csrf()).param("trimestre", T3)
                .param("enps", "Q99")).andExpect(status().is3xxRedirection()).andReturn();
        assertThat((String) refus.getFlashMap().get("erreurQuestionnaire"))
                .isEqualTo("Question eNPS inconnue : Q99. Rien n'a été enregistré.");
        MvcResult tropLong = mockMvc.perform(post("/parametres/questionnaire").with(csrf())
                .param("dimension[Q03]", "x".repeat(101))).andReturn();
        assertThat((String) tropLong.getFlashMap().get("erreurQuestionnaire")).startsWith("La dimension de Q03 dépasse 100");
        assertThat(vue().enps()).isNull();
        assertThat(vue().dimensionsConfigurees()).isFalse();
    }

    @Test
    @Order(5)
    void une_fois_reglees_les_dimensions_et_l_enps_sont_calcules() throws Exception {
        // La page Parametres liste les 16 questions importees.
        String parametres = html(page("/parametres?trimestre=" + T3));
        assertThat(parametres).contains("id=\"questionnaire\"", "name=\"dimension[Q03]\"", "name=\"dimension[Q20]\"",
                "value=\"Q13\"", "action=\"/parametres/questionnaire\"");

        MvcResult reponse = mockMvc.perform(post("/parametres/questionnaire").with(csrf()).param("trimestre", T3)
                        .param("dimension[Q03]", "Management").param("dimension[Q04]", " Management ")
                        .param("dimension[Q05]", "Reconnaissance").param("enps", "Q13"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(reponse.getResponse().getRedirectedUrl()).isEqualTo("/parametres?trimestre=2026-3#questionnaire");
        String message = "Questionnaire d'engagement : 3 question(s) rattachée(s) à 2 dimension(s) "
                + "(Management, Reconnaissance) ; question eNPS : Q13.";
        assertThat((String) reponse.getFlashMap().get("succesQuestionnaire")).isEqualTo(message);

        Engagement vue = vue();
        // Management : Q03 (2, 3, 2) et Q04 (3, 3, 3) = 16 / 6 ; Reconnaissance : Q05 (2, 4, 1) = 7 / 3.
        assertThat(vue.dimensions()).extracting(Engagement.Dimension::nom, Engagement.Dimension::codes,
                        Engagement.Dimension::nbReponses)
                .containsExactly(tuple("Management", List.of("Q03", "Q04"), 6), tuple("Reconnaissance", List.of("Q05"), 3));
        assertThat(vue.dimensions().get(0).moyenne()).isEqualByComparingTo("2.67");
        assertThat(vue.dimensions().get(1).moyenne()).isEqualByComparingTo("2.33");
        // eNPS sur Q13 (6, 3, 2) : 0 promoteur, 3 detracteurs, soit -100.
        assertThat(vue.enps()).satisfies(e -> {
            assertThat(e.code()).isEqualTo("Q13");
            assertThat(e.nbReponses()).isEqualTo(3);
            assertThat(e.promoteurs()).isZero();
            assertThat(e.passifs()).isZero();
            assertThat(e.detracteurs()).isEqualTo(3);
            assertThat(e.valeur()).isEqualByComparingTo("-100");
            assertThat(e.horsEchelle()).isZero();
        });
        assertThat(question(vue, "Q13").questionEnps()).isTrue();
        assertThat(question(vue, "Q03").dimension()).isEqualTo("Management");
        String page = html(page("/engagement?trimestre=" + T3));
        assertThat(page).contains("id=\"kpi-enps\">-100.0</div>", "data-dimension=\"Management\"")
                .doesNotContain("Non configuré");

        // Le theme des reponses vient du reglage : colonne Theme du Talent Passport.
        Map<String, String> themes = reponseRepository.findDuCollaborateur("BP001", t3).stream()
                .filter(r -> r.getTheme() != null)
                .collect(Collectors.toMap(r -> r.getCodeQuestion(), r -> r.getTheme()));
        assertThat(themes).containsOnly(Map.entry("Q03", "Management"), Map.entry("Q04", "Management"),
                Map.entry("Q05", "Reconnaissance"));
        assertThat(html(page("/fiche-collaborateur?matricule=BP001&trimestre=" + T3))).contains("<th>Thème</th>",
                "Reconnaissance");

        // Historique : le reglage est un evenement Parametres.
        assertThat(journalRepository.rechercher(TypeEvenement.PARAMETRES, null, null, null, null,
                        org.springframework.data.domain.Pageable.unpaged()).getContent())
                .extracting(JournalEvenement::getDescription).contains(message);
    }

    @Test
    @Order(6)
    void un_nouvel_import_des_reponses_garde_les_themes_et_est_journalise() throws Exception {
        importerReponses();
        assertThat(reponseRepository.findDuCollaborateur("BP002", t3)).filteredOn(r -> r.getCodeQuestion().equals("Q05"))
                .singleElement().satisfies(r -> assertThat(r.getTheme()).isEqualTo("Reconnaissance"));
        assertThat(journalRepository.rechercher(TypeEvenement.IMPORT, null, null, null, "%questionnaire%",
                        org.springframework.data.domain.Pageable.unpaged()).getContent())
                .hasSize(2).allSatisfy(e -> assertThat(e.getDescription()).startsWith(
                        "Import des réponses au questionnaire « questionnaire-reponses-BP001-BP003.xlsx » (T3 2026) : Réussi"));
        // Retirer la question eNPS et les dimensions : de nouveau "Non configuré".
        mockMvc.perform(post("/parametres/questionnaire").with(csrf()).param("enps", "")).andExpect(status().is3xxRedirection());
        assertThat(vue().enps()).isNull();
        assertThat(vue().dimensionsConfigurees()).isFalse();
        assertThat(reponseRepository.findDuCollaborateur("BP001", t3)).allSatisfy(r -> assertThat(r.getTheme()).isNull());
    }
}
