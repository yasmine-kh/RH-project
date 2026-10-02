package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.AlertesView.Compteur;

import java.util.List;

/**
 * Notifications de l'utilisateur RH : un resume des alertes du dernier
 * trimestre qui a des scores, sans table ni etat lu / non lu (un seul
 * utilisateur RH). Memes chiffres que l'ecran Alertes, par construction.
 *
 * @param trimestreLibelle "T3 2026", null s'il n'existe aucun trimestre
 * @param total            nombre d'alertes du trimestre (= total de l'ecran Alertes)
 * @param parSeverite      un compteur par gravite, toutes presentes
 * @param parType          un compteur par type, tous presents
 * @param alertes          les {@link #NB_ALERTES} premieres, de la plus grave a la moins grave, avec leur lien
 * @param lienAlertes      l'ecran Alertes de ce trimestre, null sans trimestre
 * @param informations     informations des regles (ex. : pas de trimestre precedent)
 * @param erreur           reglages absents ou incomplets ; sinon null
 */
public record Notifications(String trimestreLibelle, int total, List<Compteur> parSeverite, List<Compteur> parType,
                            List<AlerteVue> alertes, String lienAlertes, List<String> informations, String erreur) {

    public static final int NB_ALERTES = 10;

    /**
     * Le badge de la cloche, pour le layout : peu de champs, peu de requetes.
     *
     * @param critiques alertes Critique ; {@code elevees} et {@code moyennes} de meme
     */
    public record Badge(String trimestreLibelle, int total, int critiques, int elevees, int moyennes,
                        String lienAlertes) {
    }
}
