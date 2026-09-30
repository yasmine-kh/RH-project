package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
 * Regles d'acces de {@link SecurityConfig} sur le vrai contexte : chaque
 * profil sur chaque famille d'URL, connexion par formulaire (vrais comptes en
 * base, vrai BCrypt), CSRF des formulaires, premier compte RH.
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
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void comptes() {
        if (empreinte == null) {
            empreinte = passwordEncoder.encode(MOT_DE_PASSE);
        }
        compte("rh", Role.RH, null, true);
        compte("comite", Role.COMITE, null, true);
        compte("manager", Role.MANAGER, collaborateur("SEC-M", StatutCollaborateur.ACTIF), true);
        compte("collab", Role.COLLABORATEUR, collaborateur("SEC-C", StatutCollaborateur.ACTIF), true);
        compte("desactive", Role.COLLABORATEUR, collaborateur("SEC-D", StatutCollaborateur.ACTIF), false);
        compte("parti", Role.COLLABORATEUR, collaborateur("SEC-P", StatutCollaborateur.INACTIF), true);
    }

    // ------------------------------------------------------------ anonyme

    @Test
    void une_page_demandee_sans_connexion_renvoie_vers_login() throws Exception {
        for (String page : List.of("/", "/9box", "/moi", "/rh/utilisateurs", "/nimporte-quoi")) {
            mockMvc.perform(get(page))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    void l_api_sans_connexion_rend_401_en_json() throws Exception {
        mockMvc.perform(get("/api/trimestres"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erreur").value("non_authentifie"));

        mockMvc.perform(post("/api/trimestres").header(EN_TETE, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void la_page_de_connexion_et_les_ressources_statiques_sont_publiques() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                // Jeton CSRF ajoute par th:action.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ profils x URL

    /** URL -> profils autorises. Tout autre profil doit recevoir 403. */
    private static final Map<String, Set<Role>> AUTORISES = Map.ofEntries(
            Map.entry("/moi", Set.of(Role.COLLABORATEUR, Role.MANAGER)),
            Map.entry("/moi/resultats", Set.of(Role.COLLABORATEUR, Role.MANAGER)),
            Map.entry("/manager", Set.of(Role.MANAGER)),
            Map.entry("/manager/equipe", Set.of(Role.MANAGER)),
            Map.entry("/comite", Set.of(Role.COMITE, Role.RH)),
            Map.entry("/comite/succession", Set.of(Role.COMITE, Role.RH)),
            Map.entry("/rh/utilisateurs", Set.of(Role.RH)),
            Map.entry("/", Set.of(Role.RH)),
            Map.entry("/9box", Set.of(Role.RH, Role.COMITE)),
            Map.entry("/viviers", Set.of(Role.RH, Role.COMITE)),
            Map.entry("/comite-talent", Set.of(Role.RH, Role.COMITE)),
            Map.entry("/postes-critiques", Set.of(Role.RH, Role.COMITE)),
            Map.entry("/alertes", Set.of(Role.RH)),
            Map.entry("/parametres", Set.of(Role.RH)),
            Map.entry("/api/trimestres", Set.of(Role.values())),
            Map.entry("/nimporte-quoi", Set.of()));

    static Stream<Arguments> profilsEtUrls() {
        List<Arguments> cas = new ArrayList<>();
        AUTORISES.forEach((url, roles) -> {
            for (Role role : Role.values()) {
                cas.add(Arguments.of(url, role, roles.contains(role)));
            }
        });
        return cas.stream();
    }

    @ParameterizedTest(name = "{1} sur {0} : autorise = {2}")
    @MethodSource("profilsEtUrls")
    void chaque_profil_n_ouvre_que_ses_urls(String url, Role role, boolean autorise) throws Exception {
        MvcResult resultat = mockMvc.perform(get(url).with(user(role.name().toLowerCase()).roles(role.name())))
                .andReturn();
        int statut = resultat.getResponse().getStatus();

        if (autorise) {
            // Autorise : la page s'affiche, ou 404 pour un espace dont l'ecran n'existe pas encore.
            assertThat(statut).as(role + " sur " + url).isIn(200, 404);
        } else {
            assertThat(statut).as(role + " sur " + url).isEqualTo(403);
            if (!url.startsWith("/api/")) {
                assertThat(resultat.getResponse().getForwardedUrl()).isEqualTo(SecurityConfig.PAGE_ACCES_REFUSE);
            }
        }
    }

    // ------------------------------------------------------------ ecritures API

    static Stream<Arguments> ecrituresApi() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/trimestres"),
                Arguments.of(HttpMethod.POST, "/api/imports"),
                Arguments.of(HttpMethod.POST, "/api/trimestres/2026/3/calcul"),
                Arguments.of(HttpMethod.POST, "/api/trimestres/2026/3/scores/recalcul"),
                Arguments.of(HttpMethod.POST, "/api/trimestres/2026/3/9box/placement"),
                Arguments.of(HttpMethod.PUT, "/api/trimestres/2026/3/parametre"),
                Arguments.of(HttpMethod.PATCH, "/api/collaborateurs/SEC-C"),
                Arguments.of(HttpMethod.DELETE, "/api/collaborateurs/SEC-C"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ecrituresApi")
    void une_ecriture_sur_l_api_est_refusee_a_tout_profil_sauf_rh(HttpMethod methode, String url) throws Exception {
        for (Role role : List.of(Role.COLLABORATEUR, Role.MANAGER, Role.COMITE)) {
            mockMvc.perform(request(methode, url).header(EN_TETE, "1")
                            .with(user(role.name().toLowerCase()).roles(role.name())))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.erreur").value("acces_refuse"));
        }
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

    @ParameterizedTest
    @EnumSource(Role.class)
    void la_connexion_renvoie_vers_l_accueil_du_profil(Role role) throws Exception {
        String login = switch (role) {
            case RH -> "rh";
            case COMITE -> "comite";
            case MANAGER -> "manager";
            case COLLABORATEUR -> "collab";
        };
        String accueil = switch (role) {
            case RH -> "/";
            case COMITE -> "/comite";
            case MANAGER -> "/manager";
            case COLLABORATEUR -> "/moi";
        };
        mockMvc.perform(formLogin().user(login).password(MOT_DE_PASSE))
                .andExpect(redirectedUrl(accueil))
                .andExpect(authenticated().withUsername(login).withRoles(role.name()));
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
    void le_compte_d_un_collaborateur_inactif_est_refuse() throws Exception {
        mockMvc.perform(formLogin().user("parti").password(MOT_DE_PASSE))
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
        utilisateurRepository.findAll().stream()
                .filter(utilisateur -> utilisateur.getRole() == Role.RH)
                .forEach(utilisateurRepository::delete);
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
        utilisateurRepository.findAll().stream()
                .filter(utilisateur -> utilisateur.getRole() == Role.RH)
                .forEach(utilisateurRepository::delete);
        long avant = utilisateurRepository.count();

        assertThat(new PremierCompteRhInitializer(utilisateurRepository, passwordEncoder, new MockEnvironment())
                .creerSiAucunRh()).isFalse();
        assertThat(new PremierCompteRhInitializer(utilisateurRepository, passwordEncoder, new MockEnvironment()
                .withProperty(PremierCompteRhInitializer.VARIABLE_LOGIN, "admin.rh")
                .withProperty(PremierCompteRhInitializer.VARIABLE_MOT_DE_PASSE, "court"))
                .creerSiAucunRh()).isFalse();
        // Login deja pris par un compte non RH.
        assertThat(new PremierCompteRhInitializer(utilisateurRepository, passwordEncoder, new MockEnvironment()
                .withProperty(PremierCompteRhInitializer.VARIABLE_LOGIN, "collab")
                .withProperty(PremierCompteRhInitializer.VARIABLE_MOT_DE_PASSE, "un-mot-de-passe-initial"))
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

    private Collaborateur collaborateur(String id, StatutCollaborateur statut) {
        Collaborateur collaborateur = collaborateurRepository.findById(id).orElseGet(Collaborateur::new);
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom("Test");
        collaborateur.setPrenom(id);
        collaborateur.setDateEntree(LocalDate.of(2020, 1, 1));
        collaborateur.setStatut(statut);
        return collaborateurRepository.save(collaborateur);
    }

    private void compte(String login, Role role, Collaborateur collaborateur, boolean actif) {
        if (utilisateurRepository.findByLogin(login).isPresent()) {
            return;
        }
        Utilisateur utilisateur = new Utilisateur(login, empreinte, role, collaborateur);
        utilisateur.setActif(actif);
        utilisateurRepository.save(utilisateur);
    }
}
