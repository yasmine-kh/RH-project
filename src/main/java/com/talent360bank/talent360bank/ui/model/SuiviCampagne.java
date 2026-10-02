package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;

import java.math.BigDecimal;
import java.util.List;

/**
 * Avancement de la campagne d'evaluation d'un trimestre, a partir des seules
 * donnees importees : qui a son evaluation du manager (performance ET
 * potentiel), par direction ou par entite fille.
 *
 * @param entite     l'entite demandee (code, libelle, type), null pour toute la banque
 * @param total      toute la population retenue (la banque ou l'entite demandee)
 * @param lignes     une ligne par direction (sans entite demandee) ou par entite fille de
 *                   l'entite demandee, plus une ligne pour ceux qui y sont rattaches directement
 * @param remarques  ce que les donnees ne permettent pas de suivre (dates, auto-evaluation...)
 */
public record SuiviCampagne(TrimestreFiche trimestre, VueEntite.EntiteRef entite, Ligne total, List<Ligne> lignes,
                            List<String> remarques) {

    /**
     * Avancement d'un groupe de collaborateurs actifs.
     *
     * @param code               code de l'entite, null pour la banque entiere ou sans entite
     * @param nbEvalues          evalues par leur manager en performance ET en potentiel ce trimestre
     * @param pourcentageEvalues nbEvalues / nbActifs x 100, 1 decimale ; null sans actif
     * @param nbManquants        nbActifs - nbEvalues (= alertes "Evaluation du manager manquante")
     * @param nbAutoEvaluations  actifs avec une auto-evaluation (performance ou potentiel)
     * @param managers           managers ayant au moins un collaborateur non evalue, du plus en retard
     *                           au moins en retard
     */
    public record Ligne(String code, String libelle, String type, int nbActifs, int nbEvalues,
                        BigDecimal pourcentageEvalues, int nbManquants, int nbAutoEvaluations,
                        List<ManagerEnRetard> managers) {
    }

    /**
     * @param matricule null pour les collaborateurs sans manager
     * @param manquants matricules des collaborateurs de son equipe sans evaluation complete
     */
    public record ManagerEnRetard(String matricule, String nom, int nbManquants, List<String> manquants) {
    }
}
