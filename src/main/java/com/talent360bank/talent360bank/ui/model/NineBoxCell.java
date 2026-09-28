package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * Une case de la grille 9-Box, prete pour l'affichage.
 * Toutes les donnees viennent de Matrice9Box (Jas/Dou) et de Score (Jas) :
 * cette classe ne fait aucun calcul, elle ne fait que porter les valeurs.
 */
public class NineBoxCell {

    private final String categorie;
    private final int niveauPerformance;
    private final int niveauPotentiel;
    private final List<String> employes;

    public NineBoxCell(String categorie, int niveauPerformance, int niveauPotentiel, List<String> employes) {
        this.categorie = categorie;
        this.niveauPerformance = niveauPerformance;
        this.niveauPotentiel = niveauPotentiel;
        this.employes = employes;
    }

    public String getCategorie() {
        return categorie;
    }

    public int getNiveauPerformance() {
        return niveauPerformance;
    }

    public int getNiveauPotentiel() {
        return niveauPotentiel;
    }

    public List<String> getEmployes() {
        return employes;
    }

    public int getNombreEmployes() {
        return employes.size();
    }
}