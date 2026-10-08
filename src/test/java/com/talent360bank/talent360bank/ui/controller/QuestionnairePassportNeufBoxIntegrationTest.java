package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.ReponseQuestionnaire;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ReponseQuestionnaireRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportQuestionnaireService;
import com.talent360bank.talent360bank.service.resultat.ResultatImportQuestionnaire;
import com.talent360bank.talent360bank.ui.model.ReponsesQuestionnaire;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reponses au questionnaire (import du fichier de reponses, section de la fiche), Talent
 * Passport sans engagement, bouton d'impression du panneau 9-Box, echappement.
 *
 * <p>Population de T1 2026 : Q01 et Q02 (actifs, notes et score d'engagement importe), Q03
 * sans questionnaire. Le fichier de reponses a les colonnes du vrai fichier
 * (docs/data/"... Questionnaire d'engagement collaborateur (réponses)").
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:questionnaire-passport;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class QuestionnairePassportNeufBoxIntegrationTest {

    private static final String XSS = "<script>alert(1)</script>";

    /** En-tete du vrai fichier de reponses (espaces et doublons compris). */
    private static final List<String> ENTETE = List.of("Horodateur", "Matricule", "Direction", "  Ancienneté  ",
            "  Mon manager me donne un retour régulier et utile sur mon travail.  ",
            "J'ai confiance dans les décisions prises par mon manager. ",
            "Mes contributions sont reconnues à leur juste valeur.",
            " La communication entre les équipes et les directions fonctionne bien.",
            " Sur une échelle de 0 à 10, recommanderiez-vous la Banque comme employeur à un proche ?",
            "Qu'est-ce qui améliorerait le plus votre expérience de travail au sein de la banque ? ",
            " J'ai accès aux formations dont j'ai besoin pour progresser.",
            "  J'ai accès aux formations dont j'ai besoin pour progresser.   2");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private EntiteRepository entiteRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PotentielRepository potentielRepository;
    @Autowired
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private ReponseQuestionnaireRepository reponseRepository;
    @Autowired
    private ImportExcelRepository importExcelRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;

    private Trimestre t1;

    @BeforeAll
    void peupler() {
        t1 = new Trimestre();
        t1.setAnnee(2026);
        t1.setNumero(1);
        t1.setDateReference(Trimestre.dernierJour(2026, 1));
        t1 = trimestreRepository.save(t1);
        parametreRepository.save(Parametre.parDefaut(t1));
        Entite retail = entiteRepository.save(new Entite("Reseau Retail", TypeEntite.DIRECTION, null));
        for (String id : List.of("Q01", "Q02", "Q03")) {
            Collaborateur c = new Collaborateur();
            c.setIdCollaborateur(id);
            c.setNom("Nom" + id);
            c.setPrenom("Prenom" + id);
            c.setDateEntree(LocalDate.of(2018, 1, 1));
            c.setEntite(retail);
            c.setStatut(StatutCollaborateur.ACTIF);
            c = collaborateurRepository.save(c);
            BigDecimal note = new BigDecimal("90");
            performanceRepository.save(new Performance(c, t1, note, note, note, note, note));
            potentielRepository.save(new Potentiel(c, t1, note, note, note, note, note, note, note));
            QuestionnaireEngagement score = new QuestionnaireEngagement();
            score.setCollaborateur(c);
            score.setTrimestre(t1);
            score.setScoreEngagement(new BigDecimal("77.00"));
            score.setDateReponse(LocalDate.of(2026, 3, 1));
            questionnaireRepository.save(score);
        }
        calculTrimestreService.calculer(t1);
    }

    private String html(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** Un fichier de reponses : l'en-tete du vrai fichier, puis une ligne par tableau de valeurs. */
    private static byte[] fichier(Object[]... lignes) throws Exception {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Réponses au formulaire 1");
            CellStyle date = classeur.createCellStyle();
            date.setDataFormat(classeur.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            Row entete = feuille.createRow(0);
            for (int i = 0; i < ENTETE.size(); i++) {
                entete.createCell(i).setCellValue(ENTETE.get(i));
            }
            for (int l = 0; l < lignes.length; l++) {
                Row ligne = feuille.createRow(l + 1);
                for (int i = 0; i < lignes[l].length; i++) {
                    Object valeur = lignes[l][i];
                    if (valeur instanceof LocalDateTime horodatage) {
                        ligne.createCell(i).setCellValue(horodatage);
                        ligne.getCell(i).setCellStyle(date);
                    } else if (valeur instanceof Number nombre) {
                        ligne.createCell(i).setCellValue(nombre.doubleValue());
                    } else if (valeur != null) {
                        ligne.createCell(i).setCellValue(valeur.toString());
                    }
                }
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    private MvcResult envoyer(byte[] contenu, String trimestre) throws Exception {
        return mockMvc.perform(multipart("/import/questionnaire")
                        .file(new MockMultipartFile("fichier", "reponses.xlsx", null, contenu))
                        .param("trimestre", trimestre).with(csrf()))
                .andExpect(status().isOk()).andReturn();
    }

    // --- 5. reponses au questionnaire -------------------------------------------------------

    @Test
    @Order(1)
    void avant_tout_import_la_fiche_dit_qu_aucun_questionnaire_n_est_importe() throws Exception {
        String fiche = html("/fiche-collaborateur?matricule=Q01");
        assertThat(fiche).contains("id=\"questionnaire\"", "Réponses au questionnaire",
                "Aucun questionnaire importé pour ce trimestre.");
        assertThat(reponseRepository.count()).isZero();
    }

    @Test
    @Order(2)
    void l_import_enregistre_les_reponses_brutes_et_ne_touche_pas_au_score() throws Exception {
        byte[] contenu = fichier(
                new Object[]{LocalDateTime.of(2026, 3, 18, 15, 9, 38), "Q01", "Reseau Retail", "5 ans", 2, 3, 4, 1, 6,
                        XSS, null, 5},
                new Object[]{LocalDateTime.of(2026, 3, 18, 16, 0), "q02", "Reseau Retail", "2 ans", 5, 5, 5, 5, 9,
                        "Plus de formation", 4, null},
                new Object[]{LocalDateTime.of(2026, 3, 19, 9, 0), "INCONNU", "X", "Y", 1, 1, 1, 1, 1, "z", 1, 1},
                new Object[]{LocalDateTime.of(2026, 3, 19, 10, 0), null, "X", "Y", 1, 1, 1, 1, 1, "z", 1, 1});
        MvcResult resultat = envoyer(contenu, "2026-1");
        ResultatImportQuestionnaire rapport = (ResultatImportQuestionnaire) resultat.getModelAndView().getModel()
                .get("rapportQuestionnaire");
        assertThat(rapport.statut()).isEqualTo("PARTIEL");
        assertThat(rapport.nbCollaborateurs()).isEqualTo(2);
        assertThat(rapport.nbQuestions()).isEqualTo(10);
        // Q01 : 10 questions dont 1 vide ; Q02 (matricule en minuscules) : 10 dont 1 vide.
        assertThat(rapport.nbReponses()).isEqualTo(18);
        assertThat(rapport.erreurs()).anyMatch(e -> e.contains("INCONNU")).anyMatch(e -> e.contains("matricule absent"));
        assertThat(resultat.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("id=\"rapport-questionnaire\"", "Questionnaire — reponses.xlsx");

        // Question (code, texte tel qu'ecrit) et reponse, telles quelles ; aucun theme deduit.
        List<ReponseQuestionnaire> q01 = reponseRepository.findDuCollaborateur("Q01", t1);
        assertThat(q01).extracting(ReponseQuestionnaire::getCodeQuestion)
                .containsExactly("Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08", "Q10");
        assertThat(q01.get(2).getQuestion()).isEqualTo("Mon manager me donne un retour régulier et utile sur mon travail.");
        assertThat(q01.get(2).getReponse()).isEqualTo("2");
        assertThat(q01).extracting(ReponseQuestionnaire::getTheme).containsOnlyNulls();
        // Le suffixe " 2" du formulaire fait partie du texte ecrit : il est garde.
        assertThat(q01.get(8).getQuestion())
                .isEqualTo("J'ai accès aux formations dont j'ai besoin pour progresser.   2");
        assertThat(q01.get(0).getDateReponse()).isEqualTo(LocalDateTime.of(2026, 3, 18, 15, 9, 38));

        // Le score d'engagement importe avec le classeur ne change pas.
        assertThat(questionnaireRepository.findByTrimestreAvecCollaborateur(t1))
                .allSatisfy(q -> assertThat(q.getScoreEngagement()).isEqualByComparingTo("77.00"));
        assertThat(importExcelRepository.findAllRecentsDabord().get(0).getSource())
                .isEqualTo(ImportQuestionnaireService.SOURCE);
    }

    @Test
    @Order(3)
    void la_fiche_montre_les_reponses_dans_l_ordre_du_fichier_echappees() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/fiche-collaborateur?matricule=Q01")).andReturn();
        ReponsesQuestionnaire vue = (ReponsesQuestionnaire) resultat.getModelAndView().getModel().get("questionnaire");
        assertThat(vue.reponses()).extracting(ReponsesQuestionnaire.Reponse::code)
                .containsExactly("Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08", "Q10");
        assertThat(vue.reponses()).extracting(ReponsesQuestionnaire.Reponse::theme).containsOnlyNulls();
        String fiche = resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Un seul tableau, sans regroupement ; tous les themes sont vides : pas de colonne Theme.
        assertThat(vue.avecTheme()).isFalse();
        assertThat(fiche).contains("Réponses au questionnaire", "T1 2026 · réponse du 18/03/2026",
                        "<th>Code</th><th>Question</th><th>Réponse</th>",
                        "Mon manager me donne un retour régulier et utile sur mon travail.",
                        "&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<h4>Management</h4>", "questionnaire-theme", "<th>Thème</th>")
                .doesNotContain(XSS, "Aucun questionnaire importé pour ce trimestre.");
        // Q03 n'a pas repondu.
        assertThat(html("/fiche-collaborateur?matricule=Q03")).contains("Aucun questionnaire importé pour ce trimestre.");
    }

    @Test
    @Order(4)
    void le_selecteur_de_trimestre_de_la_section_change_les_reponses() throws Exception {
        Trimestre t2 = new Trimestre();
        t2.setAnnee(2026);
        t2.setNumero(2);
        t2.setDateReference(Trimestre.dernierJour(2026, 2));
        trimestreRepository.save(t2);
        String fiche = html("/fiche-collaborateur?matricule=Q01&trimestre=2026-1&questionnaire=2026-2");
        assertThat(fiche).contains("name=\"questionnaire\"", "<option value=\"2026-2\" selected=\"selected\">T2 2026</option>",
                "Aucun questionnaire importé pour ce trimestre.");
        assertThat(html("/fiche-collaborateur?matricule=Q01&trimestre=2026-1&questionnaire=2026-1"))
                .doesNotContain("Aucun questionnaire importé pour ce trimestre.");
        mockMvc.perform(get("/fiche-collaborateur?matricule=Q01&questionnaire=2031-1")).andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void reimporter_remplace_les_reponses_d_un_collaborateur() throws Exception {
        envoyer(fichier(new Object[]{LocalDateTime.of(2026, 3, 25, 9, 0), "Q01", "Reseau Retail", "5 ans", 4}),
                "2026-1");
        assertThat(reponseRepository.findDuCollaborateur("Q01", t1)).extracting(ReponseQuestionnaire::getReponse)
                .containsExactly("Reseau Retail", "5 ans", "4");
        // Q02, absent du nouveau fichier, garde ses reponses.
        assertThat(reponseRepository.findDuCollaborateur("Q02", t1)).hasSize(9);
    }

    @Test
    @Order(6)
    void un_fichier_sans_matricule_ou_un_trimestre_absent_est_refuse() throws Exception {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            classeur.createSheet("X").createRow(0).createCell(0).setCellValue("Nom");
            classeur.write(sortie);
            ResultatImportQuestionnaire rapport = (ResultatImportQuestionnaire) envoyer(sortie.toByteArray(), "2026-1")
                    .getModelAndView().getModel().get("rapportQuestionnaire");
            assertThat(rapport.statut()).isEqualTo("ECHEC");
            assertThat(rapport.message()).contains("Matricule");
        }
        assertThat((String) envoyer(fichier(), "2031-4").getModelAndView().getModel().get("erreurQuestionnaire"))
                .contains("trimestre");
        assertThat(html("/import")).contains("id=\"formulaire-questionnaire\"", "action=\"/import/questionnaire\"");
    }

    @Test
    @Order(7)
    void le_vrai_fichier_de_reponses_est_lu_et_ses_matricules_inconnus_ecartes() throws Exception {
        Path vrai = Path.of("docs/data/TALENT 360 BANK — Questionnaire d'engagement collaborateur (réponses) - Copy.xlsx");
        if (!Files.exists(vrai)) {
            return;
        }
        ResultatImportQuestionnaire rapport = (ResultatImportQuestionnaire) envoyer(Files.readAllBytes(vrai), "2026-1")
                .getModelAndView().getModel().get("rapportQuestionnaire");
        // Le fichier fourni a des matricules de test ("ertyui"...) : lu (20 questions), rien d'enregistre.
        assertThat(rapport.nbQuestions()).isEqualTo(20);
        assertThat(rapport.statut()).isEqualTo("ECHEC");
        assertThat(rapport.erreurs()).hasSize(4);
    }

    @Test
    @Order(11)
    void la_colonne_theme_revient_des_qu_une_reponse_a_un_theme() throws Exception {
        Collaborateur q03 = collaborateurRepository.findById("Q03").orElseThrow();
        reponseRepository.save(new ReponseQuestionnaire(q03, t1, "Q01", 1, null, "Direction", "Reseau Retail", null));
        reponseRepository.save(new ReponseQuestionnaire(q03, t1, "Q02", 2, "Management",
                "Mon manager me donne un retour régulier et utile sur mon travail.", "4", null));
        String fiche = html("/fiche-collaborateur?matricule=Q03&trimestre=2026-1");
        assertThat(fiche).contains("<th>Code</th><th>Thème</th><th>Question</th><th>Réponse</th>",
                "<td>Management</td>");
        // Q01 (sans theme) garde sa cellule Theme, vide : les colonnes restent alignees.
        assertThat(fiche).containsPattern("<td class=\"mono\">Q01</td>\\s*<td></td>\\s*<td>Direction</td>");
        // Les autres fiches, sans theme, n'ont toujours pas la colonne.
        assertThat(html("/fiche-collaborateur?matricule=Q01&trimestre=2026-1")).contains("<th>Code</th><th>Question</th>")
                .doesNotContain("<th>Thème</th>");
    }

    // --- 4. Talent Passport sans engagement ---------------------------------------------------

    @Test
    @Order(8)
    void le_talent_passport_n_affiche_plus_l_engagement() throws Exception {
        String fiche = html("/fiche-collaborateur?matricule=Q01");
        assertThat(fiche).contains("Performance /100", "Potentiel /100")
                .doesNotContain("Engagement /100", "Questionnaire d'engagement", "Score d'engagement",
                        "id=\"engagement\"", "77.00", "Pas de questionnaire d'engagement");
        // Ailleurs, l'engagement reste : moyenne du tableau de bord.
        assertThat(html("/")).contains("❤️ Engagement (3 réponses)", "77.00");
    }

    // --- 3. impression de la liste d'une case 9-Box ---------------------------------------------

    @Test
    @Order(9)
    void le_panneau_9box_a_un_bouton_imprimer_et_un_en_tete_d_impression() throws Exception {
        String page = html("/9box?case=9&q=Q0&tri=PERFORMANCE");
        assertThat(page).contains("<body class=\"impression-9box\">", "class=\"btn ghost neufbox-imprimer\"",
                "🖨 Imprimer", "impression-entete", "T1 2026 · imprimé le ",
                "Recherche : « Q0 » · Tri : Performance", "id=\"panneau-matrice\"", "id=\"panneau-seuils\"");
        // Un bouton par panneau (9 cases), a cote du champ de recherche.
        assertThat(page.split("neufbox-imprimer", -1)).hasSize(10);
        assertThat(page.indexOf("neufbox-imprimer")).isGreaterThan(page.indexOf("neufbox-recherche"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
        assertThat(css).contains("@media print", ".impression-9box #sidebar", ".impression-9box #panneau-matrice");
        assertThat(Files.readString(Path.of("src/main/resources/static/js/neufbox.js"))).contains("window.print()");
    }

    @Test
    @Order(10)
    void la_recherche_imprimee_est_echappee() throws Exception {
        String page = mockMvc.perform(get("/9box").param("case", "9").param("q", XSS)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).doesNotContain(XSS).contains("Recherche : « &lt;script&gt;alert(1)&lt;/script&gt; »");
    }
}
