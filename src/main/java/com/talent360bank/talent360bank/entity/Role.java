package com.talent360bank.talent360bank.entity;

/**
 * Profil d'un compte. Spring Security le voit comme l'autorite
 * "ROLE_" + name() : hasRole("RH") designe {@link #RH}.
 *
 * <p>Decision du client : seul le RH se connecte. Collaborateurs et managers
 * n'utilisent pas l'application, ils remplissent les fichiers Excel importes.
 * L'enum est garde (colonne utilisateur.role) pour ne pas figer ce choix.
 */
public enum Role {

    /** Seul profil : consulte tous les resultats, importe, regle, administre. */
    RH;

    /** Autorite Spring Security correspondante. */
    public String autorite() {
        return "ROLE_" + name();
    }
}
