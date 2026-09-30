package com.talent360bank.talent360bank.securite;

import com.talent360bank.talent360bank.entity.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Compte connecte, tel que Spring Security le garde en session. Porte en plus
 * le role et le matricule du collaborateur rattache : le futur
 * PerimetreService en aura besoin pour limiter ce que chacun voit.
 */
public class UtilisateurConnecte extends User {

    private final Role role;
    private final String idCollaborateur;

    public UtilisateurConnecte(String login, String motDePasseHash, boolean actif, Role role,
                               String idCollaborateur) {
        super(login, motDePasseHash, actif, true, true, true,
                List.of(new SimpleGrantedAuthority(role.autorite())));
        this.role = role;
        this.idCollaborateur = idCollaborateur;
    }

    public Role getRole() {
        return role;
    }

    /** Matricule du collaborateur rattache, null pour un compte RH ou COMITE sans rattachement. */
    public String getIdCollaborateur() {
        return idCollaborateur;
    }
}
