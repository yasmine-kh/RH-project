package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.service.enums.NiveauVigilance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Chiffres cles d'un trimestre pour le tableau de bord (00_DASHBOARD), tous
 * issus du moteur : rien n'est a recalculer ni a coder en dur cote ecran.
 *
 * @param nbTalents              talents proposes (10_TALENTS, Talent propose)
 * @param nbHautsPotentiels      hauts potentiels proposes, talents compris
 * @param vigilanceParNiveau     employes par niveau de vigilance, chaque
 *                               niveau present (0 si personne), du plus faible
 *                               au plus eleve
 * @param nbPostesCritiques      postes critiques suivis
 * @param tauxCouverture         part des postes critiques hors alerte, sur 100
 *                               au centieme ; null sans poste critique
 * @param alertesPostesCritiques postes critiques en alerte, par Poste_ID
 * @param repartition9Box        employes par case de la matrice, chaque case
 *                               presente (0 si vide), dans l'ordre des cases
 * @param nbNonPlaces9Box        scores du trimestre sans case 9-box (placement
 *                               pas encore lance ou scores incomplets)
 */
public record SyntheseTableauDeBord(int nbTalents, int nbHautsPotentiels,
                                    Map<NiveauVigilance, Integer> vigilanceParNiveau,
                                    int nbPostesCritiques, BigDecimal tauxCouverture,
                                    List<CouverturePoste> alertesPostesCritiques,
                                    Map<String, Integer> repartition9Box, int nbNonPlaces9Box) {

    public int nbAlertesPostesCritiques() {
        return alertesPostesCritiques.size();
    }

    /** Employes a vigilance moderee ou elevee, comme {@link ResultatVigilance#estARisque()}. */
    public int nbARisque() {
        return vigilanceParNiveau.entrySet().stream()
                .filter(entree -> entree.getKey() != NiveauVigilance.FAIBLE)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public int nbPlaces9Box() {
        return repartition9Box.values().stream().mapToInt(Integer::intValue).sum();
    }
}
