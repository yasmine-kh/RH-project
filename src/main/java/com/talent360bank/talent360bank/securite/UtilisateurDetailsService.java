package com.talent360bank.talent360bank.securite;

import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charge un compte par son login pour la connexion par formulaire.
 *
 * <p>Un compte desactive est refuse (Spring Security leve DisabledException) ;
 * la page de connexion affiche le meme message que pour un mauvais mot de
 * passe.
 */
@Service
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        Utilisateur utilisateur = utilisateurRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));

        return User.withUsername(utilisateur.getLogin())
                .password(utilisateur.getMotDePasseHash())
                .disabled(!utilisateur.isActif())
                .authorities(utilisateur.getRole().autorite())
                .build();
    }
}
