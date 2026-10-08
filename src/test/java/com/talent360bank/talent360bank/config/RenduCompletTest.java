package com.talent360bank.talent360bank.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.View;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une erreur au milieu d'un template ne produit jamais une page envoyee a moitie
 * (net::ERR_INCOMPLETE_CHUNKED_ENCODING dans le navigateur) : RenduCompletConfig fait rendre toute
 * la page en memoire avant d'ecrire. Contre-epreuve : avec le reglage par defaut de Thymeleaf, le
 * debut de la page est deja ecrit quand l'erreur survient.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rendu-complet;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RenduCompletTest {

    private static final String GABARIT = "test/rendu-interrompu";

    @Autowired
    private ThymeleafViewResolver resolveur;
    @Autowired
    private SpringTemplateEngine moteur;
    @Autowired
    private org.springframework.context.ApplicationContext contexte;

    /** Objet du modele dont la methode echoue pendant le rendu. */
    public static class Bombe {
        public String exploser() {
            throw new IllegalStateException("donnee illisible");
        }
    }

    private static MockHttpServletResponse rendre(View vue) {
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        assertThatThrownBy(() -> vue.render(Map.of("bombe", new Bombe()), new MockHttpServletRequest(), reponse))
                .hasStackTraceContaining("donnee illisible");
        return reponse;
    }

    @Test
    void l_application_rend_la_page_entiere_avant_d_ecrire() throws Exception {
        assertThat(resolveur.getProducePartialOutputWhileProcessing()).isFalse();
        MockHttpServletResponse reponse = rendre(resolveur.resolveViewName(GABARIT, Locale.FRANCE));
        // Rien n'a ete ecrit : la page d'erreur pourra etre rendue a la place, entiere.
        assertThat(reponse.getContentAsString()).doesNotContain("DEBUT-DE-PAGE").isEmpty();
        assertThat(reponse.isCommitted()).isFalse();
    }

    @Test
    void sans_ce_reglage_le_debut_de_la_page_part_avant_l_erreur() throws Exception {
        ThymeleafViewResolver parDefaut = new ThymeleafViewResolver();
        parDefaut.setTemplateEngine(moteur);
        parDefaut.setCharacterEncoding("UTF-8");
        parDefaut.setCache(false);
        parDefaut.setApplicationContext(contexte);
        assertThat(parDefaut.getProducePartialOutputWhileProcessing()).isTrue();
        MockHttpServletResponse reponse = rendre(parDefaut.resolveViewName(GABARIT, Locale.FRANCE));
        assertThat(reponse.getContentAsString()).contains("DEBUT-DE-PAGE").doesNotContain("FIN-DE-PAGE");
    }
}
