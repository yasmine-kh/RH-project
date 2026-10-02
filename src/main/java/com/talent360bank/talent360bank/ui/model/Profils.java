package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * Options du selecteur de profil de la sidebar. Les "profils" sont des vues
 * que le RH ouvre (fiche d'un collaborateur, vue d'un manager, vue d'une
 * entite, comite), pas des connexions : un seul compte RH.
 *
 * @param collaborateurs matricule et "Nom Prenom", par nom (hors archives)
 * @param managers       matricule et "Nom Prenom" de chaque manager, par nom
 * @param entites        code et libelle, dans l'ordre de l'organigramme, decale par niveau
 */
public record Profils(List<Choix> collaborateurs, List<Choix> managers, List<Choix> entites) {

    /** Les profils du premier menu, dans l'ordre d'affichage. */
    public static final List<Choix> TYPES = List.of(
            new Choix("RH", "RH"),
            new Choix("COLLABORATEUR", "Collaborateur"),
            new Choix("MANAGER", "Manager"),
            new Choix("ENTITE", "Entité"),
            new Choix("COMITE", "Comité Talent"));

    /** Les profils du premier menu (pour le template). */
    public List<Choix> types() {
        return TYPES;
    }
}
