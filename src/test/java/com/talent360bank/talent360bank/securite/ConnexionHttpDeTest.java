package com.talent360bank.talent360bank.securite;

import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connexion d'un client HTTP de test (TestRestTemplate) par le vrai
 * formulaire, comme un navigateur : GET /login pour obtenir la session et le
 * jeton CSRF, POST /login, puis le cookie de la nouvelle session (Spring en
 * change l'identifiant a la connexion) est ajoute a chaque requete.
 */
public final class ConnexionHttpDeTest {

    public static final String LOGIN_RH = "rh.test";
    private static final String MOT_DE_PASSE_RH = "mot-de-passe-de-test";

    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private ConnexionHttpDeTest() {
    }

    /**
     * Cree le compte RH de test s'il manque, connecte le client et y pose le cookie de session.
     *
     * @return l'en-tete Cookie de la session, pour un autre client
     */
    public static String connecterRh(TestRestTemplate client, UtilisateurRepository utilisateurRepository,
                                     PasswordEncoder passwordEncoder) {
        if (utilisateurRepository.findByLogin(LOGIN_RH).isEmpty()) {
            utilisateurRepository.save(new Utilisateur(LOGIN_RH, passwordEncoder.encode(MOT_DE_PASSE_RH),
                    Role.RH));
        }
        String cookie = seConnecter(client, LOGIN_RH, MOT_DE_PASSE_RH);
        ClientHttpRequestInterceptor session = (requete, corps, execution) -> {
            requete.getHeaders().add(HttpHeaders.COOKIE, cookie);
            return execution.execute(requete, corps);
        };
        client.getRestTemplate().getInterceptors().add(session);
        return cookie;
    }

    /** Parcours de connexion ; rend l'en-tete Cookie de la session ouverte. */
    public static String seConnecter(TestRestTemplate clientDeBase, String login, String motDePasse) {
        // Sans suivre les redirections : le cookie de la nouvelle session est sur la reponse 302.
        TestRestTemplate client = clientDeBase.withRedirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW);
        ResponseEntity<String> page = client.getForEntity("/login", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        Matcher jeton = JETON_CSRF.matcher(page.getBody());
        assertThat(jeton.find()).as("jeton CSRF dans le formulaire de connexion").isTrue();

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        entetes.add(HttpHeaders.COOKIE, cookieSession(page));
        MultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
        formulaire.add("username", login);
        formulaire.add("password", motDePasse);
        formulaire.add("_csrf", jeton.group(1));

        ResponseEntity<String> reponse = client.postForEntity("/login", new HttpEntity<>(formulaire, entetes),
                String.class);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(reponse.getHeaders().getLocation()).isNotNull();
        assertThat(reponse.getHeaders().getLocation().getPath()).isNotEqualTo("/login");
        return cookieSession(reponse);
    }

    /** "JSESSIONID=..." tire de l'en-tete Set-Cookie. */
    private static String cookieSession(ResponseEntity<?> reponse) {
        List<String> cookies = reponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        return cookies.stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pas de cookie de session dans " + cookies));
    }
}
