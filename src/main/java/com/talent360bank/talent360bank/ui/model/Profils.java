package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * Options du selecteur de profil de la sidebar. Les "profils" sont des vues
 * que le RH ouvre (collaborateur, manager, comite), pas des connexions : un
 * seul compte RH. Les entites restent dans le menu RH (Organigramme).
 *
 * @param collaborateurs matricule et "Nom Prenom", par nom (hors archives)
 * @param managers       matricule et "Nom Prenom" de chaque manager, par nom
 */
public record Profils(List<Choix> collaborateurs, List<Choix> managers) {

    /** Les profils du premier menu, dans l'ordre d'affichage (comme le prototype : pas de profil Entite). */
    public static final List<Choix> TYPES = List.of(
            new Choix("RH", "RH"),
            new Choix("COLLABORATEUR", "Collaborateur"),
            new Choix("MANAGER", "Manager"),
            new Choix("COMITE", "Comité Talent"));

    /** Les profils du premier menu (pour le template). */
    public List<Choix> types() {
        return TYPES;
    }
}
