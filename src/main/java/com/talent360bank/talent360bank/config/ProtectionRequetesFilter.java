package com.talent360bank.talent360bank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talent360bank.talent360bank.controller.dto.ErreurApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deux protections, en amont de Spring Security (voir {@link SecurityConfig}).
 *
 * <p><strong>Ecritures sur l'API (CSRF).</strong> Toute requete POST, PUT,
 * PATCH ou DELETE sur /api/** doit porter l'en-tete {@link #EN_TETE_ECRITURE}.
 * Un navigateur ne peut pas ajouter un en-tete personnalise a une requete
 * envoyee depuis un autre site sans une verification CORS prealable, que
 * l'application refuse (aucune configuration CORS) : une page malveillante ne
 * peut donc pas declencher un recalcul, un import ou une modification des
 * reglages. C'est la protection CSRF de l'API : le jeton CSRF de Spring
 * Security y est desactive. Les formulaires HTML (connexion, deconnexion...)
 * ne sont pas concernes : ils portent le jeton CSRF de Spring Security. Un
 * ecran qui ecrit par fetch doit envoyer l'en-tete (voir
 * docs/requetes-ecriture.md).
 *
 * <p><strong>Hote (DNS rebinding).</strong> Toute requete dont l'en-tete Host
 * ne designe pas la machine locale est refusee, lectures comprises : un site
 * qui ferait pointer son nom vers 127.0.0.1 ne peut pas lire les donnees RH.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProtectionRequetesFilter extends OncePerRequestFilter {

    /** Nom de l'en-tete exige sur toute ecriture. Seule definition du projet. */
    public static final String EN_TETE_ECRITURE = "X-Talent360";

    private static final Logger log = LoggerFactory.getLogger(ProtectionRequetesFilter.class);

    private static final Set<String> METHODES_ECRITURE = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final Set<String> hotesAutorises;
    private final ObjectMapper objectMapper;

    public ProtectionRequetesFilter(
            @Value("${talent360.securite.hotes-autorises:localhost,127.0.0.1}") List<String> hotesAutorises,
            ObjectMapper objectMapper) {
        this.hotesAutorises = hotesAutorises.stream()
                .map(hote -> hote.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String hote = requete.getServerName() == null ? "" : requete.getServerName().toLowerCase(Locale.ROOT);
        if (!hotesAutorises.contains(hote)) {
            log.warn("Requete refusee : hote non autorise ({} {})", requete.getMethod(), requete.getRequestURI());
            refuser(reponse, "hote_non_autorise",
                    "Cette application n'accepte que les requêtes adressées à la machine locale");
            return;
        }

        String valeur = requete.getHeader(EN_TETE_ECRITURE);
        if (SecurityConfig.estRequeteApi(requete) && METHODES_ECRITURE.contains(requete.getMethod())
                && (valeur == null || valeur.isBlank())) {
            log.warn("Requete refusee : en-tete {} absent ({} {})", EN_TETE_ECRITURE,
                    requete.getMethod(), requete.getRequestURI());
            refuser(reponse, "en_tete_manquant",
                    "Les requêtes d'écriture doivent porter l'en-tête " + EN_TETE_ECRITURE);
            return;
        }

        chaine.doFilter(requete, reponse);
    }

    private void refuser(HttpServletResponse reponse, String code, String message) throws IOException {
        reponse.setStatus(HttpStatus.FORBIDDEN.value());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(reponse.getOutputStream(), ErreurApi.de(HttpStatus.FORBIDDEN.value(), code, message));
    }
}
