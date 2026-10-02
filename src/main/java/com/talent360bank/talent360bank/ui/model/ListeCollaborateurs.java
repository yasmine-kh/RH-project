package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.ManagerFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;

import java.math.BigDecimal;
import java.util.List;

/**
 * Liste des collaborateurs d'un trimestre (ecran Collaborateurs) : une ligne
 * par collaborateur actif, filtree, triee et paginee, prete pour l'affichage
 * et pour l'API GET /api/trimestres/{annee}/{numero}/collaborateurs.
 *
 * <p>Aucun calcul ici : les valeurs viennent du moteur (voir
 * ListeCollaborateursViewService).
 *
 * @param criteres          filtres, tri et page effectivement appliques
 * @param nbTotal           collaborateurs actifs du trimestre, avant filtres
 * @param nbFiltres         collaborateurs qui passent les filtres (toutes pages)
 * @param nbPages           pages pour {@code nbFiltres} et la taille de page, au moins 1
 * @param lignes            la page demandee
 * @param donneesManquantes une phrase par donnee absente, pour l'afficher telle quelle
 */
public record ListeCollaborateurs(TrimestreFiche trimestre, Criteres criteres, int nbTotal, int nbFiltres,
                                  int nbPages, List<LigneCollaborateur> lignes, List<String> donneesManquantes) {

    /** Colonnes de tri. Les valeurs inconnues sont toujours en fin de liste, quel que soit l'ordre. */
    public enum Tri {
        NOM, MATRICULE, PERFORMANCE, POTENTIEL, MATCHING, VIGILANCE
    }

    /**
     * Filtres, tri et pagination. Un filtre null n'est pas applique.
     *
     * @param entite    code d'une entite : ses collaborateurs et ceux de tout son sous-arbre
     * @param caseNeufBox numero de case 1 a 9 (9 = performance et potentiel eleves)
     * @param talent    true : talents proposes seulement ; false : les autres
     * @param vivier    code d'un vivier thematique (COMMERCIAL...) ou RELEVE
     * @param readiness readiness sur le poste cible (READY_NOW, MOINS_1_AN, ENTRE_1_ET_2_ANS, PLUS_2_ANS)
     * @param vigilance niveau de vigilance (FAIBLE, MODEREE, ELEVEE)
     * @param recherche texte cherche dans le nom, le prenom ou le matricule, sans accents ni casse
     * @param page      numero de page, a partir de 1
     * @param taille    lignes par page, 1 a {@link #TAILLE_MAX}
     */
    public record Criteres(String entite, Integer caseNeufBox, Boolean talent, String vivier, String readiness,
                           String vigilance, String recherche, Tri tri, boolean decroissant, int page,
                           int taille) {

        public static final int TAILLE_PAR_DEFAUT = 20;
        public static final int TAILLE_MAX = 100;
    }

    /**
     * Un collaborateur sur le trimestre. Un bloc sans donnees est null.
     *
     * @param direction          libelle de la direction de son entite
     * @param poste              fonction occupee (01_COLLABORATEURS!L)
     * @param neufBox            case 1 a 9 et libelle actuel ; null s'il n'est pas place
     * @param estTalent          talent propose (10_TALENTS!E) ; null sans score complet
     * @param estHautPotentiel   haut potentiel propose (10_TALENTS!F)
     * @param estTalentValide    talent propose ET decision du comite Oui (10_TALENTS!H)
     * @param decisionComite     OUI / NON / EN_ATTENTE pour un talent propose, null sinon
     * @param viviers            vivier thematique de sa direction, puis le vivier de releve s'il en est
     * @param posteCible         poste critique ou son matching est le meilleur (PosteCibleService)
     * @param niveauVigilance    FAIBLE / MODEREE / ELEVEE ; null sans aucune donnee de vigilance
     */
    public record LigneCollaborateur(String matricule, String nom, String prenom, String nomComplet,
                                     EntiteRef entite, String direction, ManagerFiche manager, String poste,
                                     String grade, BigDecimal scorePerformance, BigDecimal scorePotentiel,
                                     CaseNeufBox neufBox, Boolean estTalent, Boolean estHautPotentiel,
                                     boolean estTalentValide, String decisionComite, List<VivierRef> viviers,
                                     PosteCibleLigne posteCible, BigDecimal indiceVigilance,
                                     String niveauVigilance, String niveauVigilanceLibelle) {
    }

    /** @param code COMMERCIAL, DIGITAL, EXPERTISE, MANAGEMENT, RISQUES ou RELEVE */
    public record VivierRef(String code, String libelle) {
    }

    /**
     * @param gapCompetence competence la plus eloignee du niveau requis, null sans ecart
     * @param gapNiveaux    niveaux manquants sur cette competence, 0 sans ecart
     */
    public record PosteCibleLigne(String posteId, String nomPoste, BigDecimal scoreMatching, String readiness,
                                  String readinessLibelle, boolean successeurIdentifie, String gapCompetence,
                                  int gapNiveaux) {
    }
}
