package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.entity.Entite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedList;
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
 * @param performance       score officiel et notes de l'evaluation du manager ; null sans
 *                          evaluation du manager en performance ce trimestre
 * @param potentiel         idem pour le potentiel
 * @param neufBox           null si le collaborateur n'est pas place dans la matrice
 * @param engagement        score du questionnaire sur 100, null sans questionnaire
 * @param vigilance         null si l'indice n'est pas calculable (reglages absents)
 * @param successions       postes critiques ou il est successeur identifie, avec le plus grand gap
 * @param posteCible        poste critique ou son matching est le meilleur (PosteCibleService) ;
 *                          null sans score complet ou sans reglages
 * @param historique        trimestres precedents, du plus recent au plus ancien
 * @param autoEvaluation    auto-evaluation face a l'evaluation du manager ; null sans auto-evaluation
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
        PosteCible posteCible,
        List<HistoriqueTrimestre> historique,
        AutoEvaluation autoEvaluation,
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

        /** null pour une entite inconnue ; le chemin remonte les parents jusqu'a la direction. */
        public static EntiteFiche de(Entite entite) {
            if (entite == null) {
                return null;
            }
            LinkedList<String> chemin = new LinkedList<>();
            for (Entite niveau = entite; niveau != null; niveau = niveau.getParent()) {
                chemin.addFirst(niveau.getLibelle());
            }
            return new EntiteFiche(entite.getCode(), entite.getLibelle(),
                    entite.getType() == null ? null : entite.getType().name(), List.copyOf(chemin));
        }
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
     * Auto-evaluation du trimestre (le fichier rempli par le collaborateur),
     * comparee a l'evaluation de son manager, axe par axe.
     *
     * @param performance null sans auto-evaluation de performance
     * @param potentiel   null sans auto-evaluation de potentiel
     */
    public record AutoEvaluation(EvaluationAuto performance, EvaluationAuto potentiel) {
    }

    /**
     * Un axe de l'auto-evaluation. Chaque score est calcule par la formule du
     * moteur (poids des criteres) sur les notes de sa source ; les ecarts sont
     * auto - manager (positif : le collaborateur se note plus haut que son manager).
     *
     * @param score        score de l'auto-evaluation sur 100, null sans reglages
     * @param categorie    categorie de ce score (memes seuils que le score officiel)
     * @param scoreManager score de l'evaluation du manager, meme calcul ; null sans elle
     * @param ecart        score - scoreManager, null si l'un manque
     */
    public record EvaluationAuto(BigDecimal score, String categorie, String categorieLibelle,
                                 BigDecimal scoreManager, BigDecimal ecart, List<CritereAuto> criteres) {
    }

    /**
     * Un critere des deux evaluations.
     *
     * @param note        note de l'auto-evaluation sur 100
     * @param noteManager note du manager, null sans evaluation du manager
     * @param ecart       note - noteManager, null sans evaluation du manager
     */
    public record CritereAuto(String code, String libelle, BigDecimal note, BigDecimal noteManager,
                              BigDecimal ecart, BigDecimal poids) {
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

    /**
     * Poste critique pour lequel le collaborateur est successeur identifie.
     *
     * @param gapCompetence competence la plus eloignee du niveau requis (09_SUCCESSION!M),
     *                      null sans aucun ecart
     * @param gapNiveaux    niveaux manquants sur cette competence, 0 sans ecart
     */
    public record Succession(String posteId, String nomPoste, String direction, String criticite,
                             BigDecimal scoreMatching, String readiness, String readinessLibelle,
                             String gapCompetence, int gapNiveaux) {
    }

    /**
     * Poste cible : le poste critique (hors celui dont il est titulaire) ou son
     * matching est le meilleur ; a egalite, le plus petit Poste_ID.
     *
     * @param successeurIdentifie vrai si le RH l'a designe successeur de ce poste
     * @param gapCompetence       competence la plus eloignee du niveau requis, null sans ecart
     * @param gapNiveaux          niveaux manquants sur cette competence, 0 sans ecart
     */
    public record PosteCible(String posteId, String nomPoste, String direction, String criticite,
                             BigDecimal scoreMatching, String readiness, String readinessLibelle,
                             boolean successeurIdentifie, String gapCompetence, int gapNiveaux) {
    }

    /** @param neufBox null si le collaborateur n'etait pas place ce trimestre-la */
    public record HistoriqueTrimestre(int annee, int numero, String libelle, BigDecimal scorePerformance,
                                      BigDecimal scorePotentiel, CaseNeufBox neufBox) {
    }
}
