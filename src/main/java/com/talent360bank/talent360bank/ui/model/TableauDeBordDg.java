package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.PosteCibleLigne;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tableau de bord DG d'un trimestre, pret pour l'ecran et pour l'API
 * GET /api/dashboard/dg. Aucun chiffre calcule ici ni ecrit en dur.
 *
 * @param kpis            les cartes du tableau de bord RH (DashboardService), memes valeurs
 * @param neufBox         la matrice du tableau de bord RH
 * @param postesCritiques un poste critique par ligne, par Poste_ID
 * @param topTalents      les talents proposes, du meilleur au moins bon selon {@link #REGLE_TOP_TALENTS}
 * @param erreur          reglages absents ou incomplets : seules les cartes qui n'en dependent pas sont la
 */
public record TableauDeBordDg(String trimestreLibelle, List<KpiCard> kpis, List<CaseTableau> neufBox,
                              List<PosteDg> postesCritiques, List<TalentDg> topTalents, String erreur) {

    /** Nombre de talents affiches par defaut (le prototype en montre 8). */
    public static final int TOP_TALENTS_PAR_DEFAUT = 8;
    public static final int TOP_TALENTS_MAX = 50;

    /**
     * Le classeur ne classe pas les talents (10_TALENTS suit l'ordre des
     * matricules) : regle retenue, a confirmer avec le client.
     */
    public static final String REGLE_TOP_TALENTS = "Talents proposés (10_TALENTS!E), classés par performance "
            + "+ potentiel décroissant, puis performance décroissante, puis matricule";

    /**
     * Un poste critique (08_POSTES_CRITIQUES) et sa releve.
     *
     * @param nbSuccesseurs     successeurs identifies retenus (08_POSTES_CRITIQUES!G)
     * @param nbReadyNow        successeurs prets maintenant (09_SUCCESSION!L = Ready Now)
     * @param meilleurSuccesseur null si aucun successeur n'a pu etre evalue
     * @param couverture        code stable (READY_NOW, MOINS_1_AN, PARTIELLE, ALERTE) ; afficher couvertureLibelle
     */
    public record PosteDg(String posteId, String nomPoste, String direction, String criticite, String titulaireNom,
                          int nbSuccesseurs, int nbReadyNow, SuccesseurDg meilleurSuccesseur, String couverture,
                          String couvertureLibelle, boolean alerte) {
    }

    public record SuccesseurDg(String matricule, String nomComplet, BigDecimal scoreMatching, String readiness,
                               String readinessLibelle) {
    }

    /**
     * Un talent propose et son poste cible.
     *
     * @param rang            1 pour le premier
     * @param scoreCumule     performance + potentiel, la cle du classement
     * @param decisionComite  OUI / NON / EN_ATTENTE
     * @param posteCible      poste critique ou son matching est le meilleur ; null s'il n'y en a pas
     */
    public record TalentDg(int rang, String matricule, String nomComplet, String direction,
                           BigDecimal scorePerformance, BigDecimal scorePotentiel, BigDecimal scoreCumule,
                           boolean estHautPotentiel, String decisionComite, boolean estTalentValide,
                           PosteCibleLigne posteCible) {
    }
}
