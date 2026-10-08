package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.ResultatImportQuestionnaire;
import com.talent360bank.talent360bank.ui.model.ReponsesQuestionnaire;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le fichier de reponses au questionnaire sur le vrai classeur (T3 2026) :
 * src/test/resources/questionnaire/questionnaire-reponses-BP001-BP003.xlsx reprend
 * la feuille, l'en-tete et les reponses du fichier exemple de docs/data, avec de
 * vrais matricules (BP001 a BP003). Importe depuis la page /import, ses reponses
 * s'affichent sur la fiche de BP001, dans l'ordre du fichier, sans theme.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:questionnaire-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class QuestionnaireDatasetTest {

    private static final Path CLASSEUR = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final Path REPONSES = Path.of(
            "src/test/resources/questionnaire/questionnaire-reponses-BP001-BP003.xlsx");

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

    static boolean classeurPresent() {
        return Files.exists(CLASSEUR);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", CLASSEUR.getFileName().toString(), null,
                Files.readAllBytes(CLASSEUR));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        trimestreService.modifierDateReference(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow(),
                LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow());
    }

    @Test
    void les_reponses_importees_s_affichent_sur_la_fiche_de_bp001() throws Exception {
        Map<String, BigDecimal> scoresAvant = scores();
        assertThat(mockMvc.perform(get("/fiche-collaborateur?matricule=BP001")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("Aucun questionnaire importé pour ce trimestre.");

        MvcResult envoi = mockMvc.perform(multipart("/import/questionnaire")
                        .file(new MockMultipartFile("fichier", REPONSES.getFileName().toString(), null,
                                Files.readAllBytes(REPONSES)))
                        .param("trimestre", "2026-3").with(csrf()))
                .andExpect(status().isOk()).andReturn();
        ResultatImportQuestionnaire rapport = (ResultatImportQuestionnaire) envoi.getModelAndView().getModel()
                .get("rapportQuestionnaire");
        assertThat(rapport.statut()).isEqualTo("SUCCES");
        assertThat(rapport.nbCollaborateurs()).isEqualTo(3);
        assertThat(rapport.nbQuestions()).isEqualTo(20);
        // 20 questions, les 4 de la branche du formulaire non suivie sont vides : 16 reponses chacun.
        assertThat(rapport.nbReponses()).isEqualTo(48);
        assertThat(rapport.erreurs()).isEmpty();

        MvcResult page = mockMvc.perform(get("/fiche-collaborateur?matricule=BP001")).andExpect(status().isOk())
                .andReturn();
        ReponsesQuestionnaire vue = (ReponsesQuestionnaire) page.getModelAndView().getModel().get("questionnaire");
        assertThat(vue.trimestreLibelle()).isEqualTo("T3 2026");
        // Ordre du fichier (Q01, Q02...), texte de l'en-tete tel qu'ecrit, reponse telle quelle, theme vide.
        assertThat(vue.reponses()).extracting(ReponsesQuestionnaire.Reponse::code).containsExactly("Q01", "Q02",
                "Q03", "Q04", "Q05", "Q06", "Q07", "Q08", "Q09", "Q10", "Q11", "Q12", "Q13", "Q14", "Q19", "Q20");
        assertThat(vue.reponses()).extracting(ReponsesQuestionnaire.Reponse::theme).containsOnlyNulls();
        assertThat(vue.reponses().get(0)).isEqualTo(new ReponsesQuestionnaire.Reponse("Q01", null, "Direction",
                "Reseau Retail"));
        assertThat(vue.reponses().get(2)).isEqualTo(new ReponsesQuestionnaire.Reponse("Q03", null,
                "Mon manager me donne un retour régulier et utile sur mon travail.", "2"));
        assertThat(vue.reponses().get(12).reponse()).isEqualTo("6");
        // Chaque cellule remplie de la ligne de BP001 est enregistree, et seulement elles : Q15 a Q18
        // (colonnes Q a T, branche du formulaire non suivie) sont vides dans le fichier.
        try (XSSFWorkbook fichierReponses = new XSSFWorkbook(Files.newInputStream(REPONSES))) {
            Row ligne = fichierReponses.getSheetAt(0).getRow(1);
            List<String> remplies = new ArrayList<>();
            for (int colonne = 2; colonne <= 21; colonne++) {
                Cell cellule = ligne.getCell(colonne);
                if (cellule != null && cellule.getCellType() != CellType.BLANK
                        && !new DataFormatter().formatCellValue(cellule).isBlank()) {
                    remplies.add(String.format("Q%02d", colonne - 1));
                }
            }
            assertThat(remplies).doesNotContain("Q15", "Q16", "Q17", "Q18");
            assertThat(vue.reponses()).extracting(ReponsesQuestionnaire.Reponse::code)
                    .containsExactlyElementsOf(remplies);
        }
        assertThat(vue.avecTheme()).isFalse();

        String html = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("id=\"questionnaire-reponses\"", "T3 2026 · réponse du 18/09/2026",
                        "J&#39;ai confiance dans les décisions prises par mon manager.",
                        "Plus de visibilité sur les postes ouverts en interne.")
                .doesNotContain("Aucun questionnaire importé pour ce trimestre.", "<th>Thème</th>");
        // Les reponses de BP002 ne sont pas sur la fiche de BP001.
        assertThat(html).doesNotContain("Des échanges plus réguliers avec mon manager.");

        // Le score d'engagement du classeur (12_VIGILANCE D) ne change pas.
        assertThat(scores()).isEqualTo(scoresAvant);
    }

    private Map<String, BigDecimal> scores() {
        return questionnaireRepository.findByTrimestreAvecCollaborateur(
                        trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow()).stream()
                .collect(Collectors.toMap(q -> q.getCollaborateur().getIdCollaborateur(),
                        q -> q.getScoreEngagement().stripTrailingZeros()));
    }
}
