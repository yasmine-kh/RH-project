package com.talent360bank.talent360bank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talent360bank.talent360bank.controller.dto.ErreurApi;
import com.talent360bank.talent360bank.entity.Role;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.io.IOException;

/**
 * Connexion par formulaire. Seul le RH se connecte (voir {@link Role}).
 *
 * <p><strong>Regles.</strong> Pages publiques : /login et les ressources
 * statiques. Tout le reste, pages et /api/**, lectures comme ecritures, exige
 * un RH connecte. Apres connexion, le RH arrive sur /.
 *
 * <p><strong>CSRF.</strong> Deux mecanismes, un par type de client :
 * <ul>
 *   <li>Formulaires HTML (connexion, deconnexion, futurs formulaires CRUD) :
 *   jeton CSRF de Spring Security, ajoute automatiquement par Thymeleaf dans
 *   tout {@code <form th:action>}.</li>
 *   <li>/api/** : jeton CSRF desactive, remplace par
 *   {@link ProtectionRequetesFilter} (en-tete X-Talent360 obligatoire sur
 *   toute ecriture). Un autre site ne peut pas ajouter cet en-tete sans
 *   verification CORS prealable, que l'application refuse. S'y ajoute le
 *   cookie de session SameSite=Strict, que le navigateur n'envoie jamais
 *   depuis un autre site. Un fetch de la meme origine envoie le cookie de
 *   session par defaut (credentials "same-origin") : les appels existants
 *   n'ont rien a changer, ni jeton a lire dans la page.</li>
 * </ul>
 *
 * <p><strong>Erreurs.</strong> Pour /api/** : 401 ou 403 en JSON (corps
 * {@link ErreurApi}). Pour les pages : renvoi vers /login si l'utilisateur
 * n'est pas connecte, page erreur/403 sinon (jeton CSRF absent, ou compte
 * connecte sans le role RH).
 *
 * <p>Duree de session, cookie HttpOnly et SameSite : application.properties
 * (server.servlet.session.*).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String PAGE_CONNEXION = "/login";
    public static final String PAGE_ACCES_REFUSE = "/erreur/403";
    public static final String PAGE_ACCUEIL = "/";

    private static final String RH = Role.RH.name();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filtreSecurite(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(regles -> regles
                        // Renvois internes (page 403, page d'erreur) : la requete d'origine a deja ete jugee.
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers(PAGE_CONNEXION, "/css/**", "/js/**", "/vendor/**", "/images/**",
                                "/favicon.ico").permitAll()

                        // Tout le reste : pages et API, lecture et ecriture.
                        .anyRequest().hasRole(RH))

                .formLogin(formulaire -> formulaire
                        .loginPage(PAGE_CONNEXION)
                        .defaultSuccessUrl(PAGE_ACCUEIL, true)
                        .failureUrl(PAGE_CONNEXION + "?error"))
                .logout(deconnexion -> deconnexion
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(PAGE_CONNEXION + "?deconnexion")
                        .deleteCookies("JSESSIONID"))

                .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfig::estRequeteApi))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                // Apres connexion, toujours l'accueil : inutile de memoriser la page
                // demandee, et d'ouvrir une session pour cela.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))

                .exceptionHandling(erreurs -> erreurs
                        .authenticationEntryPoint(pointEntree(objectMapper))
                        .accessDeniedHandler(accesRefuse(objectMapper)));

        return http.build();
    }

    /** Requete adressee a l'API JSON (et non a une page). */
    static boolean estRequeteApi(HttpServletRequest requete) {
        String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
        return chemin.equals("/api") || chemin.startsWith("/api/");
    }

    /** Non connecte : 401 JSON pour l'API, renvoi vers /login pour une page. */
    private static AuthenticationEntryPoint pointEntree(ObjectMapper objectMapper) {
        LoginUrlAuthenticationEntryPoint versConnexion = new LoginUrlAuthenticationEntryPoint(PAGE_CONNEXION);
        return (requete, reponse, exception) -> {
            if (estRequeteApi(requete)) {
                ecrireErreur(reponse, objectMapper, HttpStatus.UNAUTHORIZED, "non_authentifie",
                        "Connexion requise");
            } else {
                versConnexion.commence(requete, reponse, exception);
            }
        };
    }

    /** Connecte mais sans le droit : 403 JSON pour l'API, page erreur/403 sinon. */
    private static AccessDeniedHandler accesRefuse(ObjectMapper objectMapper) {
        AccessDeniedHandlerImpl versPage = new AccessDeniedHandlerImpl();
        versPage.setErrorPage(PAGE_ACCES_REFUSE);
        return (requete, reponse, exception) -> {
            if (estRequeteApi(requete)) {
                ecrireErreur(reponse, objectMapper, HttpStatus.FORBIDDEN, "acces_refuse",
                        "Votre profil ne permet pas cette action");
            } else {
                versPage.handle(requete, reponse, exception);
            }
        };
    }

    private static void ecrireErreur(HttpServletResponse reponse, ObjectMapper objectMapper, HttpStatus statut,
                                     String code, String message) throws IOException {
        reponse.setStatus(statut.value());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(reponse.getOutputStream(), ErreurApi.de(statut.value(), code, message));
    }
}
