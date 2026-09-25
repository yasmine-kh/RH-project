package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;

import java.math.BigDecimal;
import java.util.List;

/**
 * Couverture d'un poste critique. Le poste est aplati : ses competences
 * requises sont LAZY et n'ont pas leur place dans une ligne d'alerte.
 */
public record CouverturePosteResponse(String posteId, String nomPoste, String direction, String criticite,
                                      String titulaireId, String titulaireNom,
                                      int nbSuccesseurs, BigDecimal meilleurMatching,
                                      String couverture, String couvertureLibelle, boolean alerte,
                                      List<MatchingResponse> successeurs,
                                      List<SuccesseurIgnoreResponse> ignores) {

    public record SuccesseurIgnoreResponse(String employeeId, String motif) {
    }

    public static CouverturePosteResponse de(CouverturePoste couverture) {
        Poste poste = couverture.poste();
        return new CouverturePosteResponse(poste.getPosteId(), poste.getNomPoste(), poste.getDirection(),
                poste.getCriticite(), poste.getTitulaireId(), poste.getTitulaireNom(),
                couverture.nbSuccesseurs(), couverture.meilleurMatching(),
                couverture.niveau().name(), couverture.niveau().getLibelle(), couverture.estEnAlerte(),
                couverture.successeurs().stream().map(MatchingResponse::de).toList(),
                couverture.ignores().stream()
                        .map(ignore -> new SuccesseurIgnoreResponse(ignore.employeeId(), ignore.motif()))
                        .toList());
    }
}
