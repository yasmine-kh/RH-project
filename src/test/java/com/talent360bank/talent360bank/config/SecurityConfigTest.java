package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regles d'acces de {@link SecurityConfig} sur le vrai contexte : anonyme
 * renvoye vers /login ou 401, RH autorise partout, connexion par formulaire
 * (vrais comptes en base, vrai BCrypt), CSRF des formulaires, premier compte RH.
 *
 * <p>Base en memoire a part : les comptes crees ici ne gênent pas les autres tests.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:securite;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String MOT_DE_PASSE = "mot-de-passe-solide";
    private static final String EN_TETE = ProtectionRequetesFilter.EN_TETE_ECRITURE;

    /** Empreinte calculee une fois : BCrypt est volontairement lent. */
    private static String empreinte;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void comptes() {
        if (empreinte == null) {
            empreinte = passwordEncoder.encode(MOT_DE_PASSE);
        }
        compte("rh", true);
        compte("desactive", false);
    }

    // ------------------------------------------------------------ anonyme

    /** Echantillon de pages : ecrans existants, URL inconnue, anciens espaces par profil supprimes. */
    private static final List<String> PAGES = List.of("/", "/9box", "/viviers", "/comite-talent",
            "/postes-critiques", "/alertes", "/parametres", "/moi", "/manager", "/comite", "/nimporte-quoi");

    /** Echantillon d'API, lectures. */
    private static final List<String> API = List.of("/api/trimestres", "/api/collaborateurs",
            "/api/collaborateurs/SEC-C", "/api/dashboard/synthese?annee=2026&numero=3",
            "/api/trimestres/2026/3/vigilance", "/api/imports", "/api/nimporte-quoi");

    @Test
    void une_page_demandee_sans_connexion_renvoie_vers_login() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    void l_api_sans_connexion_rend_401_en_json() throws Exception {
        for (String url : API) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.erreur").value("non_authentifie"));
        }
    }

    @Test
    void la_page_de_connexion_et_les_ressources_statiques_sont_publiques() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                // Jeton CSRF ajoute par th:action.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
    }

    /** Ecritures sur l'API. */
    static Stream<Arguments> ecrituresApi() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/trimestres"),
                Arguments.of(HttpMethod.PUT, "/api/trimestres/2026/3"),
                Arguments.of(HttpMethod.POST, "/api/imports"),
                Arguments.of(HttpMethod.POST, "/api/trimestres/2026/3/calcul"),
                Arguments.of(HttpMethod.PUT, "/api/trimestres/2026/3/parametre"),
                Arguments.of(HttpMethod.DELETE, "/api/collaborateurs/SEC-C"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ecrituresApi")
    void une_ecriture_sans_connexion_rend_401(HttpMethod methode, String url) throws Exception {
        mockMvc.perform(request(methode, url).header(EN_TETE, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erreur").value("non_authentifie"));
    }

    // ------------------------------------------------------------ RH

    @Test
    void le_rh_ouvre_toutes_les_pages_et_toute_l_api() throws Exception {
        for (String url : Stream.concat(PAGES.stream(), API.stream()).toList()) {
            int statut = mockMvc.perform(get(url).with(user("rh").roles(Role.RH.name())))
                    .andReturn().getResponse().getStatus();
            // Autorise : la page ou la reponse, sinon 404 (URL inconnue) ou une erreur metier (donnees absentes).
            assertThat(statut).as("RH sur " + url).isNotIn(302, 401, 403);
        }
    }

    @Test
    void une_session_sans_le_role_rh_est_refusee_en_403() throws Exception {
        // Aucun compte de l'application n'a d'autre role ; la regle reste "RH", pas "connecte".
        mockMvc.perform(get("/api/trimestres").with(user("autre").roles("AUTRE")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erreur").value("acces_refuse"));
        MvcResult page = mockMvc.perform(get("/9box").with(user("autre").roles("AUTRE")))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(page.getResponse().getForwardedUrl()).isEqualTo(SecurityConfig.PAGE_ACCES_REFUSE);
    }

    @Test
    void le_rh_ecrit_sur_l_api_avec_l_en_tete_et_sans_jeton_csrf() throws Exception {
        // Trimestre hors bornes : 400 prouve que la requete a passe la securite jusqu'au controleur.
        mockMvc.perform(post("/api/trimestres").header(EN_TETE, "1").with(user("rh").roles("RH"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annee\":2026,\"numero\":9}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sans_l_en_tete_une_ecriture_sur_l_api_reste_refusee_meme_au_rh() throws Exception {
        mockMvc.perform(post("/api/trimestres").with(user("rh").roles("RH"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annee\":2026,\"numero\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erreur").value("en_tete_manquant"));
    }

    // ------------------------------------------------------------ connexion

    @Test
    void la_connexion_du_rh_renvoie_vers_l_accueil() throws Exception {
        mockMvc.perform(formLogin().user("rh").password(MOT_DE_PASSE))
                .andExpect(redirectedUrl(SecurityConfig.PAGE_ACCUEIL))
                .andExpect(authenticated().withUsername("rh").withRoles("RH"));
    }

    @Test
    void un_mauvais_mot_de_passe_est_refuse() throws Exception {
        mockMvc.perform(formLogin().user("rh").password("pas-le-bon"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
        mockMvc.perform(formLogin().user("inconnu").password(MOT_DE_PASSE))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void un_compte_desactive_est_refuse() throws Exception {
        mockMvc.perform(formLogin().user("desactive").password(MOT_DE_PASSE))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void le_formulaire_de_connexion_exige_le_jeton_csrf() throws Exception {
        mockMvc.perform(post("/login").param("username", "rh").param("password", MOT_DE_PASSE))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void la_deconnexion_se_fait_en_post_avec_jeton() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()).with(user("rh").roles("RH")))
                .andExpect(redirectedUrl("/login?deconnexion"))
                .andExpect(unauthenticated());
        mockMvc.perform(post("/logout").with(user("rh").roles("RH")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ premier compte RH

    @Test
    void le_premier_compte_rh_est_cree_depuis_les_variables_puis_permet_de_se_connecter() throws Exception {
        utilisateurRepository.deleteAll();
        MockEnvironment environnement = new MockEnvironment()
                .withProperty(PremierCompteRhInitializer.VARIABLE_LOGIN, "admin.rh")
                .withProperty(PremierCompteRhInitializer.VARIABLE_MOT_DE_PASSE, "un-mot-de-passe-initial");

        PremierCompteRhInitializer initialisation =
                new PremierCompteRhInitializer(utilisateurRepository, passwordEncoder, environnement);
        assertThat(initialisation.creerSiAucunRh()).isTrue();
        // Un compte RH existe : un second demarrage ne cree rien.
        assertThat(initialisation.creerSiAucunRh()).isFalse();

        Utilisateur admin = utilisateurRepository.findByLogin("admin.rh").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.RH);
        assertThat(admin.getMotDePasseHash()).isNotEqualTo("un-mot-de-passe-initial").startsWith("$2");
        assertThat(admin.getDateCreation()).isNotNull();

        mockMvc.perform(formLogin().user("admin.rh").password("un-mot-de-passe-initial"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("RH"));
    }

    @Test
    void sans_variables_ou_avec_un_mot_de_passe_trop_court_aucun_compte_n_est_cree() {
        utilisateurRepository.deleteAll();
        long avant = utilisateurRepository.count();

        assertThat(new PremierCompteRhInitializer(utilisateurRepository, passwordEncoder, new MockEnvironment())
                .creerSiAucunRh()).isFalse();
        assertThat(new PremierCompteRhInitializer(utilisateurRepository, passwordEncoder, new MockEnvironment()
                .withProperty(PremierCompteRhInitializer.VARIABLE_LOGIN, "admin.rh")
                .withProperty(PremierCompteRhInitializer.VARIABLE_MOT_DE_PASSE, "court"))
                .creerSiAucunRh()).isFalse();

        assertThat(utilisateurRepository.count()).isEqualTo(avant);
        assertThat(utilisateurRepository.existsByRole(Role.RH)).isFalse();
    }

    // ------------------------------------------------------------ reglages de session

    /**
     * Les reglages de session sont dans le application.properties principal,
     * que celui des tests remplace sur le classpath : on relit donc le fichier.
     */
    @Test
    void la_session_est_courte_et_son_cookie_protege() throws IOException {
        Properties proprietes = new Properties();
        try (Reader lecteur = Files.newBufferedReader(Path.of("src/main/resources/application.properties"),
                StandardCharsets.UTF_8)) {
            proprietes.load(lecteur);
        }
        assertThat(proprietes.getProperty("server.servlet.session.timeout")).isEqualTo("15m");
        assertThat(proprietes.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(proprietes.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("strict");
        // Ni root ni valeur par defaut pour la base.
        assertThat(proprietes.getProperty("spring.datasource.username")).isEqualTo("${SPRING_DATASOURCE_USERNAME}");
        assertThat(proprietes.getProperty("spring.datasource.password")).isEqualTo("${SPRING_DATASOURCE_PASSWORD}");
    }

    // ------------------------------------------------------------ outils

    private void compte(String login, boolean actif) {
        if (utilisateurRepository.findByLogin(login).isPresent()) {
            return;
        }
        Utilisateur utilisateur = new Utilisateur(login, empreinte, Role.RH);
        utilisateur.setActif(actif);
        utilisateurRepository.save(utilisateur);
    }
}
