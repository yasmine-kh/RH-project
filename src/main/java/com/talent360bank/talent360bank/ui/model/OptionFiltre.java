package com.talent360bank.talent360bank.ui.model;

/**
 * Une option d'un menu deroulant de filtre (trimestre, statut...), prete pour
 * l'affichage : la valeur envoyee dans l'URL, le libelle affiche et si elle
 * est selectionnee.
 */
public class OptionFiltre {

    private final String valeur;
    private final String libelle;
    private final boolean selectionnee;

    public OptionFiltre(String valeur, String libelle, boolean selectionnee) {
        this.valeur = valeur;
        this.libelle = libelle;
        this.selectionnee = selectionnee;
    }

    public String getValeur() {
        return valeur;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean isSelectionnee() {
        return selectionnee;
    }
}
