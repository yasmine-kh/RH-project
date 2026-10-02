package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.ProfilActif;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Menu de la sidebar selon le profil choisi (session HTTP), sur le vrai classeur :
 * menus du prototype, bandeau, retour a la vue RH, profil garde d'une page a
 * l'autre, entree surlignee, et meme nombre de requetes quel que soit le profil.
 * Ignore sans le classeur.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profils-menu-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class ProfilsMenuDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final Pattern LIBELLE = Pattern.compile("class=\"nav-item[^\"]*\"[^>]*>\\s*<i[^>]*></i><span>([^<]*)</span>");
    private static final Pattern LIEN = Pattern.compile("<a href=\"([^\"]*)\" class=\"nav-item");

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
    private EntityManagerFactory entityManagerFactory;

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
    }

    private String page(String url, MockHttpSession session) throws Exception {
        return mockMvc.perform(get(url).session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String nav(String html) {
        int debut = html.indexOf("<nav class=\"sidebar-nav\">");
        return html.substring(debut, html.indexOf("</nav>", debut));
    }

    private static List<String> extraire(Pattern motif, String texte) {
        List<String> trouves = new ArrayList<>();
        Matcher m = motif.matcher(texte);
        while (m.find()) {
            trouves.add(m.group(1).replace("&amp;", "&").replace("&#39;", "'"));
        }
        return trouves;
    }

    private static final List<String> MENU_RH = List.of("Accueil", "Dashboard DG", "Collaborateurs", "Managers",
            "Organigramme", "9-Box", "Viviers",
            "Postes critiques", "Compétences", "Comite Talent", "Fiche collaborateur", "Alertes", "Notifications",
            "Campagne", "Import", "Parametres");

    @Test
    void sans_profil_la_vue_rh_montre_tout_le_menu_sans_bandeau() throws Exception {
        String html = page("/", new MockHttpSession());
        assertThat(extraire(LIBELLE, nav(html))).containsExactlyElementsOf(MENU_RH);
        assertThat(html).doesNotContain("Revenir à la vue RH", "sidebar-bandeau-texte");
    }

    @Test
    void le_profil_manager_montre_le_menu_du_prototype_et_le_bandeau_sur_chaque_page() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=MANAGER&cible=BP026&trimestre=2026-3").session(session))
                .andExpect(redirectedUrl("/managers/BP026?trimestre=2026-3"));
        ProfilActif profil = (ProfilActif) session.getAttribute(ProfilActif.SESSION);
        assertThat(profil.type()).isEqualTo("MANAGER");
        assertThat(profil.matricule()).isEqualTo("BP026");

        for (String url : List.of("/managers/BP026?trimestre=2026-3", "/alertes", "/viviers", "/9box")) {
            String html = page(url, session);
            // Prototype, role manager : Campagne, Collaborateurs, Matrice 9-Box, Talent Passport, Alertes, Notifications.
            assertThat(extraire(LIBELLE, nav(html))).as(url).containsExactly("Campagne d'évaluation", "Collaborateurs",
                    "Matrice 9-Box", "Talent Passport", "Alertes", "Notifications");
            assertThat(html).as(url).contains("Vue : Manager — " + profil.nom(), "Revenir à la vue RH",
                    "<option value=\"MANAGER\" selected=\"selected\">");
        }
        List<String> liens = extraire(LIEN, nav(page("/managers/BP026", session)));
        assertThat(liens).contains("/managers/BP026#evaluations", "/managers/BP026#equipe", "/managers/BP026#neufbox",
                "/managers/BP026#alertes");
        assertThat(liens).noneMatch(l -> l.startsWith("/api/"));
        // L'entree de la page courante est surlignee.
        assertThat(nav(page("/managers/BP026", session)))
                .containsPattern("href=\"/managers/BP026#equipe\" class=\"nav-item active\"");
        // Toutes les pages restent accessibles par leur URL.
        page("/import", session);
        page("/parametres", session);
    }

    @Test
    void le_profil_collaborateur_mene_a_sa_fiche_ses_alertes_et_la_campagne_de_son_entite() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=COLLABORATEUR&cible=BP001").session(session))
                .andExpect(redirectedUrl("/fiche-collaborateur?matricule=BP001"));
        String html = page("/fiche-collaborateur?matricule=BP001", session);
        assertThat(extraire(LIBELLE, nav(html)))
                .containsExactly("Mon profil", "Mon engagement", "Campagne d'évaluation", "Notifications");
        assertThat(extraire(LIEN, nav(html))).contains("/fiche-collaborateur?matricule=BP001",
                "/fiche-collaborateur?matricule=BP001#engagement", "/alertes?q=BP001");
        assertThat(extraire(LIEN, nav(html))).anyMatch(l -> l.startsWith("/campagne?entite=DIR:RESEAU_RETAIL/"));
        assertThat(html).contains("Vue : Collaborateur — ", "id=\"engagement\"");
    }

    @Test
    void le_comite_a_le_tableau_de_bord_dg_et_revenir_a_la_vue_rh_rend_tout_le_menu() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=COMITE").session(session)).andExpect(redirectedUrl("/comite-talent"));
        String html = page("/dashboard-dg", session);
        assertThat(extraire(LIBELLE, nav(html))).containsExactly("Tableau de bord DG", "Comité Talent",
                "Postes critiques & Succession", "Historique (imports)");
        assertThat(html).contains("Vue : Comité Talent / Direction");

        mockMvc.perform(get("/profil?profil=RH").session(session)).andExpect(redirectedUrl("/"));
        String rh = page("/dashboard-dg", session);
        assertThat(extraire(LIBELLE, nav(rh))).containsExactlyElementsOf(MENU_RH);
        assertThat(rh).doesNotContain("Revenir à la vue RH");
    }

    private static final List<String> MENU_MANAGER = List.of("Campagne d'évaluation", "Collaborateurs",
            "Matrice 9-Box", "Talent Passport", "Alertes", "Notifications");

    @Test
    void choisir_manager_sans_personne_montre_tout_de_suite_le_menu_manager_grise() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // Le selecteur renvoie la page courante : on y reste, seul le menu change.
        mockMvc.perform(get("/profil").param("profil", "MANAGER").param("cible", "")
                        .param("retour", "/9box?trimestre=2026-3").session(session))
                .andExpect(redirectedUrl("/9box?trimestre=2026-3"));
        ProfilActif profil = (ProfilActif) session.getAttribute(ProfilActif.SESSION);
        assertThat(profil.type()).isEqualTo("MANAGER");
        assertThat(profil.matricule()).isNull();

        String html = page("/9box?trimestre=2026-3", session);
        String nav = nav(html);
        assertThat(extraire(LIBELLE, nav)).containsExactlyElementsOf(MENU_MANAGER);
        // Les entrees qui demandent un manager sont grisees, sans lien ; jamais la liste /managers.
        assertThat(Pattern.compile("nav-item-grisee").matcher(nav).results().count()).isEqualTo(4);
        assertThat(extraire(LIEN, nav)).containsExactly("/fiche-collaborateur", "/notifications?trimestre=2026-3");
        assertThat(nav).contains("choisir un manager").doesNotContain("href=\"/managers");
        assertThat(html).contains("Vue : Manager<", "Choisir un manager", "<datalist id=\"profils-managers\">",
                        "name=\"retour\" value=\"/9box?trimestre=2026-3\"")
                .doesNotContain("<datalist id=\"profils-collaborateurs\">");

        // Un manager choisi : chaque entree ouvre sa page filtree, le bandeau donne son nom.
        mockMvc.perform(get("/profil?profil=MANAGER&cible=BP026&trimestre=2026-3").session(session))
                .andExpect(redirectedUrl("/managers/BP026?trimestre=2026-3"));
        ProfilActif bp026 = (ProfilActif) session.getAttribute(ProfilActif.SESSION);
        String choisi = page("/9box?trimestre=2026-3", session);
        assertThat(extraire(LIBELLE, nav(choisi))).containsExactlyElementsOf(MENU_MANAGER);
        assertThat(nav(choisi)).doesNotContain("nav-item-grisee");
        assertThat(extraire(LIEN, nav(choisi))).contains("/managers/BP026?trimestre=2026-3#evaluations",
                "/managers/BP026?trimestre=2026-3#equipe", "/managers/BP026?trimestre=2026-3#neufbox",
                "/managers/BP026?trimestre=2026-3#alertes");
        assertThat(choisi).contains("Vue : Manager — " + bp026.nom());

        // Un autre manager : meme profil, autre personne.
        Matcher option = Pattern.compile("<datalist id=\"profils-managers\">.*?<option value=\"(BP(?!026\")[^\"]+)\"",
                Pattern.DOTALL).matcher(choisi);
        assertThat(option.find()).isTrue();
        mockMvc.perform(get("/profil?profil=MANAGER&cible=" + option.group(1)).session(session))
                .andExpect(redirectedUrl("/managers/" + option.group(1)));
        ProfilActif autre = (ProfilActif) session.getAttribute(ProfilActif.SESSION);
        assertThat(autre.type()).isEqualTo("MANAGER");
        assertThat(autre.matricule()).isEqualTo(option.group(1));
        assertThat(extraire(LIEN, nav(page("/", session)))).contains("/managers/" + option.group(1) + "#equipe");

        // Revenir a la vue RH remet profil et personne a zero.
        mockMvc.perform(get("/profil?profil=RH").session(session)).andExpect(redirectedUrl("/"));
        assertThat(session.getAttribute(ProfilActif.SESSION)).isEqualTo(ProfilActif.RH);
    }

    @Test
    void choisir_collaborateur_sans_personne_grise_son_menu_jusqu_au_choix() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=COLLABORATEUR&retour=/alertes").session(session))
                .andExpect(redirectedUrl("/alertes"));
        String html = page("/alertes", session);
        assertThat(extraire(LIBELLE, nav(html)))
                .containsExactly("Mon profil", "Mon engagement", "Campagne d'évaluation", "Notifications");
        assertThat(extraire(LIEN, nav(html))).isEmpty();
        assertThat(html).contains("Choisir un collaborateur", "choisir un collaborateur", "Vue : Collaborateur<",
                "<datalist id=\"profils-collaborateurs\">");

        // Choisir de nouveau le meme profil sans personne garde la personne deja choisie.
        mockMvc.perform(get("/profil?profil=COLLABORATEUR&cible=BP001").session(session));
        mockMvc.perform(get("/profil?profil=COLLABORATEUR&retour=/alertes").session(session));
        assertThat(((ProfilActif) session.getAttribute(ProfilActif.SESSION)).matricule()).isEqualTo("BP001");
        // Sans JavaScript, passer a Manager renvoie aussi l'ancien matricule : seul le profil change.
        mockMvc.perform(get("/profil?profil=MANAGER&cible=BP001&retour=/alertes").session(session))
                .andExpect(redirectedUrl("/alertes"));
        assertThat(session.getAttribute(ProfilActif.SESSION)).isEqualTo(ProfilActif.sansPersonne("MANAGER"));
    }

    @Test
    void le_selecteur_n_a_plus_de_profil_entite_et_le_retour_reste_dans_l_application() throws Exception {
        String html = page("/", new MockHttpSession());
        Matcher types = Pattern.compile("<option value=\"([A-Z]+)\"").matcher(
                html.substring(html.indexOf("id=\"profil-type\""), html.indexOf("</select>")));
        List<String> valeurs = new ArrayList<>();
        while (types.find()) {
            valeurs.add(types.group(1));
        }
        assertThat(valeurs).containsExactly("RH", "COLLABORATEUR", "MANAGER", "COMITE");
        assertThat(html).doesNotContain("profils-entites", "value=\"ENTITE\"");
        // RH : pas de personne a choisir.
        assertThat(html).doesNotContain("id=\"profil-cible\"", "Choisir un");

        for (String retour : List.of("//exemple.com", "https://exemple.com", "/profil?profil=RH", "/api/trimestres")) {
            mockMvc.perform(get("/profil").param("profil", "MANAGER").param("retour", retour)
                    .session(new MockHttpSession())).andExpect(redirectedUrl("/"));
        }
    }

    @Test
    void une_entite_reste_une_page_rh_et_une_personne_inconnue_ne_change_pas_le_profil() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=MANAGER&cible=BP026").session(session));
        mockMvc.perform(get("/profil?profil=MANAGER&cible=BP999").session(session));
        assertThat(((ProfilActif) session.getAttribute(ProfilActif.SESSION)).matricule()).isEqualTo("BP026");

        mockMvc.perform(get("/profil?profil=ENTITE&cible=DIR:RESEAU_RETAIL").session(session))
                .andExpect(redirectedUrl("/entites?code=DIR:RESEAU_RETAIL"));
        assertThat(((ProfilActif) session.getAttribute(ProfilActif.SESSION)).estRh()).isTrue();
    }

    @Test
    void le_profil_n_ajoute_aucune_requete() throws Exception {
        MockHttpSession rh = new MockHttpSession();
        MockHttpSession manager = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=MANAGER&cible=BP026").session(manager));
        MockHttpSession collaborateur = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=COLLABORATEUR&cible=BP001").session(collaborateur));
        MockHttpSession managerSansPersonne = new MockHttpSession();
        mockMvc.perform(get("/profil?profil=MANAGER").session(managerSansPersonne));

        for (String url : List.of("/alertes", "/9box", "/collaborateurs")) {
            long requetesRh = requetes(url, rh);
            assertThat(requetes(url, manager)).as(url).isEqualTo(requetesRh);
            assertThat(requetes(url, collaborateur)).as(url).isEqualTo(requetesRh);
            assertThat(requetes(url, managerSansPersonne)).as(url).isEqualTo(requetesRh);
        }
    }

    private long requetes(String url, MockHttpSession session) throws Exception {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        page(url, session);
        return statistiques.getPrepareStatementCount();
    }
}
