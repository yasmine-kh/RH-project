package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;

import java.math.BigDecimal;

/**
 * Resultat du rapprochement d'un candidat et d'un poste cible : le score de
 * matching sur 100, le delai de disponibilite qui en decoule, et le detail des
 * six criteres.
 *
 * <p>Le detail est conserve parce qu'un score global seul ne se defend pas
 * devant un comite : il faut pouvoir dire si le candidat est ecarte pour ses
 * competences ou pour son anciennete.
 */
public record ResultatMatching(Employe candidat, BigDecimal scoreMatching,
                               NiveauReadiness readiness, DetailMatching detail) {

    /**
     * Sous-scores des six criteres, chacun sur 100.
     * Un critere a null n'a pas pu etre evalue (donnee absente) : il est alors
     * ecarte de la moyenne au lieu de compter pour zero.
     */
    public record DetailMatching(BigDecimal competences, BigDecimal performance,
                                 BigDecimal potentiel, BigDecimal experience,
                                 BigDecimal leadership, BigDecimal mobilite) {
    }
}
