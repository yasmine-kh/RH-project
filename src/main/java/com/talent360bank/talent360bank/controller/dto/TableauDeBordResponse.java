package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthese du tableau de bord telle que l'API l'expose. Les postes en alerte
 * sont aplatis : leur Poste porte des relations LAZY.
 */
public record TableauDeBordResponse(int nbTalents, int nbHautsPotentiels,
                                    Map<String, Integer> vigilanceParNiveau, int nbARisque,
                                    int nbPostesCritiques, int nbAlertesPostesCritiques,
                                    BigDecimal tauxCouverture,
                                    List<CouverturePosteResponse> alertesPostesCritiques,
                                    Map<String, Integer> repartition9Box, int nbNonPlaces9Box) {

    public static TableauDeBordResponse de(SyntheseTableauDeBord synthese) {
        Map<String, Integer> vigilance = new LinkedHashMap<>();
        for (Map.Entry<NiveauVigilance, Integer> entree : synthese.vigilanceParNiveau().entrySet()) {
            vigilance.put(entree.getKey().name(), entree.getValue());
        }
        return new TableauDeBordResponse(synthese.nbTalents(), synthese.nbHautsPotentiels(),
                vigilance, synthese.nbARisque(),
                synthese.nbPostesCritiques(), synthese.nbAlertesPostesCritiques(), synthese.tauxCouverture(),
                synthese.alertesPostesCritiques().stream().map(CouverturePosteResponse::de).toList(),
                synthese.repartition9Box(), synthese.nbNonPlaces9Box());
    }
}
