package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.NiveauVigilance;
import com.talent360bank.talent360bank.entity.SignalVigilance;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Indice de vigilance d'un employe sur un trimestre, le niveau qui en decoule
 * et les signaux qui l'ont produit.
 *
 * <p>Les signaux sont conserves parce qu'un indice seul ne dit pas quoi faire :
 * c'est la liste des signaux qui indique au RH sur quoi agir pour retenir la
 * personne.
 */
public record ResultatVigilance(Employe employe, BigDecimal indice, NiveauVigilance niveau,
                                Set<SignalVigilance> signaux) {

    public boolean estARisque() {
        return niveau != NiveauVigilance.FAIBLE;
    }
}
