package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import com.talent360bank.talent360bank.securite.ConnexionHttpDeTest;
import com.talent360bank.talent360bank.service.ClasseurDeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page d'import sur un vrai serveur (Tomcat), comme depuis un navigateur :
 * connexion, GET /import pour le jeton CSRF du formulaire, POST multipart avec
 * le jeton en champ cache. Verifie ce que MockMvc ne voit pas : Tomcat lit le
 * jeton dans le corps multipart avant Spring Security.
 *
 * <p>Limite de taille abaissee a 100 Ko.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:import-page-http;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.servlet.multipart.max-file-size=100KB", "spring.servlet.multipart.max-request-size=100KB"})
class ImportPageHttpTest {

    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ImportExcelRepository importExcelRepository;

    /** Cookie de session : le client sans redirection (withRedirects) n'a pas l'intercepteur qui le pose. */
    private String session;

    @BeforeEach
    void connecterRh() {
        session = ConnexionHttpDeTest.connecterRh(restTemplate, utilisateurRepository, passwordEncoder);
    }

    @Test
    void le_formulaire_importe_le_classeur_avec_son_jeton_csrf() {
        ResponseEntity<String> reponse = envoyer(ClasseurDeTest.complet().octets(), jeton());
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reponse.getBody()).contains("Bilan de l'import", "SUCCES", "Voir le tableau de bord T3 2026");
        assertThat(importExcelRepository.findAllRecentsDabord()).isNotEmpty();
    }

    @Test
    void sans_jeton_csrf_le_formulaire_est_refuse() {
        long imports = importExcelRepository.count();

        ResponseEntity<String> reponse = envoyer(ClasseurDeTest.complet().octets(), null);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(importExcelRepository.count()).isEqualTo(imports);
    }

    @Test
    void un_fichier_au_dela_de_la_limite_renvoie_sur_la_page_avec_un_message() {
        long imports = importExcelRepository.count();

        ResponseEntity<String> reponse = envoyer(new byte[150 * 1024], jeton());

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(reponse.getHeaders().getLocation()).isNotNull();
        assertThat(reponse.getHeaders().getLocation().toString()).endsWith("/import?erreur=taille");
        assertThat(lire("/import?erreur=taille").getBody()).contains("Fichier trop volumineux : 100 Ko au maximum.");
        assertThat(importExcelRepository.count()).isEqualTo(imports);
    }

    private ResponseEntity<String> lire(String url) {
        HttpHeaders entetes = new HttpHeaders();
        entetes.add(HttpHeaders.COOKIE, session);
        return client().exchange(url, HttpMethod.GET, new HttpEntity<>(entetes), String.class);
    }

    /**
     * Client sans intercepteur ni redirection : chaque requete porte le seul
     * cookie de la session du test (connecterRh ajoute un intercepteur a
     * chaque connexion, qui enverrait aussi les sessions precedentes).
     */
    private TestRestTemplate client() {
        return restTemplate.withRedirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW);
    }

    private String jeton() {
        ResponseEntity<String> page = lire("/import");
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        Matcher jeton = JETON_CSRF.matcher(page.getBody());
        assertThat(jeton.find()).as("jeton CSRF dans le formulaire d'import").isTrue();
        return jeton.group(1);
    }

    private ResponseEntity<String> envoyer(byte[] classeur, String jeton) {
        MultiValueMap<String, Object> formulaire = new LinkedMultiValueMap<>();
        if (jeton != null) {
            formulaire.add("_csrf", jeton);
        }
        formulaire.add("annee", "2026");
        formulaire.add("numero", "3");
        formulaire.add("calcul", "true");
        formulaire.add("fichier", new ByteArrayResource(classeur) {
            @Override
            public String getFilename() {
                return "classeur-test.xlsx";
            }
        });
        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(MediaType.MULTIPART_FORM_DATA);
        entetes.add(HttpHeaders.COOKIE, session);
        return client().postForEntity("/import", new HttpEntity<>(formulaire, entetes), String.class);
    }
}
