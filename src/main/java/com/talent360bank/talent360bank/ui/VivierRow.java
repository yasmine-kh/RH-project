package com.talent360bank.talent360bank.ui;

import java.math.BigDecimal;

/**
 * Une ligne du tableau Viviers, prete pour l'affichage.
 * Aucun calcul ici : tout vient de Employe, Score et AppartenanceVivier.
 */
public class VivierRow {

    private final String nom;
    private final String prenom;
    private final String poste;
    private final String departement;
    private final BigDecimal potentiel;
    private final BigDecimal performance;
    private final String categorieVivier;

    public VivierRow(String nom, String prenom, String poste, String departement,
                     BigDecimal potentiel, BigDecimal performance, String categorieVivier) {
        this.nom = nom;
        this.prenom = prenom;
        this.poste = poste;
        this.departement = departement;
        this.potentiel = potentiel;
        this.performance = performance;
        this.categorieVivier = categorieVivier;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getPoste() {
        return poste;
    }

    public String getDepartement() {
        return departement;
    }

    public BigDecimal getPotentiel() {
        return potentiel;
    }

    public BigDecimal getPerformance() {
        return performance;
    }

    public String getCategorieVivier() {
        return categorieVivier;
    }
}