package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Poste;

/**
 * Poste cible d'un collaborateur : le poste critique sur lequel son matching
 * est le meilleur (a egalite, le plus petit Poste_ID).
 *
 * @param matching            matching, readiness et plus grand gap sur ce poste
 * @param successeurIdentifie vrai si le RH l'a designe successeur de ce poste
 *                            (09_SUCCESSION) ; faux si le poste ressort du seul calcul
 */
public record ResultatPosteCible(Collaborateur collaborateur, Poste poste, ResultatMatching matching,
                                 boolean successeurIdentifie) {
}
