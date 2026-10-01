package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Chiffres cles d'un trimestre pour le tableau de bord (00_DASHBOARD), tous
 * issus du moteur : rien n'est a recalculer ni a coder en dur cote ecran.
 *
 * @param nbTalents              talents proposes (10_TALENTS, Talent propose)
 * @param nbTalentsValides       talents proposes et valides par le Comite Talent
 *                               (10_TALENTS, Talent valide)
 * @param nbHautsPotentiels      hauts potentiels proposes, talents compris
 * @param vigilanceParNiveau     collaborateurs par niveau de vigilance, chaque
 *                               niveau present (0 si personne), du plus faible
 *                               au plus eleve
 * @param nbPostesCritiques      postes critiques suivis
 * @param tauxCouverture         part des postes critiques hors alerte, sur 100
 *                               au centieme ; null sans poste critique
 * @param alertesPostesCritiques postes critiques en alerte, par Poste_ID
 * @param repartition9Box        collaborateurs par case de la matrice, chaque case
 *                               presente (0 si vide), dans l'ordre des cases
 * @param nbNonPlaces9Box        scores du trimestre sans case 9-box (placement
 *                               pas encore lance ou scores incomplets)
 * @param nbVivierReleve         vivier de releve : talents OU hauts potentiels (10_TALENTS J)
 * @param couvertures            tous les postes critiques avec leurs successeurs evalues
 *                               (les alertes en sont un sous-ensemble)
 * @param vigilancesElevees      collaborateurs au niveau de vigilance ELEVEE, du plus
 *                               au moins a risque (regle EntreesVigilance)
 */
public record SyntheseTableauDeBord(int nbTalents, int nbTalentsValides, int nbHautsPotentiels,
                                    Map<NiveauVigilance, Integer> vigilanceParNiveau,
                                    int nbPostesCritiques, BigDecimal tauxCouverture,
                                    List<CouverturePoste> alertesPostesCritiques,
                                    Map<String, Integer> repartition9Box, int nbNonPlaces9Box,
                                    int nbVivierReleve, List<CouverturePoste> couvertures,
                                    List<ResultatVigilance> vigilancesElevees) {

    public int nbAlertesPostesCritiques() {
        return alertesPostesCritiques.size();
    }

    /** Collaborateurs a vigilance moderee ou elevee, comme {@link ResultatVigilance#estARisque()}. */
    public int nbARisque() {
        return vigilanceParNiveau.entrySet().stream()
                .filter(entree -> entree.getKey() != NiveauVigilance.FAIBLE)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /** Postes critiques sans aucun successeur identifie (toujours en alerte : le minimum vaut au moins 1). */
    public int nbPostesSansSuccesseur() {
        return (int) couvertures.stream().filter(couverture -> couverture.nbSuccesseurs() == 0).count();
    }

    /**
     * Successions Ready Now : couples (poste critique, successeur identifie) au
     * niveau READY_NOW, comme 00_DASHBOARD C10 (COUNTIF(09_SUCCESSION!L, "Ready Now")).
     * Un collaborateur pret sur deux postes compte deux fois.
     */
    public int nbSuccessionsReadyNow() {
        return (int) couvertures.stream()
                .flatMap(couverture -> couverture.successeurs().stream())
                .filter(successeur -> successeur.readiness() == NiveauReadiness.READY_NOW)
                .count();
    }

    public int nbPlaces9Box() {
        return repartition9Box.values().stream().mapToInt(Integer::intValue).sum();
    }
}
