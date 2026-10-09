package com.talent360bank.talent360bank.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requetes refusees : chaque refus est journalise avec sa cause ; un formulaire perime (jeton CSRF
 * d'une session disparue) ramene a la connexion avec "Votre session a expire", ou affiche "La page a
 * expire" a un RH connecte ; un vrai manque de droits garde la page "Acces refuse" ; l'API repond en
 * JSON. Et la configuration qui evite les sessions perdues : DevTools ne redemarre pas l'application,
 * les tests se construisent dans target-tests/.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:refus-acces;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RefusAccesTest {

    @Autowired
    private MockMvc mockMvc;

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ formulaire de connexion perime

    @Test
    void un_formulaire_de_connexion_perime_ramene_a_la_connexion_avec_un_message(CapturedOutput journal)
            throws Exception {
        // Jeton d'une session disparue (application redemarree, session expiree) : aucune session.
        mockMvc.perform(post("/login").param("username", "rh").param("password", "x").param("_csrf", "PERIME"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?expiree"))
                .andExpect(unauthenticated());
        // Session en cours mais jeton faux (onglet d'une session precedente) : meme retour.
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        mockMvc.perform(get("/login").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/login").session(session).param("username", "rh").param("password", "x")
                        .param("_csrf", "PERIME"))
                .andExpect(redirectedUrl("/login?expiree")).andExpect(unauthenticated());

        String page = html(mockMvc.perform(get("/login").param("expiree", "")).andExpect(status().isOk()).andReturn());
        assertThat(page).contains("id=\"session-expiree\"", "Votre session a expiré, reconnectez-vous.");
        assertThat(html(mockMvc.perform(get("/login")).andReturn())).doesNotContain("Votre session a expiré");
        // La cause est dans le journal, sans le jeton.
        assertThat(journal.getOut()).contains("Requete refusee (403, jeton CSRF : aucune session, expiree ou "
                        + "application redemarree) : POST /login, non connecte",
                "Requete refusee (403, jeton CSRF absent ou invalide pour cette session) : POST /login, non connecte")
                .doesNotContain("PERIME");
    }

    @Test
    void une_page_envoyee_apres_la_fin_de_la_session_ramene_aussi_a_la_connexion() throws Exception {
        // Visiteur qui n'est plus connecte : un formulaire de l'application (Comite) avec un vieux jeton.
        mockMvc.perform(post("/comite-talent/talents").param("_csrf", "PERIME").param("trimestre", "2026-3")
                        .param("matricule", "BP001").param("decision", "OUI"))
                .andExpect(redirectedUrl("/login?expiree"));
    }

    // ------------------------------------------------------------ RH connecte

    @Test
    void un_rh_connecte_avec_un_formulaire_perime_voit_la_page_expiree(CapturedOutput journal) throws Exception {
        MvcResult refus = mockMvc.perform(post("/parametres/questionnaire").with(user("rh").roles("RH"))
                        .param("enps", ""))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(refus.getResponse().getForwardedUrl()).isEqualTo(SecurityConfig.PAGE_ACCES_REFUSE);
        assertThat(refus.getRequest().getAttribute(SecurityConfig.ATTRIBUT_MOTIF))
                .isEqualTo(SecurityConfig.MOTIF_PAGE_EXPIREE);
        assertThat(journal.getOut()).contains(
                "Requete refusee (403, jeton CSRF : aucune session, expiree ou application redemarree) : "
                        + "POST /parametres/questionnaire, utilisateur connecte");

        String page = html(mockMvc.perform(post("/erreur/403").with(user("rh").roles("RH")).with(csrf())
                        .requestAttr(SecurityConfig.ATTRIBUT_MOTIF, SecurityConfig.MOTIF_PAGE_EXPIREE))
                .andReturn());
        assertThat(page).contains("Page expirée", "La page a expiré : rechargez-la et recommencez.",
                        "rien n'a été enregistré")
                .doesNotContain("Accès refusé", "Votre profil ne permet pas");
        assertThat(page).contains("<title>BP TALENT 360 — Page expirée</title>");
    }

    @Test
    void un_vrai_manque_de_droits_garde_la_page_acces_refuse(CapturedOutput journal) throws Exception {
        MvcResult refus = mockMvc.perform(get("/9box").with(user("autre").roles("AUTRE")))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(refus.getRequest().getAttribute(SecurityConfig.ATTRIBUT_MOTIF))
                .isEqualTo(SecurityConfig.MOTIF_ACCES_REFUSE);
        assertThat(journal.getOut()).contains("Requete refusee (403, droits insuffisants) : GET /9box, utilisateur connecte");
        // Rendu de la page (en vrai, renvoi interne autorise ; ici, appele directement par un RH).
        String page = html(mockMvc.perform(get("/erreur/403").with(user("rh").roles("RH"))
                        .requestAttr(SecurityConfig.ATTRIBUT_MOTIF, SecurityConfig.MOTIF_ACCES_REFUSE))
                .andReturn());
        assertThat(page).contains("Accès refusé", "Votre compte n'a pas accès à cette page.")
                .doesNotContain("Page expirée", "Votre profil ne permet pas");
        // L'API garde son 403 JSON.
        mockMvc.perform(get("/api/trimestres").with(user("autre").roles("AUTRE")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erreur").value("acces_refuse"));
    }

    // ------------------------------------------------------------ configuration

    @Test
    void devtools_ne_redemarre_jamais_l_application_sans_qu_on_le_demande() throws Exception {
        for (String fichier : new String[]{"application.properties", "application-dev.properties"}) {
            java.util.Properties proprietes = new java.util.Properties();
            try (java.io.Reader lecteur = Files.newBufferedReader(Path.of("src/main/resources", fichier))) {
                proprietes.load(lecteur);
            }
            assertThat(proprietes.getProperty("spring.devtools.restart.enabled")).as(fichier).isEqualTo("false");
        }
    }

    @Test
    void les_tests_se_construisent_a_part_de_target_classes() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<id>tests-isoles</id>", "<directory>${project.basedir}/target-tests</directory>");
        assertThat(Files.readString(Path.of(".gitignore"))).contains("target-tests/");
    }
}
