package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultats de l'equipe d'un manager sur un trimestre, prets pour la page
 * "Vue manager" et pour l'API
 * GET /api/trimestres/{annee}/{numero}/managers/{matricule}/vue.
 *
 * <p>Aucun calcul ici : les valeurs viennent des services du moteur (voir
 * VueManagerViewService). Memes blocs de base que la fiche collaborateur
 * (trimestre, entite, case 9-box).
 *
 * @param membres           equipe directe, triee par nom puis prenom
 * @param alertes           vigilance elevee et evaluations manquantes, dans l'ordre des membres
 * @param autoVsManager     toujours null pour l'instant (voir la TODO)
 * @param donneesManquantes une phrase par manque qui touche toute la vue (reglages absents...)
 */
public record VueManager(
        TrimestreFiche trimestre,
        ManagerVue manager,
        Synthese synthese,
        List<Membre> membres,
        List<Alerte> alertes,
        // TODO auto-evaluation : ecart auto-evaluation / evaluation du manager par membre, a remplir
        // quand le modele AUTO / MANAGER de Dou sera en place. Le type pourra changer avec ce modele.
        List<EcartAutoManager> autoVsManager,
        List<String> donneesManquantes) {

    /** Le manager lui-meme ; neufBox est sa propre case sur le trimestre, null s'il n'est pas place. */
    public record ManagerVue(String matricule, String nom, String prenom, String fonction, EntiteFiche entite,
                             CaseNeufBox neufBox) {
    }

    /**
     * Synthese de l'equipe. Les moyennes portent sur les membres qui ont la
     * valeur, arrondies a 2 decimales ; null si aucun ne l'a. Chaque
     * repartition liste toutes les valeurs possibles, a 0 si besoin.
     *
     * @param nbAvecScore      membres avec un score de performance et de potentiel sur le trimestre
     * @param niveauxVigilance membres par niveau, sans ceux dont la vigilance est absente
     * @param nbSansVigilance  membres sans vigilance (aucune donnee de vigilance, ou reglages absents)
     */
    public record Synthese(int effectif, int nbAvecScore, BigDecimal moyennePerformance, BigDecimal moyennePotentiel,
                           List<Compte> categoriesPerformance, List<Compte> categoriesPotentiel,
                           List<CompteCase> neufBox, int nbTalents, int nbHautsPotentiels, int nbVivierSuccession,
                           BigDecimal moyenneEngagement, List<Compte> niveauxVigilance, int nbSansVigilance) {
    }

    public record Compte(String code, String libelle, int nombre) {
    }

    /** Nombre de membres dans la case numero 1 a 9 ; libelle actuel de la case. */
    public record CompteCase(int numero, String libelle, int nombre) {
    }

    /**
     * Un membre de l'equipe directe.
     *
     * @param aDesDonnees vrai si le membre a une evaluation (performance ou
     *                    potentiel) ou un score sur le trimestre
     */
    public record Membre(String matricule, String nom, String prenom, String fonction,
                         BigDecimal scorePerformance, String categoriePerformance, String categoriePerformanceLibelle,
                         BigDecimal scorePotentiel, String categoriePotentiel, String categoriePotentielLibelle,
                         CaseNeufBox neufBox, Boolean estTalent, Boolean estHautPotentiel, Boolean estVivierSuccession,
                         BigDecimal engagement, BigDecimal indiceVigilance, String niveauVigilance,
                         String niveauVigilanceLibelle, boolean aDesDonnees) {
    }

    /** @param type VIGILANCE_ELEVEE ou EVALUATION_MANQUANTE */
    public record Alerte(String matricule, String nom, String type, String message) {
    }

    /** Placeholder du futur ecart auto-evaluation / evaluation manager (voir la TODO). */
    public record EcartAutoManager(String matricule, BigDecimal ecartPerformance, BigDecimal ecartPotentiel) {
    }

    /** Un manager dans la liste de choix : son equipe directe hors collaborateurs archives. */
    public record ManagerResume(String matricule, String nom, String prenom, String fonction, EntiteFiche entite,
                                int tailleEquipe) {
    }
}
