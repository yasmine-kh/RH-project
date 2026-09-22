package com.talent360bank.talent360bank.entity;

/**
 * Niveau d'un axe de la matrice 9-box. Sert indifferemment a la performance
 * et au potentiel : les deux axes utilisent les memes seuils.
 */
public enum NiveauGrille {
    FAIBLE(1),
    MOYEN(2),
    ELEVE(3);

    private final int rang;

    NiveauGrille(int rang) {
        this.rang = rang;
    }

    /** Rang 1 a 3, aligne sur Matrice9Box.niveauPerformance / niveauPotentiel. */
    public int getRang() {
        return rang;
    }

    public static NiveauGrille depuisRang(int rang) {
        for (NiveauGrille niveau : values()) {
            if (niveau.rang == rang) {
                return niveau;
            }
        }
        throw new IllegalArgumentException("Rang de grille invalide : " + rang);
    }
}
