package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * Les chiffres du moteur qu'affiche le tableau de bord RH pour un trimestre (DashboardService) :
 * aucun chiffre n'est calcule ni ecrit en dur cote ecran. Le reste de la page vient de
 * TableauDeBordInteractif.
 *
 * @param trimestreLibelle "T3 2026", null s'il n'existe aucun trimestre
 * @param kpis             cartes du moteur : postes critiques, couverture, successions Ready Now, postes
 *                         sans successeur, postes en alerte ; sans reglages : effectif et engagement
 *                         seulement ; vide sans trimestre
 * @param neufBox          les 9 cases, performance elevee en haut, potentiel faible a gauche ; vide sans reglages
 * @param erreur           reglages absents ou incomplets pour le trimestre : seuls les chiffres qui n'en
 *                         dependent pas sont affiches ; sinon null
 */
public record TableauDeBordView(String trimestreLibelle, List<KpiCard> kpis, List<CaseTableau> neufBox,
                                String erreur) {

    /** Une case de la matrice : niveaux 1 (faible) a 3 (eleve), libelle actuel et effectif. */
    public record CaseTableau(String libelle, int niveauPerformance, int niveauPotentiel, int nombre) {
    }
}
