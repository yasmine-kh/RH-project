package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;

/**
 * Un successeur evalue d'un poste critique, a valider par le Comite Talent. Aucun calcul ici : le
 * matching vient de PosteCritiqueService, la decision de validation_succession.
 *
 * @param decision        code de DecisionSuccession, null sans decision
 * @param decisionLibelle libelle de la decision, null sans decision
 * @param enAttente       sans decision ou "A reevaluer" : compte dans "Successions a valider"
 * @param saisie          date, auteur et commentaire de la decision ; null sans decision
 */
public record SuccessionComiteRow(String posteId, String nomPoste, String matricule, String nomComplet,
                                  BigDecimal scoreMatching, String readinessLibelle, String decision,
                                  String decisionLibelle, boolean enAttente, DecisionSaisie saisie) {

    /** Ancre de la ligne dans la page. */
    public String ancre() {
        return "succession-" + posteId + "-" + matricule;
    }
}
