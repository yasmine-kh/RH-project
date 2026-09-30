package com.talent360bank.talent360bank.securite;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charge un compte par son login pour la connexion par formulaire.
 *
 * <p>Un compte est refuse (Spring Security leve DisabledException) s'il est
 * desactive, ou si son collaborateur n'est plus ACTIF : un collaborateur parti
 * ne se connecte plus, meme si personne n'a pense a fermer son compte. La page
 * de connexion affiche le meme message que pour un mauvais mot de passe.
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
        Utilisateur utilisateur = utilisateurRepository.findByLoginAvecCollaborateur(login)
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));

        Collaborateur collaborateur = utilisateur.getCollaborateur();
        boolean collaborateurActif = collaborateur == null
                || collaborateur.getStatut() == StatutCollaborateur.ACTIF;

        return new UtilisateurConnecte(utilisateur.getLogin(), utilisateur.getMotDePasseHash(),
                utilisateur.isActif() && collaborateurActif, utilisateur.getRole(),
                collaborateur == null ? null : collaborateur.getIdCollaborateur());
    }
}
