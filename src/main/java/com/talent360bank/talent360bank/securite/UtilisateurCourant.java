package com.talent360bank.talent360bank.securite;

import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Le compte RH connecte pour la requete en cours, tel qu'enregistre en base (table utilisateur).
 * Vide hors requete authentifiee (initialiseurs, taches) ou si le login n'a pas de compte
 * (utilisateur de test) : l'appelant enregistre alors null.
 */
@Component
public class UtilisateurCourant {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurCourant(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    public Optional<Utilisateur> utilisateur() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated()
                || authentification instanceof AnonymousAuthenticationToken || authentification.getName() == null) {
            return Optional.empty();
        }
        return utilisateurRepository.findByLogin(authentification.getName());
    }
}
