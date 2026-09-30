package com.talent360bank.talent360bank.entity;

/** Issue d'un import de classeur, ecrite dans ImportExcel.statut. */
public enum StatutImport {
    /** Toutes les feuilles lues, aucune ligne ecartee. */
    SUCCES,
    /** Des lignes ou des feuilles ont ete ecartees, le reste est en base. */
    PARTIEL,
    /** Rien n'a ete ecrit : fichier illisible, aucune ligne valide ou erreur inattendue. */
    ECHEC
}
