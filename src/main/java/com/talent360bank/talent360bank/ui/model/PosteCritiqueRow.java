package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;

/**
 * Une ligne du tableau Postes critiques, prete pour l'affichage.
 * Aucun calcul ici : tout vient de CouverturePoste (PosteCritiqueService, Jas).
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

    public PosteCritiqueRow(String nomPoste, String direction, String criticite, String titulaire,
                            int nbSuccesseurs, String candidatPotentiel, BigDecimal scoreMatching,
                            String statut, boolean enAlerte) {
        this.nomPoste = nomPoste;
        this.direction = direction;
        this.criticite = criticite;
        this.titulaire = titulaire;
        this.nbSuccesseurs = nbSuccesseurs;
        this.candidatPotentiel = candidatPotentiel;
        this.scoreMatching = scoreMatching;
        this.statut = statut;
        this.enAlerte = enAlerte;
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
}