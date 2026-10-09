package com.talent360bank.talent360bank.ui.controller;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Une erreur inattendue montre une page en francais, sans detail technique ; le detail va au journal.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erreur-inattendue;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@WithMockUser(roles = "RH")
@Import(ErreurInattendueTest.PanneController.class)
@ExtendWith(OutputCaptureExtension.class)
class ErreurInattendueTest {

    private static final String SECRET = "détail interne : table entite, id 0";

    /** Un ecran qui tombe, comme "/" sur la base MySQL (EntityNotFoundException). */
    @Controller
    static class PanneController {
        @GetMapping("/test-panne")
        public String panne() {
            throw new jakarta.persistence.EntityNotFoundException(SECRET);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment environment;

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void une_erreur_d_un_ecran_montre_la_page_francaise_sans_detail(CapturedOutput journal) throws Exception {
        MvcResult resultat = mockMvc.perform(get("/test-panne")).andExpect(status().isInternalServerError())
                .andExpect(view().name("erreur/500")).andReturn();
        String page = html(resultat);
        String reference = (String) resultat.getModelAndView().getModel().get("reference");
        assertThat(reference).hasSize(8);
        assertThat(page).contains("Une erreur est survenue", "Référence de l&#39;erreur : " + reference)
                .doesNotContain(SECRET, "EntityNotFoundException", "at com.", "Exception", "Whitelabel");
        // Le detail complet (message, pile) est dans le journal, avec la meme reference.
        assertThat(journal.getOut()).contains("Erreur inattendue [ref " + reference + "] sur GET /test-panne",
                SECRET, "at com.talent360bank");
    }

    @Test
    void la_page_d_erreur_de_spring_est_la_meme_et_sans_detail() throws Exception {
        String page = html(mockMvc.perform(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(SECRET))
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, SECRET))
                .andExpect(status().isInternalServerError()).andReturn());
        assertThat(page).contains("Une erreur est survenue")
                .doesNotContain(SECRET, "IllegalStateException", "trace", "Whitelabel");
    }

    @Test
    void aucun_detail_technique_meme_avec_devtools() throws Exception {
        // Explicites dans application.properties : DevTools passe sinon stacktrace et message a "always".
        assertThat(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.properties")))
                .contains("server.error.include-stacktrace=never", "server.error.include-message=never",
                        "server.error.include-exception=false", "server.error.whitelabel.enabled=false");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-exception")).isEqualTo("false");
        assertThat(environment.getProperty("server.error.whitelabel.enabled")).isEqualTo("false");
    }

    @Test
    void une_erreur_de_requete_garde_son_statut() throws Exception {
        // Parametres absents : 400 de Spring, pas une "erreur inattendue".
        mockMvc.perform(post("/comite-talent/talents").with(csrf())).andExpect(status().isBadRequest());
        // Trimestre inconnu : 404 francaise, pas une erreur inattendue.
        mockMvc.perform(get("/").param("trimestre", "2031-1")).andExpect(status().isNotFound())
                .andExpect(view().name("erreur/404"));
    }
}
