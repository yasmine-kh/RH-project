package com.talent360bank.talent360bank.ui.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base vide (aucun trimestre) : chaque ecran par trimestre repond 200 avec un
 * message clair et le lien vers l'import, jamais une page d'erreur. Et aucun
 * template n'injecte de HTML non echappe (th:utext).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pages-sans-trimestre;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@WithMockUser(roles = "RH")
class PagesSansTrimestreTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/dashboard-dg", "/collaborateurs", "/9box", "/viviers", "/postes-critiques",
            "/competences", "/comite-talent", "/alertes", "/notifications", "/campagne", "/managers",
            "/managers/BP001", "/entites", "/entites?code=DIR:RESEAU_RETAIL/DEP:NORD"})
    void sans_trimestre_la_page_invite_a_importer(String url) throws Exception {
        String page = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Aucun trimestre importé pour le moment", "href=\"/import\"",
                "Importer le classeur");
    }

    @Test
    void la_sidebar_liste_les_ecrans_dans_l_ordre() throws Exception {
        String page = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> liens = List.of("href=\"/\"", "href=\"/dashboard-dg\"", "href=\"/collaborateurs\"",
                "href=\"/9box\"", "href=\"/viviers\"", "href=\"/postes-critiques\"", "href=\"/competences\"",
                "href=\"/comite-talent\"", "href=\"/fiche-collaborateur\"", "href=\"/alertes\"",
                "href=\"/notifications\"", "href=\"/campagne\"", "href=\"/import\"", "href=\"/parametres\"");
        int precedent = -1;
        String sidebar = page.substring(page.indexOf("sidebar-nav"));
        for (String lien : liens) {
            int position = sidebar.indexOf(lien);
            assertThat(position).as(lien).isGreaterThan(precedent);
            precedent = position;
        }
    }

    @Test
    void aucun_template_n_utilise_th_utext() throws IOException {
        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            assertThat(templates.filter(p -> p.toString().endsWith(".html")))
                    .allSatisfy(p -> assertThat(Files.readString(p)).as(p.toString()).doesNotContainPattern("th:utext\\s*="));
        }
    }
}
