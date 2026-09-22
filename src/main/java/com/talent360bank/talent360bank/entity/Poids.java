package com.talent360bank.talent360bank.entity;

import java.math.BigDecimal;

/** Outils de controle des blocs de poids d'un {@link Parametre}. */
final class Poids {

    static final BigDecimal TOTAL_ATTENDU = new BigDecimal("100");

    private Poids() {
    }

    /**
     * Vrai si la somme vaut exactement 100. Un poids absent rend le controle
     * inoperant : c'est @NotNull qui signale le champ manquant, pas la somme.
     */
    static boolean sommeValide(BigDecimal... valeurs) {
        BigDecimal somme = BigDecimal.ZERO;
        for (BigDecimal valeur : valeurs) {
            if (valeur == null) {
                return true;
            }
            somme = somme.add(valeur);
        }
        return somme.compareTo(TOTAL_ATTENDU) == 0;
    }

    /** Vrai si les seuils sont strictement decroissants (aucun null tolere). */
    static boolean ordreDecroissant(BigDecimal... seuils) {
        for (int i = 0; i < seuils.length - 1; i++) {
            if (seuils[i] == null || seuils[i + 1] == null) {
                return true;
            }
            if (seuils[i].compareTo(seuils[i + 1]) <= 0) {
                return false;
            }
        }
        return true;
    }
}
