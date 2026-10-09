package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * "Se deconnecter" sur chaque gabarit : le pied de la barre laterale (toutes les pages de l'application) et
 * les pages d'erreur 404, 500 et 403. Le bouton envoie un POST avec le jeton CSRF de la page ; on revient a
 * /login avec « Vous êtes déconnecté. » et la session est fermee.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deconnexion;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(DeconnexionTest.PanneController.class)
class DeconnexionTest {

    private static final Pattern FORMULAIRE = Pattern.compile(
            "<form method=\"post\" action=\"/logout\"[^>]*id=\"form-deconnexion\"[^>]*>(.*?)</form>", Pattern.DOTALL);
    private static final Pattern JETON = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    /** Ouvre la page dans une session RH, se deconnecte avec le formulaire qu'elle affiche. */
    private void deconnexionDepuis(MockHttpServletRequestBuilder page) throws Exception {
        deconnexionDepuis(page, 200);
    }

    private void deconnexionDepuis(MockHttpServletRequestBuilder page, int statut) throws Exception {
        MockHttpSession session = new MockHttpSession();
        var reponse = mockMvc.perform(page.session(session).with(user("rh.test").roles("RH"))).andReturn().getResponse();
        assertThat(reponse.getStatus()).isEqualTo(statut);
        String html = reponse.getContentAsString(StandardCharsets.UTF_8);

        Matcher formulaire = FORMULAIRE.matcher(html);
        assertThat(formulaire.find()).as("formulaire de deconnexion").isTrue();
        assertThat(formulaire.group(1)).contains("Se déconnecter");
        Matcher jeton = JETON.matcher(formulaire.group(1));
        assertThat(jeton.find()).as("jeton CSRF dans le formulaire").isTrue();

        mockMvc.perform(post("/logout").session(session).param("_csrf", jeton.group(1)))
                .andExpect(redirectedUrl("/login?deconnexion"));
        assertThat(session.isInvalid()).isTrue();
        String connexion = mockMvc.perform(get("/login").param("deconnexion", ""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(connexion).contains("Vous êtes déconnecté.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/import", "/collaborateurs", "/historique", "/parametres"})
    void le_pied_de_la_barre_laterale_deconnecte(String url) throws Exception {
        deconnexionDepuis(get(url));
    }

    @Test
    void la_page_404_deconnecte() throws Exception {
        // Trimestre inconnu : ProfilsAdvice rend erreur/404.
        deconnexionDepuis(get("/collaborateurs").param("trimestre", "2099-4"), 404);
    }

    @Test
    void la_page_500_deconnecte() throws Exception {
        // Un ecran qui tombe : ProfilsAdvice rend erreur/500.
        deconnexionDepuis(get("/test-deconnexion-panne"), 500);
    }

    /** Ecran qui tombe, pour la page 500. */
    @org.springframework.stereotype.Controller
    static class PanneController {
        @org.springframework.web.bind.annotation.GetMapping("/test-deconnexion-panne")
        public String panne() {
            throw new IllegalStateException("panne de test");
        }
    }

    @Test
    void la_page_403_deconnecte() throws Exception {
        deconnexionDepuis(get("/erreur/403")
                .requestAttr(SecurityConfig.ATTRIBUT_MOTIF, SecurityConfig.MOTIF_ACCES_REFUSE));
    }

    @Test
    void la_deconnexion_sans_jeton_est_refusee() throws Exception {
        mockMvc.perform(post("/logout").with(user("rh.test").roles("RH")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(302));
    }
}
