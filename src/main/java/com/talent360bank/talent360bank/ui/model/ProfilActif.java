package com.talent360bank.talent360bank.ui.model;

import java.io.Serializable;

/**
 * Le profil que le RH a choisi, garde en session HTTP : une vue, pas une
 * connexion (un seul compte RH, aucun droit en plus ou en moins). Il ne change
 * que le menu de la sidebar et son bandeau ; toutes les pages restent
 * accessibles par leur URL.
 *
 * @param type       RH, COLLABORATEUR, MANAGER ou COMITE (l'entite reste une page RH, pas un profil)
 * @param matricule  la personne choisie (collaborateur ou manager), null pour RH et Comite
 * @param nom        "Nom Prenom" de cette personne, lu une fois au choix
 * @param entiteCode le code de son entite (lien de la campagne du collaborateur), null si inconnue
 */
public record ProfilActif(String type, String matricule, String nom, String entiteCode) implements Serializable {

    /** Nom de l'attribut de session. */
    public static final String SESSION = "profilActif";

    public static final ProfilActif RH = new ProfilActif("RH", null, null, null);
    public static final ProfilActif COMITE = new ProfilActif("COMITE", null, null, null);

    public boolean estRh() {
        return "RH".equals(type);
    }

    /** "Manager — Nom Prenom", "Comité Talent / Direction"... pour le bandeau. */
    public String libelle() {
        return switch (type) {
            case "COLLABORATEUR" -> "Collaborateur — " + nom;
            case "MANAGER" -> "Manager — " + nom;
            case "COMITE" -> "Comité Talent / Direction";
            default -> "RH";
        };
    }
}
