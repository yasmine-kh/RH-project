package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * Ecran Alertes d'un trimestre : compteurs sur toutes les alertes, puis les
 * lignes qui passent les filtres.
 *
 * @param trimestreLibelle "T3 2026", null s'il n'existe aucun trimestre
 * @param total            nombre d'alertes du trimestre, avant filtres (= panneau du tableau de bord)
 * @param parType          un compteur par type d'alerte, tous presents (0 si aucune), avant filtres
 * @param parSeverite      un compteur par gravite, toutes presentes, avant filtres
 * @param alertes          alertes filtrees, de la plus grave a la moins grave
 * @param directions       directions presentes dans les alertes, pour le filtre
 * @param filtres          filtres appliques, a recopier dans le formulaire
 * @param informations     ce que les regles n'ont pas pu faire, sans etre une erreur (ex. : pas de
 *                         trimestre precedent pour reperer les nouveaux talents) ; vide sinon
 * @param erreur           reglages absents ou incomplets : rien n'est calculable ; sinon null
 */
public record AlertesView(String trimestreLibelle, int total, List<Compteur> parType, List<Compteur> parSeverite,
                          List<AlerteVue> alertes, List<String> directions, Filtres filtres,
                          List<String> informations, String erreur) {

    /** Un compteur par type ou par gravite. */
    public record Compteur(String code, String libelle, int nombre) {
    }

    /** Valeurs des filtres ; null = pas de filtre. */
    public record Filtres(String type, String severite, String direction, String recherche) {

        public static final Filtres AUCUN = new Filtres(null, null, null, null);
    }
}
