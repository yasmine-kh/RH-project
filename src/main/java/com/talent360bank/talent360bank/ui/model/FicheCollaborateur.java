package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tout ce que le RH voit pour UN collaborateur sur UN trimestre, pret pour la
 * page "Fiche collaborateur" et pour l'API
 * GET /api/trimestres/{annee}/{numero}/collaborateurs/{matricule}/fiche.
 *
 * <p>Aucun calcul ici : les valeurs viennent des services du moteur (voir
 * FicheCollaborateurViewService). Un bloc sans donnees est null (ou une liste
 * vide) et la raison est dans {@link #donneesManquantes()}.
 *
 * @param performance       null sans notes de performance ce trimestre
 * @param potentiel         null sans notes de potentiel ce trimestre
 * @param neufBox           null si le collaborateur n'est pas place dans la matrice
 * @param engagement        score du questionnaire sur 100, null sans questionnaire
 * @param vigilance         null si l'indice n'est pas calculable (reglages absents)
 * @param historique        trimestres precedents, du plus recent au plus ancien
 * @param autoEvaluation    toujours null pour l'instant (voir la TODO)
 * @param donneesManquantes une phrase par donnee absente, pour l'afficher telle quelle
 */
public record FicheCollaborateur(
        TrimestreFiche trimestre,
        Identite identite,
        Evaluation performance,
        Evaluation potentiel,
        CaseNeufBox neufBox,
        Talent talent,
        List<Competence> competences,
        BigDecimal engagement,
        Vigilance vigilance,
        List<Succession> successions,
        List<HistoriqueTrimestre> historique,
        // TODO auto-evaluation : a remplir quand le modele AUTO / MANAGER de Dou sera en place
        // (deux evaluations par collaborateur et trimestre). Le type pourra changer avec ce modele.
        Evaluation autoEvaluation,
        List<String> donneesManquantes) {

    /** Trimestre de la fiche ; l'anciennete est mesuree a sa date de reference. */
    public record TrimestreFiche(int annee, int numero, String libelle, LocalDate dateReference) {
    }

    /**
     * @param fonction   poste occupe (fonction du fichier collaborateurs)
     * @param anciennete annees decimales arrondies au dixieme a la date de reference du
     *                   trimestre, comme 01_COLLABORATEURS!G (meme calcul que le matching)
     * @param manager    null sans manager
     */
    public record Identite(String matricule, String nom, String prenom, String fonction, String grade,
                           EntiteFiche entite, ManagerFiche manager, String statut, LocalDate dateEntree,
                           BigDecimal anciennete) {
    }

    /**
     * @param chemin libelles de la direction jusqu'a l'entite, ex. ["Reseau Retail", "Nord", "Tanger"]
     */
    public record EntiteFiche(String code, String libelle, String type, List<String> chemin) {
    }

    public record ManagerFiche(String matricule, String nom) {
    }

    /**
     * Performance ou potentiel.
     *
     * @param score     score enregistre sur 100, null s'il n'est pas encore calcule
     * @param categorie code de la categorie (ex. SOLIDE, ELEVE), null sans score
     */
    public record Evaluation(BigDecimal score, String categorie, String categorieLibelle,
                             List<Critere> criteres) {
    }

    /** Une note sur 100 et son poids dans le score (reglages du trimestre). */
    public record Critere(String code, String libelle, BigDecimal note, BigDecimal poids) {
    }

    /**
     * Case 9-box : numero de 1 (faible / faible) a 9 (eleve / eleve), deduit des
     * niveaux comme au placement, et libelle actuel de la case dans la matrice.
     */
    public record CaseNeufBox(int numero, String libelle) {
    }

    /**
     * @param estTalent           null si les scores ne permettent pas de statuer
     * @param estVivierSuccession talent OU haut potentiel (vivier de releve)
     * @param decisionComite      OUI, NON, EN_ATTENTE, ou null si rien n'est saisi
     */
    public record Talent(Boolean estTalent, Boolean estHautPotentiel, Boolean estVivierSuccession,
                         String decisionComite, String decisionComiteLibelle, List<VivierFiche> viviers) {
    }

    /**
     * @param origine THEMATIQUE pour le vivier deduit de la direction, sinon
     *                l'origine de l'appartenance enregistree (MOTEUR, IMPORT, SAISIE_RH...)
     */
    public record VivierFiche(String code, String libelle, String origine) {
    }

    /**
     * @param statut PRIORITAIRE, A_DEVELOPPER ou MAITRISE, null si un niveau manque
     */
    public record Competence(String competenceId, String nom, String categorie, Integer niveauRequis,
                             Integer niveauActuel, Integer gap, String statut, String statutLibelle) {
    }

    public record Vigilance(BigDecimal indice, String niveau, String niveauLibelle, List<RaisonVigilance> raisons) {
    }

    public record RaisonVigilance(String code, String libelle, BigDecimal points) {
    }

    /** Poste critique pour lequel le collaborateur est successeur identifie. */
    public record Succession(String posteId, String nomPoste, String direction, String criticite,
                             BigDecimal scoreMatching, String readiness, String readinessLibelle) {
    }

    /** @param neufBox null si le collaborateur n'etait pas place ce trimestre-la */
    public record HistoriqueTrimestre(int annee, int numero, String libelle, BigDecimal scorePerformance,
                                      BigDecimal scorePotentiel, CaseNeufBox neufBox) {
    }
}
