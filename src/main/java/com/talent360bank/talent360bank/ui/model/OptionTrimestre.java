package com.talent360bank.talent360bank.ui.model;

/**
 * Un trimestre pour le selecteur des ecrans (attribut de modele
 * "trimestres") et le trimestre affiche (attribut "trimestre").
 *
 * @param valeur  valeur du parametre d'URL ?trimestre=, ex. "2026-3"
 * @param libelle libelle affiche, ex. "T3 2026"
 * @param hasData vrai si le trimestre a des scores calcules ; un trimestre
 *                cree mais pas encore importe ni calcule n'en a pas
 */
public record OptionTrimestre(String valeur, String libelle, int annee, int numero, boolean hasData) {
}
