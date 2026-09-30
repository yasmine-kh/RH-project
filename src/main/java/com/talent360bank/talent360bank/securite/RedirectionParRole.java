package com.talent360bank.talent360bank.securite;

import com.talent360bank.talent360bank.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Page d'accueil apres connexion, selon le profil : toujours la meme, que
 * l'utilisateur ait ete renvoye vers /login depuis une autre page ou non.
 */
public class RedirectionParRole extends SimpleUrlAuthenticationSuccessHandler {

    /** Page d'accueil d'un profil. */
    public static String accueil(Role role) {
        return switch (role) {
            case COLLABORATEUR -> "/moi";
            case MANAGER -> "/manager";
            case COMITE -> "/comite";
            case RH -> "/";
        };
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication authentification) throws IOException {
        Set<String> autorites = authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        // Ordre du plus large au plus restreint, si un compte portait un jour plusieurs roles.
        String cible = "/login?error";
        for (Role role : new Role[]{Role.RH, Role.COMITE, Role.MANAGER, Role.COLLABORATEUR}) {
            if (autorites.contains(role.autorite())) {
                cible = accueil(role);
                break;
            }
        }
        clearAuthenticationAttributes(requete);
        getRedirectStrategy().sendRedirect(requete, reponse, cible);
    }
}
