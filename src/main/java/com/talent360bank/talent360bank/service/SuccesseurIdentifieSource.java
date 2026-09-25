package com.talent360bank.talent360bank.service;

import java.util.List;

/**
 * Successeurs identifies d'un poste, tels que le RH les a designes
 * (09_SUCCESSION, colonnes Poste_ID et Employee_ID).
 *
 * <p>Ces successeurs sont une saisie, pas un calcul : le classement de
 * {@link SuccessionService} propose des candidats, le RH en retient certains.
 * C'est sur ces seuls successeurs que se juge la couverture d'un poste
 * critique ; un poste sans successeur retenu est en alerte meme si de bons
 * candidats existent.
 *
 * <p>Contrat cote moteur, sans dependance a la couche de persistance : le
 * depot de la future entite SuccesseurIdentifie l'implemente ou s'y adapte.
 * Tant qu'aucune implementation n'est declaree, {@link PosteCritiqueService}
 * considere qu'aucun successeur n'est identifie.
 */
public interface SuccesseurIdentifieSource {

    /**
     * Employee_ID des successeurs identifies pour le poste, jamais null.
     * L'ordre et les doublons sont sans importance.
     */
    List<String> successeursIdentifies(String posteId);
}
