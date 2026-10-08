package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tableau de bord RH interactif (page "/") : cartes, graphiques et listes d'un
 * trimestre, filtres croises par les parametres de l'URL. Le serveur rend la vue
 * deja filtree (lien partageable, page sans JavaScript) ; la page embarque aussi
 * toute la population ({@link #donneesJson}) pour que tableau-de-bord.js refiltre
 * sur place, avec les memes regles (TableauDeBordInteractifService).
 *
 * <p>Aucun calcul du moteur ici : chaque personne porte les resultats deja calcules
 * (case 9-Box, categories, vigilance, viviers, talent...) ; filtrer, compter et
 * moyenner l'engagement sont les seules syntheses, comme sur l'ancien tableau de bord.
 *
 * @param puces              filtres actifs, dans l'ordre des parametres
 * @param kpis               les 10 cartes du prototype (renderDashRH), dans son ordre, 2 rangees de 5 ; celles
 *                           sur les personnes suivent les filtres, celles sur les postes critiques non
 * @param neufBox            les 9 cases dans l'ordre de la grille (potentiel eleve en haut, performance elevee
 *                           a droite), effectifs filtres par tous les filtres sauf la case
 * @param personnes          personnes filtrees, performance + potentiel decroissants
 * @param alertes            alertes filtrees, les plus graves d'abord
 * @param viviers            chaque vivier et ses membres filtres
 * @param donneesJson        toute la population du trimestre en JSON (echappee par th:attr dans la page)
 */
public record TableauDeBordInteractif(Filtres filtres, List<Puce> puces, List<Kpi> kpis,
                                      List<CaseCompte> neufBox, List<Personne> personnes,
                                      List<AlerteLigne> alertes, List<VivierListe> viviers, String donneesJson) {

    /** Parametres de l'URL, dans l'ordre ; une valeur inconnue est ignoree. */
    public static final List<String> PARAMETRES = List.of("case", "entite", "vigilance", "perf", "pot", "vivier",
            "alerte");

    /**
     * Filtres actifs ; null = pas de filtre sur cette dimension.
     *
     * @param caseNeufBox numero 1 a 9
     * @param entite      code de la direction
     * @param vigilance   FAIBLE, MODEREE, ELEVEE
     * @param perf        categorie de performance (EXCEPTIONNELLE...)
     * @param pot         categorie de potentiel (ELEVE, MOYEN, FAIBLE)
     * @param vivier      code du vivier (COMMERCIAL..., RELEVE)
     * @param alerte      type d'alerte (VIGILANCE_ELEVEE...)
     */
    public record Filtres(Integer caseNeufBox, String entite, String vigilance, String perf, String pot,
                          String vivier, String alerte) {

        public boolean vide() {
            return caseNeufBox == null && entite == null && vigilance == null && perf == null && pot == null
                    && vivier == null && alerte == null;
        }
    }

    /** Un filtre actif : "9-Box : Talent cle", et le lien qui le retire. */
    public record Puce(String parametre, String dimension, String valeur, String lienRetrait) {
    }

    /**
     * @param classe classe de carte du prototype : "", gold, teal ou rust
     * @param filtre vrai si la carte suit les filtres (personnes) ; faux pour les postes critiques
     */
    public record Kpi(String code, String libelle, String valeur, String classe, boolean filtre) {
    }

    /**
     * @param classe    fond de la case du prototype (talent, high, watch ou "")
     * @param etiquette "emoji libelle", pret a afficher
     */
    public record CaseCompte(int numero, String libelle, int niveauPerformance, int niveauPotentiel, int nombre,
                             boolean selectionnee, String lien, String classe, String etiquette) {
    }

    /**
     * Une personne de la population calculee et ses resultats du trimestre.
     *
     * @param directionCode code de la direction (filtre "entite"), null sans entite
     * @param viviers       codes des viviers (thematique, RELEVE)
     * @param caseEtiquette "emoji libelle" de la case 9-Box, "—" si non placee
     * @param viviersTexte  libelles des viviers separes par des virgules, "—" sans vivier
     */
    public record Personne(String matricule, String nomComplet, String lien, String directionCode, String direction,
                           Integer caseNumero, String caseLibelle, BigDecimal performance, String categoriePerformance,
                           BigDecimal potentiel, String categoriePotentiel, String vigilance,
                           String vigilanceLibelle, BigDecimal engagement, boolean estTalent,
                           boolean estHautPotentiel, boolean estTalentValide, boolean estReleve,
                           List<String> viviers, String caseEtiquette, String viviersTexte) {
    }

    /**
     * Une alerte du trimestre (AlertesViewService).
     *
     * @param personne vrai si l'alerte porte sur une personne de la population (sinon : poste critique)
     * @param classe   crit, warn ou info (prototype)
     */
    public record AlerteLigne(String type, String typeLibelle, String severite, String classe, String sev,
                              String sujet, String matricule, boolean personne, String direction, String message,
                              String lien, String lienLibelle) {
    }

    public record VivierListe(String code, String libelle, List<Personne> membres) {
    }
}
