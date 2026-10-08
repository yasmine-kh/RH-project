package com.talent360bank.talent360bank.ui.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logo Banque Populaire : le logo complet (embleme + BANQUE POPULAIRE) dans la case de la marque de la
 * sidebar, de la page de connexion et des pages 403 / 404, avec son texte alternatif ; l'embleme carre en
 * icone d'onglet. Les fichiers sont servis sans connexion (la page de connexion les affiche avant toute
 * authentification).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:logo;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
class LogoTest {

    private static final String LOGO = "<span class=\"mark mark-logo\"><img src=\"/images/BP-Logo-Complet.png\" "
            + "alt=\"Banque Populaire\" width=\"170\" height=\"52\"></span>";
    private static final String ICONE = "<link rel=\"icon\" type=\"image/png\" href=\"/images/BP-Embleme.png\">";

    @Autowired
    private MockMvc mockMvc;

    private BufferedImage image(String url) throws Exception {
        MvcResult resultat = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        assertThat(resultat.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(resultat.getResponse().getContentAsByteArray())
                .isEqualTo(Files.readAllBytes(Path.of("src/main/resources/static" + url)));
        return ImageIO.read(new ByteArrayInputStream(resultat.getResponse().getContentAsByteArray()));
    }

    @Test
    void les_fichiers_du_logo_sont_servis_sans_connexion() throws Exception {
        // Embleme : carre, pour l'icone d'onglet.
        BufferedImage embleme = image("/images/BP-Embleme.png");
        assertThat(embleme.getWidth()).isEqualTo(embleme.getHeight());
        // Logo complet recadre : 170 x 52, affiche a cette taille (le texte fait 11 px de haut).
        BufferedImage complet = image("/images/BP-Logo-Complet.png");
        assertThat(complet.getWidth()).isEqualTo(170);
        assertThat(complet.getHeight()).isEqualTo(52);
        // Le fichier d'origine reste servi.
        image("/images/BP-Logo.png");
    }

    @Test
    void la_page_de_connexion_montre_le_logo_complet_et_l_icone() throws Exception {
        String page = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains(LOGO, ICONE).doesNotContain("<span class=\"mark\"></span>");
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_sidebar_montre_le_logo_complet_a_la_place_de_la_marque() throws Exception {
        String page = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String sidebar = page.substring(page.indexOf("<aside id=\"sidebar\""), page.indexOf("</aside>"));
        assertThat(sidebar).contains("<div class=\"brand\">", LOGO).doesNotContain("<span class=\"mark\"></span>");
        assertThat(page).contains(ICONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/erreur/403", "/?trimestre=2031-1"})
    @WithMockUser(roles = "RH")
    void les_pages_403_et_404_montrent_le_logo(String url) throws Exception {
        MvcResult resultat = mockMvc.perform(get(url)).andReturn();
        assertThat(resultat.getResponse().getStatus()).isIn(200, 403, 404);
        String page = resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains(LOGO, ICONE).doesNotContain("<span class=\"mark\"></span>");
        assertThat(page).containsAnyOf("Acces refuse", "Page introuvable");
    }

    @Test
    void la_case_garde_la_bordure_doree_et_s_elargit_aux_proportions_du_logo() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
        // Plus de carre 34 x 34 force : la case suit le logo, affiche a sa taille reelle, sans deformation.
        assertThat(css).contains(".brand .mark.mark-logo, .page-seule .marque .mark.mark-logo { width: auto; height: auto;",
                ".mark.mark-logo img { display: block; width: 170px; max-width: 100%; height: auto; }");
        assertThat(css).doesNotContain("object-fit: contain; }\n.mark");
        // La bordure doree reste celle du prototype (.brand .mark) et de la page seule (.page-seule .marque .mark).
        assertThat(Files.readString(Path.of("src/main/resources/static/css/prototype.css")))
                .contains(".brand .mark{display:flex;", "border:1.5px solid var(--gold)");
        String regle = css.substring(css.indexOf(".brand .mark.mark-logo"), css.indexOf("}", css.indexOf(".brand .mark.mark-logo")));
        assertThat(regle).doesNotContain("border");
    }
}
