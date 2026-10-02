package com.talent360bank.talent360bank.ui.model;

/**
 * Une option d'un menu deroulant de filtre : la valeur envoyee dans l'URL et
 * le libelle affiche. Le template compare la valeur au filtre applique.
 */
public record Choix(String valeur, String libelle) {
}
