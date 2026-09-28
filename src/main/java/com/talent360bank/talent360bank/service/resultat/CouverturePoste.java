package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;

import java.math.BigDecimal;
import java.util.List;

/**
 * Couverture d'un poste critique, une ligne de 08_POSTES_CRITIQUES.
 *
 * @param nbSuccesseurs successeurs identifies retenus : actifs, hors
 *                      titulaire, qu'ils aient ou non un score ce trimestre
 * @param successeurs   successeurs evalues, du meilleur matching au moins bon
 * @param ignores       successeurs identifies absents du matching, avec le
 *                      motif ; seuls ceux sans score sont comptes dans
 *                      {@code nbSuccesseurs}
 */
public record CouverturePoste(Poste poste, int nbSuccesseurs, List<ResultatMatching> successeurs,
                              List<SuccesseurIgnore> ignores, NiveauCouverture niveau) {

    public record SuccesseurIgnore(String employeeId, String motif) {
    }

    /** Meilleur successeur evalue, null si aucun ne l'a ete. */
    public ResultatMatching meilleurSuccesseur() {
        return successeurs.isEmpty() ? null : successeurs.get(0);
    }

    /** Meilleur score de matching, null si aucun successeur n'a ete evalue. */
    public BigDecimal meilleurMatching() {
        ResultatMatching meilleur = meilleurSuccesseur();
        return meilleur == null ? null : meilleur.scoreMatching();
    }

    public boolean estEnAlerte() {
        return niveau == NiveauCouverture.ALERTE;
    }
}
