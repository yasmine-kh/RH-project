package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import com.talent360bank.talent360bank.securite.ConnexionHttpDeTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La page d'erreur generique de Spring Boot (/error, templates/error/5xx.html), sur un vrai serveur : une
 * erreur hors d'un ecran (dans un filtre, ou un controleur que ProfilsAdvice ne couvre pas) fait renvoyer
 * Tomcat vers /error. Le bouton "Se deconnecter" de cette page porte le jeton CSRF de la session, et il
 * deconnecte vraiment. MockMvc ne fait pas ce renvoi : seul un serveur le montre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:deconnexion-erreur-http;DB_CLOSE_DELAY=-1;MODE=MySQL")
@Import(DeconnexionPageErreurHttpTest.Pannes.class)
class DeconnexionPageErreurHttpTest {

    private static final Pattern FORMULAIRE = Pattern.compile(
            "<form method=\"post\" action=\"/logout\"[^>]*id=\"form-deconnexion\"[^>]*>(.*?)</form>", Pattern.DOTALL);
    private static final Pattern JETON = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    /** Deux pannes hors des ecrans : un filtre apres la securite, un controleur hors de ui.controller. */
    @TestConfiguration
    static class Pannes {
        @Bean
        FilterRegistrationBean<Filter> filtreEnPanne() {
            FilterRegistrationBean<Filter> filtre = new FilterRegistrationBean<>((requete, reponse, suite) -> {
                throw new IllegalStateException("panne de filtre de test");
            });
            filtre.addUrlPatterns("/test-panne-filtre");
            filtre.setOrder(Ordered.LOWEST_PRECEDENCE);
            return filtre;
        }

        @Bean
        ControleurEnPanne controleurEnPanne() {
            return new ControleurEnPanne();
        }
    }

    @RestController
    static class ControleurEnPanne {
        @GetMapping("/test-panne-hors-ecran")
        public String panne() {
            throw new IllegalStateException("panne de test hors ecran");
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @ValueSource(strings = {"/test-panne-filtre", "/test-panne-hors-ecran"})
    void la_page_5xx_generique_porte_le_jeton_et_deconnecte(String panne) {
        String cookie = ConnexionHttpDeTest.connecterRh(restTemplate, utilisateurRepository, passwordEncoder);
        HttpHeaders html = new HttpHeaders();
        html.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        ResponseEntity<String> erreur = restTemplate.exchange(panne, HttpMethod.GET, new HttpEntity<>(html),
                String.class);

        assertThat(erreur.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(erreur.getBody()).contains("Une erreur est survenue").doesNotContain("panne de", "Exception");
        Matcher formulaire = FORMULAIRE.matcher(erreur.getBody());
        assertThat(formulaire.find()).as("formulaire de deconnexion").isTrue();
        assertThat(formulaire.group(1)).contains("Se déconnecter");
        Matcher jeton = JETON.matcher(formulaire.group(1));
        assertThat(jeton.find()).as("jeton CSRF sur le bouton de la page 5xx").isTrue();

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        entetes.add(HttpHeaders.COOKIE, cookie);
        MultiValueMap<String, String> corps = new LinkedMultiValueMap<>();
        corps.add("_csrf", jeton.group(1));
        ResponseEntity<String> sortie = restTemplate.withRedirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW)
                .postForEntity("/logout", new HttpEntity<>(corps, entetes), String.class);

        assertThat(sortie.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(sortie.getHeaders().getLocation()).isNotNull();
        assertThat(sortie.getHeaders().getLocation().toString()).endsWith("/login?deconnexion");
    }
}
