package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tout ce qu'affiche le tableau de bord RH pour un trimestre, pret pour le
 * template : aucun chiffre n'est calcule ni ecrit en dur cote ecran, tous
 * viennent du moteur (TableauDeBordService et les services qu'il assemble).
 *
 * @param trimestreLibelle  "T3 2026", null s'il n'existe aucun trimestre
 * @param kpis              cartes KPI, dans l'ordre d'affichage
 * @param neufBox           les 9 cases, performance elevee en haut, potentiel faible a gauche ;
 *                          vide sans reglages
 * @param nbPlaces9Box      scores du trimestre places dans une case
 * @param nbNonPlaces9Box   scores du trimestre sans case (placement non lance ou scores incomplets)
 * @param vigilance         collaborateurs par niveau de vigilance (Faible, Moderee, Elevee)
 * @param nbSansVigilance   actifs sans aucune donnee de vigilance : pas d'indice (regle EntreesVigilance)
 * @param alertes           alertes prioritaires (AlertesViewService, comme l'ecran Alertes), les plus
 *                          graves d'abord, au plus {@link #ALERTES_AFFICHEES}
 * @param nbAlertes         nombre total d'alertes du trimestre (= total de l'ecran Alertes)
 * @param viviers           viviers thematiques, un par ligne
 * @param erreur            reglages absents ou incomplets pour le trimestre : seuls les
 *                          chiffres qui n'en dependent pas sont affiches ; sinon null
 */
public record TableauDeBordView(String trimestreLibelle, List<KpiCard> kpis, List<CaseTableau> neufBox,
                                int nbPlaces9Box, int nbNonPlaces9Box, List<CompteNiveau> vigilance,
                                int nbSansVigilance, List<AlerteVue> alertes, int nbAlertes,
                                List<VivierTableau> viviers, String erreur) {

    /** Nombre d'alertes listees sur le tableau de bord ; la page Alertes les montrera toutes. */
    public static final int ALERTES_AFFICHEES = 6;

    /** Une case de la matrice : niveaux 1 (faible) a 3 (eleve), libelle actuel et effectif. */
    public record CaseTableau(String libelle, int niveauPerformance, int niveauPotentiel, int nombre) {
    }

    /** Effectif d'un niveau (de vigilance...). */
    public record CompteNiveau(String code, String libelle, int nombre) {
    }

    /**
     * Un vivier thematique sur le trimestre.
     *
     * @param performanceMoyenne moyenne des scores de performance des membres, 2 decimales ; null si aucun
     * @param potentielMoyen     idem pour le potentiel
     * @param nbTalents          membres talents proposes
     * @param nbReadyNow         membres prets maintenant sur au moins un poste critique
     * @param nbPostesCouverts   postes critiques dont au moins un successeur est membre du vivier
     */
    public record VivierTableau(String code, String libelle, int effectif, BigDecimal performanceMoyenne,
                                BigDecimal potentielMoyen, int nbTalents, int nbReadyNow, int nbPostesCouverts) {
    }
}
