package com.talent360bank.talent360bank.ui.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reponses d'un collaborateur au questionnaire d'engagement pour un trimestre,
 * telles qu'importees (ImportQuestionnaireService), pour la section "Reponses
 * au questionnaire" de la fiche. Aucun calcul ni regroupement : une ligne par
 * question, dans l'ordre des colonnes du fichier (Q01, Q02...).
 *
 * @param trimestreValeur "AAAA-N" du trimestre affiche
 * @param dateReponse     horodatage du formulaire, null s'il manque
 * @param reponses        vide si aucun questionnaire n'a ete importe pour ce trimestre
 */
public record ReponsesQuestionnaire(String trimestreValeur, String trimestreLibelle, LocalDateTime dateReponse,
                                    List<Reponse> reponses) {

    /**
     * @param code     Q01, Q02... : rang de la question dans le fichier
     * @param theme    vide pour l'instant (le fichier n'en fournit pas)
     * @param question texte de l'en-tete, tel qu'ecrit
     */
    public record Reponse(String code, String theme, String question, String reponse) {
    }

    public boolean vide() {
        return reponses.isEmpty();
    }

    /** Vrai si au moins une reponse a un theme : la colonne Theme n'est affichee qu'alors. */
    public boolean avecTheme() {
        return reponses.stream().anyMatch(r -> r.theme() != null && !r.theme().isBlank());
    }
}
