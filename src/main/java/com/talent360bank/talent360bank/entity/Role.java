package com.talent360bank.talent360bank.entity;

/**
 * Profil d'un compte. Spring Security le voit comme l'autorite
 * "ROLE_" + name() : hasRole("RH") designe {@link #RH}.
 */
public enum Role {

    /** Voit ses propres resultats. */
    COLLABORATEUR,

    /** Voit son equipe, et ses propres resultats. */
    MANAGER,

    /** Modules talents et succession. */
    COMITE,

    /** Seul administrateur : imports, reglages, referentiels, comptes. */
    RH;

    /** Autorite Spring Security correspondante. */
    public String autorite() {
        return "ROLE_" + name();
    }

    /** Un compte de ce profil doit etre rattache a un collaborateur. */
    public boolean exigeCollaborateur() {
        return this == COLLABORATEUR || this == MANAGER;
    }
}
