package com.talent360bank.talent360bank.ui.model;

import java.time.LocalDateTime;

/**
 * Une decision du Comite Talent saisie dans l'application : quand, par qui, avec quel commentaire.
 *
 * @param auteur      login du compte RH, null si inconnu (saisie hors compte)
 * @param commentaire null sans commentaire
 */
public record DecisionSaisie(LocalDateTime date, String auteur, String commentaire) {
}
