package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.MatriceNeufBox;
import com.talent360bank.talent360bank.ui.model.MatriceNeufBox.CaseMatrice;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sur le vrai classeur (T3 2026) : aucun lien d'une page HTML ne mene a l'API,
 * et la matrice 9-Box place chaque case au bon endroit avec les effectifs de
 * 00_DASHBOARD. Ignore sans le classeur, qui n'est pas versionne.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:liens-matrice-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class LiensEtMatriceDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]*)\"");
    private static final Pattern CASE = Pattern.compile("data-case=\"(\\d)\"");

    /** Chaque ecran, avec des filtres qui font apparaitre des liens de chaque sorte. */
    private static final List<String> PAGES = List.of(
            "/", "/dashboard-dg", "/collaborateurs", "/collaborateurs?talent=true&tri=PERFORMANCE",
            "/collaborateurs?page=2", "/9box", "/viviers", "/postes-critiques", "/competences",
            "/competences?poste=PST01", "/comite-talent", "/fiche-collaborateur",
            "/fiche-collaborateur?matricule=BP019&trimestre=2026-3", "/alertes", "/notifications", "/campagne",
            "/campagne?entite=DIR:RESEAU_RETAIL", "/import", "/parametres");

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

    private static List<String> liens(String html) {
        List<String> liens = new ArrayList<>();
        Matcher m = HREF.matcher(html);
        while (m.find()) {
            liens.add(m.group(1).replace("&amp;", "&"));
        }
        return liens;
    }

    // --- 1. liens --------------------------------------------------------------------

    @Test
    @Order(1)
    void aucun_lien_d_une_page_ne_mene_a_l_api() throws Exception {
        Map<String, List<String>> versApi = new HashMap<>();
        int total = 0;
        for (String url : PAGES) {
            List<String> liens = liens(html(page(url)));
            total += liens.size();
            List<String> api = liens.stream().filter(l -> l.startsWith("/api/") || l.equals("/api")).toList();
            if (!api.isEmpty()) {
                versApi.put(url, api);
            }
        }
        assertThat(versApi).as("liens vers l'API").isEmpty();
        assertThat(total).isGreaterThan(500);
    }

    @Test
    @Order(2)
    void une_personne_mene_a_sa_fiche_et_un_poste_a_sa_ligne_pour_le_trimestre() throws Exception {
        List<String> alertes = liens(html(page("/alertes")));
        assertThat(alertes).contains("/postes-critiques?trimestre=2026-3#poste-PST13",
                "/comite-talent?trimestre=2026-3&statut=EN_ATTENTE");
        // Vigilance elevee et gaps : la fiche (page) de la personne, pour ce trimestre.
        assertThat(alertes).anyMatch(l -> l.matches("/fiche-collaborateur\\?matricule=BP\\d+&trimestre=2026-3"));
        assertThat(liens(html(page("/notifications")))).contains("/postes-critiques?trimestre=2026-3#poste-PST13");
        assertThat(liens(html(page("/")))).contains("/postes-critiques?trimestre=2026-3#poste-PST13");
        assertThat(liens(html(page("/dashboard-dg")))).contains("/fiche-collaborateur?matricule=BP035&trimestre=2026-3",
                "/postes-critiques?trimestre=2026-3#poste-PST01");
        assertThat(liens(html(page("/postes-critiques")))).contains("/fiche-collaborateur?matricule=BP013&trimestre=2026-3");
        assertThat(html(page("/postes-critiques"))).contains("id=\"poste-PST01\"", "id=\"poste-PST13\"");
        assertThat(liens(html(page("/viviers")))).anyMatch(l -> l.startsWith("/fiche-collaborateur?matricule=BP"));
        assertThat(liens(html(page("/collaborateurs?q=BP001")))).contains("/fiche-collaborateur?matricule=BP001&trimestre=2026-3");
        // La fiche suit le trimestre du lien.
        assertThat(html(page("/fiche-collaborateur?matricule=BP019&trimestre=2026-3"))).contains("T3 2026");
    }

    // --- 2. matrice 9-Box ----------------------------------------------------------------

    @Test
    @Order(3)
    void les_cases_ont_les_effectifs_de_00_dashboard_a_la_bonne_place() throws Exception {
        MvcResult resultat = page("/9box");
        MatriceNeufBox matrice = (MatriceNeufBox) resultat.getModelAndView().getModel().get("matrice");

        Map<String, Integer> attendus = new HashMap<>();
        for (int ligne = 21; ligne <= 29; ligne++) {
            Map<String, String> cellules = classeur.feuille("00_DASHBOARD").get(ligne);
            attendus.put(normaliser(cellules.get("A")), (int) Double.parseDouble(cellules.get("B")));
        }
        for (CaseMatrice c : matrice.cases()) {
            assertThat(c.nombre()).as(c.libelle()).isEqualTo(attendus.get(normaliser(c.libelle())));
        }
        assertThat(matrice.total()).isEqualTo(100);

        // Ligne du haut = performance elevee ; colonne de droite = potentiel eleve.
        assertThat(matrice.cases()).extracting(CaseMatrice::numero).containsExactly(7, 8, 9, 4, 5, 6, 1, 2, 3);
        CaseMatrice hautDroite = matrice.cases().get(2);
        assertThat(normaliser(hautDroite.libelle())).isEqualTo("talent cle");
        assertThat(hautDroite.nombre()).isEqualTo(10);
        assertThat(hautDroite.zone()).isEqualTo("excellent");
        CaseMatrice basGauche = matrice.cases().get(6);
        assertThat(basGauche.numero()).isEqualTo(1);
        assertThat(basGauche.nombre()).isEqualTo(attendus.get("a surveiller"));
        assertThat(basGauche.zone()).isEqualTo("risque");

        // Meme ordre dans le HTML.
        String html = html(resultat);
        List<String> ordre = new ArrayList<>();
        Matcher m = CASE.matcher(html);
        while (m.find()) {
            ordre.add(m.group(1));
        }
        assertThat(ordre).containsExactly("7", "8", "9", "4", "5", "6", "1", "2", "3");
        assertThat(html).contains("Potentiel", "Performance", "Total : 100 collaborateur(s) placé(s)");
    }

    @Test
    @Order(4)
    void une_case_de_plus_de_6_montre_6_noms_et_plus_n_autres() throws Exception {
        MvcResult resultat = page("/9box");
        MatriceNeufBox matrice = (MatriceNeufBox) resultat.getModelAndView().getModel().get("matrice");
        CaseMatrice talentCle = matrice.cases().get(2);
        assertThat(talentCle.nombre()).isGreaterThan(MatriceNeufBox.NOMS_MAX);
        assertThat(talentCle.membres()).hasSize(MatriceNeufBox.NOMS_MAX);
        assertThat(talentCle.nbAutres()).isEqualTo(talentCle.nombre() - MatriceNeufBox.NOMS_MAX);
        assertThat(talentCle.lienListe()).isEqualTo("/collaborateurs?trimestre=2026-3&case=9");

        String html = html(resultat);
        assertThat(html).contains("+" + talentCle.nbAutres() + " autres");
        assertThat(liens(html)).contains("/collaborateurs?trimestre=2026-3&case=9");
        // La liste ainsi ouverte a bien l'effectif de la case.
        assertThat(html(page("/collaborateurs?trimestre=2026-3&case=9"))).contains("10 collaborateur(s) sur 100");

        // Une case a 6 ou moins : tous les noms, pas de "+N autres".
        CaseMatrice petite = matrice.cases().stream().filter(c -> c.nombre() > 0 && c.nombre() <= 6)
                .findFirst().orElseThrow();
        assertThat(petite.membres()).hasSize(petite.nombre());
        assertThat(petite.nbAutres()).isZero();

        // L'accueil et le dashboard DG utilisent le meme composant, sans les noms.
        for (String url : List.of("/", "/dashboard-dg")) {
            String autre = html(page(url));
            assertThat(autre).contains("data-case=\"9\"", "matrice9-excellent")
                    .doesNotContain("matrice9-nom\"");
        }
    }

    @Test
    @Order(5)
    void les_noms_de_la_matrice_sont_echappes() throws Exception {
        MatriceNeufBox matrice = (MatriceNeufBox) page("/9box").getModelAndView().getModel().get("matrice");
        String matricule = matrice.cases().get(2).membres().get(0).matricule();
        Collaborateur collaborateur = collaborateurRepository.findById(matricule).orElseThrow();
        collaborateur.setPrenom("<img src=x onerror=alert(1)>");
        collaborateurRepository.save(collaborateur);

        String html = html(page("/9box"));
        assertThat(html).doesNotContain("<img src=x onerror=alert(1)>")
                .contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
