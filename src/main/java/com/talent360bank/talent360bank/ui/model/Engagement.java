package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * L'ecran Engagement &amp; Fidelisation d'un trimestre : reponses au questionnaire
 * (reponse_questionnaire), score d'engagement importe (12_VIGILANCE), reglages RH des questions
 * (dimension, question eNPS). Aucun chiffre n'est devine : sans reglage, "Non configure".
 *
 * @param population      collaborateurs actifs
 * @param nbRepondants    actifs qui ont au moins une reponse au questionnaire ce trimestre
 * @param tauxReponse     nbRepondants / population en %, 1 decimale ; null sans population
 * @param engagementMoyen moyenne des scores d'engagement /100 des actifs, 2 decimales ; null sans score
 * @param nbScores        nombre de scores d'engagement de cette moyenne
 * @param enps            null tant qu'aucune question eNPS n'est reglee (page : "Non configuré")
 * @param dimensions      vide tant qu'aucune question n'a de dimension
 */
public record Engagement(String trimestreLibelle, int population, int nbRepondants, BigDecimal tauxReponse,
                         BigDecimal engagementMoyen, int nbScores, Enps enps, List<Dimension> dimensions,
                         List<Question> questions, List<ParEntite> entites) {

    public boolean dimensionsConfigurees() {
        return !dimensions.isEmpty();
    }

    /** Nature d'une question, deduite de ses reponses (EngagementViewService.nature). */
    public enum Nature {
        NUMERIQUE("Échelle numérique"), CHOIX("Choix"), TEXTE("Réponse libre");

        private final String libelle;

        Nature(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    /**
     * Une question, dans l'ordre des colonnes du fichier.
     *
     * @param moyenne      moyenne des reponses numeriques (2 decimales), null si la question n'est pas numerique
     * @param distribution effectif de chaque reponse (echelle ou choix), vide pour une reponse libre
     * @param textes       reponses libres avec leur auteur, vide sinon
     */
    public record Question(String code, String texte, String dimension, boolean questionEnps, Nature nature,
                           int nbReponses, BigDecimal moyenne, List<Valeur> distribution, List<Texte> textes) {
    }

    /** @param pourcentage part des reponses a la question, 1 decimale */
    public record Valeur(String valeur, int nombre, BigDecimal pourcentage) {
    }

    public record Texte(String matricule, String nomComplet, String texte) {
    }

    /**
     * Moyenne des reponses numeriques des questions d'une dimension (la question eNPS a part).
     *
     * @param moyenne null si aucune reponse numerique
     */
    public record Dimension(String nom, List<String> codes, int nbReponses, BigDecimal moyenne) {
    }

    /**
     * eNPS : % promoteurs (9-10) moins % detracteurs (0-6), sur les reponses entieres de 0 a 10.
     *
     * @param valeur     null sans reponse valide
     * @param horsEchelle reponses ignorees (pas un entier de 0 a 10)
     */
    public record Enps(String code, String question, int nbReponses, int promoteurs, int passifs, int detracteurs,
                       BigDecimal valeur, int horsEchelle) {
    }

    /**
     * Une direction (Sans direction pour un collaborateur sans entite connue).
     *
     * @param tauxReponse     en %, 1 decimale
     * @param engagementMoyen moyenne des scores d'engagement de ses actifs, null sans score
     */
    public record ParEntite(String direction, int effectif, int repondants, BigDecimal tauxReponse,
                            BigDecimal engagementMoyen) {
    }
}
