package com.talent360bank.talent360bank.service.resultat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Competences d'une population sur un trimestre (module Competences) : une
 * ligne par competence du referentiel, les plus grands gaps moyens, et pour
 * chaque poste critique ce qui manque a ses successeurs.
 *
 * <p>Le niveau cible est celui de 06_EMPLOYEE_SKILLS (colonne F, propre a
 * chaque collaborateur et a chaque competence), comme dans le classeur : il
 * ne vient pas d'un poste. Les exigences des postes (07_POSTES) ne servent
 * qu'au bloc {@code postesCritiques}, comme dans 09_SUCCESSION.
 *
 * @param criteres          filtres et ordre appliques
 * @param nbCollaborateurs  collaborateurs actifs retenus par les filtres
 * @param competences       une ligne par competence du referentiel, triee par gap moyen
 * @param topGaps           les competences au plus grand gap moyen (au plus {@code criteres.top()})
 * @param nbGapsPrioritaires competences en gap Prioritaire de la population (00_DASHBOARD G10 sans filtre)
 * @param postesCritiques   chaque poste critique (ou celui du filtre), par Poste_ID
 */
public record SyntheseCompetences(Criteres criteres, int nbCollaborateurs, List<LigneCompetence> competences,
                                  List<LigneCompetence> topGaps, int nbGapsPrioritaires,
                                  List<PosteCompetences> postesCritiques) {

    public static final int TOP_PAR_DEFAUT = 5;

    /**
     * @param entite        code d'une entite : ses collaborateurs et ceux de son sous-arbre
     * @param vivier        code d'un vivier thematique (COMMERCIAL...) ou RELEVE
     * @param posteCritique Poste_ID d'un poste critique : ses successeurs identifies evalues
     * @param gapCroissant  true : du plus petit gap moyen au plus grand ; false (defaut) : l'inverse
     * @param top           nombre de competences dans {@code topGaps}
     */
    public record Criteres(String entite, String vivier, String posteCritique, boolean gapCroissant, int top) {
    }

    /**
     * Une competence sur la population.
     *
     * @param nbEvalues           collaborateurs qui ont les deux niveaux sur cette competence
     * @param niveauActuelMoyen   moyenne de 06!E, 2 decimales ; null sans evalue
     * @param niveauCibleMoyen    moyenne de 06!F
     * @param gapMoyen            moyenne de 06!G (cible - actuel)
     * @param nbAvecGap           statut A developper ou Prioritaire (gap &gt; 0)
     * @param pourcentageAvecGap  nbAvecGap / nbEvalues x 100, 1 decimale ; null sans evalue
     * @param nbPrioritaires      statut Prioritaire avec le seuil du trimestre (06!H)
     * @param repartition         effectif par niveau actuel, niveaux 1 a 5
     */
    public record LigneCompetence(String competenceId, String competence, String categorie, int nbEvalues,
                                  BigDecimal niveauActuelMoyen, BigDecimal niveauCibleMoyen, BigDecimal gapMoyen,
                                  int nbAvecGap, BigDecimal pourcentageAvecGap, int nbPrioritaires,
                                  List<CompteNiveau> repartition) {
    }

    public record CompteNiveau(int niveau, int nombre) {
    }

    /**
     * Un poste critique et les competences de ses successeurs identifies evalues
     * (ceux de CouverturePoste.successeurs()).
     *
     * @param exigences       les competences exigees par le poste (07_POSTES), dans son ordre
     * @param gapsFrequents   plus grands gaps (09_SUCCESSION!M) des successeurs avec un ecart reel,
     *                        du plus frequent au moins frequent
     */
    public record PosteCompetences(String posteId, String nomPoste, int nbSuccesseurs, List<Exigence> exigences,
                                   List<SyntheseVivier.GapFrequent> gapsFrequents) {
    }

    /**
     * @param niveauMoyenSuccesseurs moyenne des niveaux des successeurs (niveau par defaut du bareme
     *                               si absent, comme le matching), 2 decimales ; null sans successeur
     * @param nbSuccesseursSousLeNiveau successeurs a qui il manque au moins un niveau
     */
    public record Exigence(String competenceId, String competence, int niveauRequis,
                           BigDecimal niveauMoyenSuccesseurs, int nbSuccesseursSousLeNiveau) {
    }
}
