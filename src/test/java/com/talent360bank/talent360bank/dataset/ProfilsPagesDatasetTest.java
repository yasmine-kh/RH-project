package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.ui.model.MatriceNeufBox;
import com.talent360bank.talent360bank.ui.model.Organigramme;
import com.talent360bank.talent360bank.ui.model.Organigramme.Branche;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import com.talent360bank.talent360bank.ui.service.VueEntiteViewService;
import com.talent360bank.talent360bank.ui.service.ProfilsPagesService.PageVueEntite;
import com.talent360bank.talent360bank.ui.service.ProfilsPagesService.PageVueManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vue manager, Vue entite, selecteur de profil et liens croises sur le vrai
 * classeur (T3 2026). Attendus lus dans 01_COLLABORATEURS (Manager_ID en N,
 * Direction en H, Departement en I). Ignore sans le classeur.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profils-pages-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class ProfilsPagesDatasetTest {

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
    private EntiteRepository entiteRepository;
    @Autowired
    private VueEntiteViewService vueEntiteViewService;
    @Autowired
    private TalentService talentService;

    private List<Map<String, String>> collaborateurs;

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
        collaborateurs = new LecteurXlsx(FICHIER).feuille("01_COLLABORATEURS").values().stream()
                .filter(l -> l.getOrDefault("A", "").matches("BP\\d+")).toList();
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

    /** Le manager qui a la plus grande equipe dans 01_COLLABORATEURS!N. */
    private Map.Entry<String, Long> plusGrandeEquipe() {
        return collaborateurs.stream().map(l -> l.getOrDefault("N", "")).filter(m -> !m.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey())).orElseThrow();
    }

    // --- Vue manager ---------------------------------------------------------------------

    @Test
    void la_vue_manager_a_l_equipe_de_01_collaborateurs() throws Exception {
        Map.Entry<String, Long> equipe = plusGrandeEquipe();
        MvcResult resultat = page("/managers/" + equipe.getKey() + "?trimestre=2026-3");
        PageVueManager vue = modele(resultat, "page");

        assertThat(vue.vue().synthese().effectif()).isEqualTo(equipe.getValue().intValue());
        Set<String> attendus = collaborateurs.stream().filter(l -> equipe.getKey().equals(l.get("N")))
                .map(l -> l.get("A")).collect(Collectors.toSet());
        assertThat(vue.vue().membres()).extracting(m -> m.matricule()).containsExactlyInAnyOrderElementsOf(attendus);
        MatriceNeufBox matrice = vue.matrice();
        assertThat(matrice.total()).isEqualTo(equipe.getValue().intValue());
        assertThat(matrice.cases()).extracting(MatriceNeufBox.CaseMatrice::numero)
                .containsExactly(7, 8, 9, 4, 5, 6, 1, 2, 3);
        // Readiness du poste cible pour chaque membre qui a un score (tous dans le classeur).
        assertThat(vue.cibles()).hasSize(attendus.size());

        String html = html(resultat);
        String unMembre = attendus.iterator().next();
        assertThat(html).contains("/fiche-collaborateur?matricule=" + unMembre + "&amp;trimestre=2026-3",
                "9-Box de l'équipe", "data-case=\"9\"");
        assertThat(html(page("/managers?trimestre=2026-3"))).contains("/managers/" + equipe.getKey());
    }

    // --- Vue entite ------------------------------------------------------------------------

    @Test
    void la_vue_entite_compte_le_sous_arbre_et_accepte_les_codes_avec_des_barres() throws Exception {
        long retail = collaborateurs.stream().filter(l -> "Reseau Retail".equals(l.get("H"))).count();
        PageVueEntite direction = modele(page("/entites?code=DIR:RESEAU_RETAIL&trimestre=2026-3"), "page");
        assertThat(direction.vue().synthese().effectif()).isEqualTo((int) retail);
        assertThat(direction.matrice().total()).isEqualTo((int) retail);
        assertThat(direction.chemin()).extracting(c -> c.valeur()).containsExactly("DIR:RESEAU_RETAIL");

        // Un departement : son code contient une barre.
        String departement = collaborateurs.stream().map(l -> l.get("I")).filter(i -> i.startsWith("Reseau Retail"))
                .findFirst().orElseThrow();
        String code = "DIR:RESEAU_RETAIL/DEP:" + departement.toUpperCase().replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        long effectif = collaborateurs.stream().filter(l -> departement.equals(l.get("I"))).count();
        MvcResult resultat = page("/entites?code=" + code + "&trimestre=2026-3");
        PageVueEntite vue = modele(resultat, "page");
        assertThat(vue.vue().entite().code()).isEqualTo(code);
        assertThat(vue.vue().synthese().effectif()).isEqualTo((int) effectif);
        // Fil d'Ariane cliquable : la direction puis le departement.
        assertThat(vue.chemin()).extracting(c -> c.valeur()).containsExactly("DIR:RESEAU_RETAIL", code);
        String html = html(resultat);
        assertThat(html).contains("/entites?code=DIR:RESEAU_RETAIL&amp;trimestre=2026-3");
        // Les cases de la matrice menent a la liste du sous-arbre.
        assertThat(vue.matrice().cases().get(2).lien()).contains("/collaborateurs?", "entite=", "case=9");
        // Les entites filles sont cliquables.
        vue.vue().enfants().forEach(enfant -> assertThat(html).contains(enfant.libelle()));
        assertThat(html(page("/entites?trimestre=2026-3"))).contains("/entites?code=DIR:RESEAU_RETAIL");
    }

    // --- 404 ---------------------------------------------------------------------------------

    @Test
    void un_matricule_ou_un_code_inconnu_donne_la_page_404_en_francais() throws Exception {
        for (String url : List.of("/managers/BP999", "/entites?code=DIR:INCONNUE", "/managers/BP001?trimestre=2099-1")) {
            String html = mockMvc.perform(get(url)).andExpect(status().isNotFound()).andReturn()
                    .getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(url).contains("Page introuvable").doesNotContain("Exception", "at com.");
        }
        // Un collaborateur qui n'est pas manager.
        String nonManager = collaborateurs.stream().map(l -> l.get("A"))
                .filter(id -> collaborateurs.stream().noneMatch(l -> id.equals(l.get("N")))).findFirst().orElseThrow();
        mockMvc.perform(get("/managers/" + nonManager)).andExpect(status().isNotFound());
    }

    // --- selecteur de profil ----------------------------------------------------------------

    @Test
    void le_selecteur_est_sur_chaque_page_avec_les_options_des_donnees() throws Exception {
        long managers = collaborateurs.stream().map(l -> l.getOrDefault("N", "")).filter(m -> !m.isBlank())
                .distinct().count();
        for (String url : List.of("/", "/dashboard-dg", "/collaborateurs", "/9box", "/viviers", "/postes-critiques",
                "/competences", "/comite-talent", "/fiche-collaborateur", "/alertes", "/notifications", "/campagne",
                "/import", "/parametres", "/managers", "/entites", "/managers/" + plusGrandeEquipe().getKey(),
                "/entites?code=DIR:RESEAU_RETAIL")) {
            String html = html(page(url));
            assertThat(html).as(url).contains("id=\"profil-form\"", "action=\"/profil\"", "Comité Talent");
            // Vue RH : pas de personne a choisir, donc pas de liste.
            assertThat(html).as(url).doesNotContain("<datalist", "profils-entites");
        }
        // La liste de la personne a choisir n'apparait que pour son profil.
        MockHttpSession manager = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=MANAGER").session(manager));
        MockHttpSession collaborateur = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=COLLABORATEUR").session(collaborateur));
        for (String url : List.of("/", "/9box", "/entites")) {
            String m = mockMvc.perform(get(url).session(manager)).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(options(m, "profils-managers")).as(url).isEqualTo((int) managers);
            String c = mockMvc.perform(get(url).session(collaborateur)).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(options(c, "profils-collaborateurs")).as(url).isEqualTo(collaborateurs.size());
        }
        // Sans profil choisi, la session est en vue RH.
        assertThat(html(page("/managers/" + plusGrandeEquipe().getKey())))
                .containsPattern("<option value=\"RH\" selected=\"selected\">");
    }

    private static int options(String html, String liste) {
        int debut = html.indexOf("<datalist id=\"" + liste + "\">");
        int fin = html.indexOf("</datalist>", debut);
        Matcher m = Pattern.compile("<option ").matcher(html.substring(debut, fin));
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    void le_selecteur_ouvre_la_page_du_profil_pour_le_trimestre() throws Exception {
        String manager = plusGrandeEquipe().getKey();
        mockMvc.perform(get("/profil?profil=RH&trimestre=2026-3")).andExpect(redirectedUrl("/?trimestre=2026-3"));
        mockMvc.perform(get("/profil?profil=COMITE&trimestre=2026-3"))
                .andExpect(redirectedUrl("/comite-talent?trimestre=2026-3"));
        mockMvc.perform(get("/profil?profil=COLLABORATEUR&cible=BP001&trimestre=2026-3"))
                .andExpect(redirectedUrl("/fiche-collaborateur?matricule=BP001&trimestre=2026-3"));
        mockMvc.perform(get("/profil?profil=MANAGER&cible=" + manager + "&trimestre=2026-3"))
                .andExpect(redirectedUrl("/managers/" + manager + "?trimestre=2026-3"));
        mockMvc.perform(get("/profil").param("profil", "ENTITE").param("cible", "DIR:RESEAU_RETAIL/DEP:X")
                        .param("trimestre", "2026-3"))
                .andExpect(redirectedUrl("/entites?code=DIR:RESEAU_RETAIL/DEP:X&trimestre=2026-3"));
        // Sans personne : le profil s'applique et on reste sur la page (jamais la liste /managers).
        mockMvc.perform(get("/profil?profil=MANAGER&trimestre=2026-3&retour=/viviers?trimestre=2026-3"))
                .andExpect(redirectedUrl("/viviers?trimestre=2026-3"));
        mockMvc.perform(get("/profil?profil=MANAGER&trimestre=2026-3")).andExpect(redirectedUrl("/"));
    }

    // --- liens croises --------------------------------------------------------------------------

    @Test
    void les_noms_de_manager_et_les_entites_menent_a_leurs_vues() throws Exception {
        Map<String, String> bp001 = collaborateurs.stream().filter(l -> "BP001".equals(l.get("A"))).findFirst().orElseThrow();
        String fiche = html(page("/fiche-collaborateur?matricule=BP001&trimestre=2026-3"));
        assertThat(fiche).contains("/managers/" + bp001.get("N") + "?trimestre=2026-3", "Voir l'équipe de son manager",
                "/entites?code=DIR:RESEAU_RETAIL&amp;trimestre=2026-3");

        assertThat(html(page("/collaborateurs?q=BP001"))).contains("/managers/" + bp001.get("N") + "?trimestre=2026-3",
                "/entites?code=DIR:RESEAU_RETAIL/");
        String alertes = html(page("/alertes"));
        assertThat(alertes).contains("/managers/", "/entites?code=");
        assertThat(html(page("/notifications"))).contains("/entites?code=");
        assertThat(html(page("/campagne"))).contains("/entites?code=DIR:", "Vue entité");
    }

    // --- organigramme ---------------------------------------------------------------------------

    @Test
    void l_organigramme_fusionne_les_chaines_a_enfant_unique_sans_changer_les_effectifs() throws Exception {
        MvcResult resultat = page("/entites?trimestre=2026-3");
        Organigramme organigramme = modele(resultat, "organigramme");
        List<NoeudEntite> brut = vueEntiteViewService.listerEntites();

        // Une carte par direction, memes effectifs que l'arbre d'origine.
        assertThat(organigramme.directions()).hasSameSizeAs(brut);
        for (int i = 0; i < brut.size(); i++) {
            assertThat(organigramme.directions().get(i).branche().effectif()).isEqualTo(brut.get(i).effectif());
        }
        assertThat(organigramme.directions().stream().mapToInt(d -> d.branche().effectif()).sum())
                .isEqualTo(brut.stream().mapToInt(NoeudEntite::effectif).sum());
        // Plus aucune entite a enfant unique de meme effectif ; chaque ligne garde la somme de ses enfants au plus.
        organigramme.directions().forEach(d -> verifierFusion(d.branche()));
        // Le classeur a de telles chaines : leurs libelles sont fusionnes.
        assertThat(lignes(brut)).isGreaterThan(organigramme.directions().stream()
                .mapToLong(d -> lignes(d.branche())).sum());
        assertThat(organigramme.directions()).anyMatch(d -> contientFusion(d.branche()));

        // Talents et alertes par direction : comptes sur les listes du trimestre, sans en inventer.
        assertThat(organigramme.indicateursDisponibles()).isTrue();
        int talents = organigramme.directions().stream().mapToInt(d -> d.nbTalents()).sum();
        assertThat(talents).isPositive().isLessThanOrEqualTo(
                talentService.detecterTalents(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow()).size());
        assertThat(organigramme.directions().stream().mapToInt(d -> d.nbAlertes()).sum()).isPositive();

        String html = html(resultat);
        // Sans JavaScript : tout est ouvert et la recherche est cachee ; organigramme.js replie et filtre.
        assertThat(html).contains("class=\"organigramme-carte\" open", "class=\"organigramme-noeud\" open",
                "id=\"organigramme-recherche\"", "/js/organigramme.js");
        assertThat(html).containsPattern("id=\"organigramme-recherche-bloc\"\\s+hidden");
        assertThat(html.substring(html.indexOf("<main"))).doesNotContain("<ul", "<li", "href=\"/api/");
        Branche premiere = organigramme.directions().get(0).branche();
        assertThat(html).contains("/entites?code=" + premiere.code() + "&amp;trimestre=2026-3",
                "<strong>" + premiere.effectif() + "</strong> collaborateurs");
    }

    private static void verifierFusion(Branche b) {
        assertThat(b.enfants().size() == 1 && b.enfants().get(0).effectif() == b.effectif())
                .as(b.libelle()).isFalse();
        assertThat(b.enfants().stream().mapToInt(Branche::effectif).sum()).as(b.libelle())
                .isLessThanOrEqualTo(b.effectif());
        b.enfants().forEach(ProfilsPagesDatasetTest::verifierFusion);
    }

    private static boolean contientFusion(Branche b) {
        return b.libelle().contains(" / ") || b.enfants().stream().anyMatch(ProfilsPagesDatasetTest::contientFusion);
    }

    private static long lignes(Branche b) {
        return 1 + b.enfants().stream().mapToLong(ProfilsPagesDatasetTest::lignes).sum();
    }

    private static long lignes(List<NoeudEntite> noeuds) {
        return noeuds.stream().mapToLong(n -> 1 + lignes(n.enfants())).sum();
    }
}
