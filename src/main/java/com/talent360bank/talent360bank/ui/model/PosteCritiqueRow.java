package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Une ligne du tableau Postes critiques, prete pour l'affichage.
 * Aucun calcul ici : tout vient de CouverturePoste (PosteCritiqueService, Jas).
 *
 * <p>{@code candidatPotentiel} et {@code scoreMatching} sont ceux du meilleur
 * successeur ; {@code successeurs} les donne tous, du meilleur matching au moins bon.
 */
public class PosteCritiqueRow {

    private final String nomPoste;
    private final String direction;
    private final String criticite;
    private final String titulaire;
    private final int nbSuccesseurs;
    private final String candidatPotentiel;
    private final BigDecimal scoreMatching;
    private final String statut;
    private final boolean enAlerte;
    private final List<SuccesseurRow> successeurs;

    /**
     * Un successeur identifie evalue sur le poste.
     *
     * @param readiness     code stable (READY_NOW, MOINS_1_AN...) ; afficher readinessLibelle
     * @param gapCompetence competence la plus eloignee du niveau requis (09_SUCCESSION!M),
     *                      null si gapNiveaux = 0 (aucun ecart) ou si le poste n'exige rien
     * @param gapNiveaux    niveaux manquants sur cette competence, 0 sans ecart
     */
    public record SuccesseurRow(String matricule, String nomComplet, BigDecimal scoreMatching, String readiness,
                                String readinessLibelle, String gapCompetence, int gapNiveaux) {
    }

    public PosteCritiqueRow(String nomPoste, String direction, String criticite, String titulaire,
                            int nbSuccesseurs, String candidatPotentiel, BigDecimal scoreMatching,
                            String statut, boolean enAlerte, List<SuccesseurRow> successeurs) {
        this.nomPoste = nomPoste;
        this.direction = direction;
        this.criticite = criticite;
        this.titulaire = titulaire;
        this.nbSuccesseurs = nbSuccesseurs;
        this.candidatPotentiel = candidatPotentiel;
        this.scoreMatching = scoreMatching;
        this.statut = statut;
        this.enAlerte = enAlerte;
        this.successeurs = List.copyOf(successeurs);
    }

    public String getNomPoste() {
        return nomPoste;
    }

    public String getDirection() {
        return direction;
    }

    public String getCriticite() {
        return criticite;
    }

    public String getTitulaire() {
        return titulaire;
    }

    public int getNbSuccesseurs() {
        return nbSuccesseurs;
    }

    public String getCandidatPotentiel() {
        return candidatPotentiel;
    }

    public BigDecimal getScoreMatching() {
        return scoreMatching;
    }

    public String getStatut() {
        return statut;
    }

    public boolean isEnAlerte() {
        return enAlerte;
    }

    public List<SuccesseurRow> getSuccesseurs() {
        return successeurs;
    }
}
