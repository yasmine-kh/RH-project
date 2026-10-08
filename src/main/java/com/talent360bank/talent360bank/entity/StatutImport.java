package com.talent360bank.talent360bank.entity;

/**
 * Issue d'un import de classeur, ecrite dans ImportExcel.statut (le code ne change pas : SUCCES,
 * PARTIEL, ECHEC). Les ecrans affichent le libelle (Reussi, Partiel, Echec).
 */
public enum StatutImport {
    /** Toutes les feuilles lues, aucune ligne ecartee. */
    SUCCES("Réussi"),
    /** Des lignes ou des feuilles ont ete ecartees, le reste est en base. */
    PARTIEL("Partiel"),
    /** Rien n'a ete ecrit : fichier illisible, aucune ligne valide ou erreur inattendue. */
    ECHEC("Échec");

    private final String libelle;

    StatutImport(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Libelle d'un code enregistre ; le code lui-meme s'il est inconnu, "—" s'il manque. */
    public static String libelle(String code) {
        if (code == null || code.isBlank()) {
            return "—";
        }
        for (StatutImport statut : values()) {
            if (statut.name().equals(code)) {
                return statut.libelle;
            }
        }
        return code;
    }
}
