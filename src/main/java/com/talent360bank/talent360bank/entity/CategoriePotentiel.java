package com.talent360bank.talent360bank.entity;

/**
 * Etiquette individuelle de potentiel, comme la colonne Categorie Potentiel
 * de 03_POTENTIEL. C'est le niveau de l'axe potentiel de la matrice 9-box :
 * memes seuils, pas de reglage propre.
 */
public enum CategoriePotentiel {
    ELEVE("Élevé"),
    MOYEN("Moyen"),
    FAIBLE("Faible");

    private final String libelle;

    CategoriePotentiel(String libelle) {
        this.libelle = libelle;
    }

    public static CategoriePotentiel depuis(NiveauGrille niveau) {
        return switch (niveau) {
            case ELEVE -> ELEVE;
            case MOYEN -> MOYEN;
            case FAIBLE -> FAIBLE;
        };
    }

    public String getLibelle() {
        return libelle;
    }
}
