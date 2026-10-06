package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * La page Organigramme (/entites) : une carte par direction, ses departements en
 * arbre. Les niveaux inutiles sont fusionnes : une entite qui n'a qu'un enfant de
 * meme effectif s'affiche sur une ligne ("Siege - Casablanca / Siege").
 *
 * @param directions             les directions, dans l'ordre de l'organigramme
 * @param indicateursDisponibles faux si les reglages du trimestre manquent (talents et alertes non calcules)
 */
public record Organigramme(List<Direction> directions, boolean indicateursDisponibles) {

    /**
     * @param branche   la direction (fusionnee avec ses enfants uniques) et ses departements
     * @param nbTalents talents du trimestre dans la direction, null si non calcules
     * @param nbAlertes alertes du trimestre dans la direction, null si non calculees
     */
    public record Direction(Branche branche, Integer nbTalents, Integer nbAlertes) {
    }

    /**
     * Une ligne de l'arbre.
     *
     * @param code     le code de l'entite la plus profonde de la ligne (lien vers sa vue)
     * @param libelle  les libelles fusionnes, separes par " / "
     * @param effectif effectif du sous-arbre (inchange par la fusion)
     */
    public record Branche(String code, String libelle, String type, int effectif, List<Branche> enfants) {
    }
}
