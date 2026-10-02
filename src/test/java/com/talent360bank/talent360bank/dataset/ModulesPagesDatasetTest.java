package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.Notifications;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CompteNiveau;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les ecrans sur le vrai classeur (T3 2026) : chaque page repond 200 avec ses
 * donnees, et l'accueil montre chaque bloc de 00_DASHBOARD avec les memes
 * chiffres (sauf les plans de developpement, feuille 11 non importee).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:modules-pages-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class ModulesPagesDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

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
    private CollaborateurRepository collaborateurRepository;

    private LecteurXlsx classeur;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        trimestreService.modifierDateReference(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow(),
                LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow());
        classeur = new LecteurXlsx(FICHIER);
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
    }

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static <T> T modele(MvcResult resultat, String nom) {
        return (T) resultat.getModelAndView().getModel().get(nom);
    }

    private int entier(int ligne, String colonne) {
        return (int) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(ligne).get(colonne));
    }

    // --- Accueil = 00_DASHBOARD -------------------------------------------------------------

    @Test
    @Order(1)
    void l_accueil_montre_chaque_bloc_de_00_dashboard_avec_ses_chiffres() throws Exception {
        MvcResult resultat = page("/");
        TableauDeBordView tableau = modele(resultat, "tableau");
        Map<String, String> kpis = new HashMap<>();
        tableau.kpis().forEach(kpi -> kpis.put(kpi.getLabel(), kpi.getValue()));

        // Bloc 1, chiffres cles (lignes 6, 10, 15).
        assertThat(kpis.get("Collaborateurs actifs")).isEqualTo(String.valueOf(entier(6, "A")));
        assertThat(kpis.get("Postes critiques")).isEqualTo(String.valueOf(entier(6, "C")));
        assertThat(kpis.get("Talents validés par le Comité")).isEqualTo(String.valueOf(entier(6, "E")));
        assertThat(kpis.get("Hauts potentiels")).isEqualTo(String.valueOf(entier(6, "G")));
        assertThat(kpis.get("Couverture succession")).startsWith(String.valueOf(entier(10, "A")));
        assertThat(kpis.get("Successeurs Ready Now")).isEqualTo(String.valueOf(entier(10, "C")));
        assertThat(kpis.get("Postes critiques sans successeur")).isEqualTo(String.valueOf(entier(10, "E")));
        assertThat(kpis.get("Compétences en gap prioritaire")).isEqualTo(String.valueOf(entier(10, "G")));
        assertThat(tableau.vigilance()).extracting(CompteNiveau::code, CompteNiveau::nombre)
                .contains(org.assertj.core.groups.Tuple.tuple("ELEVEE", entier(15, "E")),
                        org.assertj.core.groups.Tuple.tuple("MODEREE", entier(15, "G")));

        // Bloc 2, repartition 9-box (lignes 21 a 29).
        Map<String, Integer> attendu = new HashMap<>();
        for (int ligne = 21; ligne <= 29; ligne++) {
            attendu.put(normaliser(classeur.feuille("00_DASHBOARD").get(ligne).get("A")), entier(ligne, "B"));
        }
        assertThat(tableau.neufBox().stream()
                .collect(Collectors.toMap(c -> normaliser(c.libelle()), CaseTableau::nombre))).isEqualTo(attendu);

        // Bloc 3, postes critiques sans successeur (lignes 35 a 49).
        assertThat(tableau.postesSansSuccesseur()).extracting(TableauDeBordView.PosteSansSuccesseur::posteId)
                .containsExactly("PST13");
        String page = html(resultat);
        assertThat(page).contains("Postes critiques sans successeur identifié", "PST13", "Categorie")
                .contains("non disponibles, la feuille 11_DEVELOPMENT_PLAN");
    }

    // --- nouveaux ecrans ----------------------------------------------------------------------

    @Test
    @Order(2)
    void la_liste_des_collaborateurs_filtre_trie_et_pagine() throws Exception {
        ListeCollaborateurs tous = modele(page("/collaborateurs"), "vue");
        assertThat(tous.nbTotal()).isEqualTo(100);
        assertThat(tous.lignes()).hasSize(20);
        assertThat(tous.nbPages()).isEqualTo(5);

        MvcResult talents = page("/collaborateurs?talent=true&tri=PERFORMANCE");
        ListeCollaborateurs vue = modele(talents, "vue");
        assertThat(vue.nbFiltres()).isEqualTo(10);
        assertThat(vue.lignes()).allSatisfy(l -> assertThat(l.estTalent()).isTrue());
        assertThat(vue.criteres().decroissant()).isTrue();
        assertThat(html(talents)).contains("/fiche-collaborateur?matricule=BP019");

        ListeCollaborateurs page3 = modele(page("/collaborateurs?page=3&tri=MATRICULE&ordre=asc"), "vue");
        assertThat(page3.lignes().get(0).matricule()).isEqualTo("BP041");
        assertThat(html(page("/collaborateurs?page=2"))).contains("page=3");

        ListeCollaborateurs filtre = modele(page("/collaborateurs?case=9&vivier=RELEVE&q=khatib"), "vue");
        assertThat(filtre.lignes()).extracting(ListeCollaborateurs.LigneCollaborateur::matricule).contains("BP019");
        assertThat(filtre.lignes()).allSatisfy(l -> {
            assertThat(l.neufBox().numero()).isEqualTo(9);
            assertThat(l.viviers()).extracting(ListeCollaborateurs.VivierRef::code).contains("RELEVE");
            assertThat(l.nomComplet().toLowerCase(Locale.ROOT)).contains("khatib");
        });

        // Un critere invalide : message, pas d'erreur 500.
        MvcResult invalide = page("/collaborateurs?case=12");
        assertThat((String) modele(invalide, "erreur")).contains("9-box");
    }

    @Test
    @Order(3)
    void le_dashboard_dg_les_competences_les_notifications_et_la_campagne() throws Exception {
        MvcResult dg = page("/dashboard-dg");
        TableauDeBordDg vueDg = modele(dg, "vue");
        assertThat(vueDg.postesCritiques()).hasSize(15);
        assertThat(vueDg.topTalents()).hasSize(8);
        assertThat(html(dg)).contains("Top talents", "PST13");

        MvcResult competences = page("/competences?poste=PST01");
        SyntheseCompetences synthese = modele(competences, "vue");
        assertThat(synthese.nbCollaborateurs()).isEqualTo(4);
        assertThat(html(competences)).contains("progress-bar", "Leadership");
        SyntheseCompetences toutes = modele(page("/competences"), "vue");
        assertThat(toutes.nbGapsPrioritaires()).isEqualTo(entier(10, "G"));

        Notifications notifications = modele(page("/notifications"), "vue");
        assertThat(notifications.total()).isEqualTo(23);
        assertThat(notifications.alertes()).hasSize(10);

        MvcResult campagne = page("/campagne");
        SuiviCampagne suivi = modele(campagne, "vue");
        assertThat(suivi.total().nbEvalues()).isEqualTo(100);
        assertThat(html(campagne)).contains("entite=DIR");
        String code = suivi.lignes().get(0).code();
        SuiviCampagne detail = modele(page("/campagne?entite=" + code), "vue");
        assertThat(detail.entite().code()).isEqualTo(code);
        assertThat((String) modele(page("/campagne?entite=INCONNUE"), "erreur")).contains("INCONNUE");
    }

    @Test
    @Order(4)
    void les_viviers_et_les_postes_critiques_montrent_les_donnees_du_jour() throws Exception {
        MvcResult viviers = page("/viviers");
        java.util.List<SyntheseVivier> syntheses = modele(viviers, "syntheses");
        assertThat(syntheses).extracting(SyntheseVivier::code)
                .containsExactly("COMMERCIAL", "DIGITAL", "EXPERTISE", "MANAGEMENT", "RISQUES", "RELEVE");
        assertThat(html(viviers)).contains("Gaps identifiés", "Juridique bancaire (3)");

        String postes = html(page("/postes-critiques"));
        // Tous les successeurs de PST01 (09_SUCCESSION), avec leur readiness.
        assertThat(postes).contains("BP035", "BP013", "BP019", "BP005", "Aucun écart");
    }

    // --- echappement -----------------------------------------------------------------------------

    @Test
    @Order(5)
    void le_texte_des_cellules_est_echappe() throws Exception {
        Collaborateur bp001 = collaborateurRepository.findById("BP001").orElseThrow();
        bp001.setNom("<script>alert(1)</script>");
        collaborateurRepository.save(bp001);

        String page = html(page("/collaborateurs?q=BP001"));
        assertThat(page).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
