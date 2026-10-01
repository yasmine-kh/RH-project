package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.model.VueManager.Synthese;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultats agreges d'une entite de l'organigramme (direction, departement,
 * region ou agence) et de tout son sous-arbre sur un trimestre, prets pour la
 * page "Vue entite" et pour l'API
 * GET /api/trimestres/{annee}/{numero}/entites/{code}/vue.
 *
 * <p>Aucun calcul ici : la synthese a la forme et le calcul de celle de la
 * vue manager (meme code, ResultatsCollaborateurs), sur les collaborateurs du
 * sous-arbre hors ARCHIVE.
 *
 * @param synthese          tout le sous-arbre : l'entite et ses descendants
 * @param enfants           comparaison des enfants directs, un par ligne, par libelle
 * @param postesCritiques   postes critiques de l'entite ou tenus par un collaborateur du sous-arbre
 * @param alertes           vigilance ELEVEE ou au-dela dans le sous-arbre, indice decroissant
 * @param managers          managers qui travaillent dans le sous-arbre, par nom (lien vers la vue manager)
 * @param donneesManquantes une phrase par manque qui touche la vue
 */
public record VueEntite(
        TrimestreFiche trimestre,
        EntiteVue entite,
        Synthese synthese,
        List<ComparaisonEnfant> enfants,
        List<PosteCritiqueVue> postesCritiques,
        List<AlerteVigilance> alertes,
        List<ManagerResume> managers,
        List<String> donneesManquantes) {

    /**
     * @param chemin  libelles de la direction jusqu'a l'entite
     * @param parent  null pour une direction
     * @param enfants enfants directs par libelle, avec l'effectif de leur sous-arbre
     */
    public record EntiteVue(String code, String libelle, String type, List<String> chemin, EntiteRef parent,
                            List<EntiteEnfant> enfants) {
    }

    public record EntiteRef(String code, String libelle, String type) {
    }

    /** @param effectif collaborateurs du sous-arbre de l'enfant, hors archives */
    public record EntiteEnfant(String code, String libelle, String type, int effectif) {
    }

    /**
     * Chiffres cles d'un enfant direct, sur son sous-arbre, pour comparer par
     * exemple les agences d'une region.
     *
     * @param pourcentageTalents talents / collaborateurs avec un score x 100, 1 decimale ;
     *                           null si personne n'a de score
     * @param nbVigilanceElevee  collaborateurs au niveau de vigilance ELEVEE ou au-dela
     */
    public record ComparaisonEnfant(String code, String libelle, String type, int effectif, int nbAvecScore,
                                    BigDecimal moyennePerformance, BigDecimal moyennePotentiel,
                                    BigDecimal pourcentageTalents, BigDecimal moyenneEngagement,
                                    int nbVigilanceElevee) {
    }

    /**
     * Un poste critique et sa couverture (PosteCritiqueService).
     *
     * @param rattachement ENTITE si le poste est rattache au sous-arbre (les
     *                     postes le sont a leur direction), TITULAIRE si seul
     *                     son titulaire y travaille
     * @param couverture   ALERTE, READY_NOW, MOINS_1_AN ou PARTIELLE ; null
     *                     (comme les champs suivants) sans reglages pour le trimestre
     */
    public record PosteCritiqueVue(String posteId, String nomPoste, String direction, String criticite,
                                   String titulaireId, String titulaireNom, String rattachement,
                                   String couverture, String couvertureLibelle, Boolean alerte,
                                   Integer nbSuccesseurs, BigDecimal meilleurMatching) {
    }

    public record AlerteVigilance(String matricule, String nom, String prenom, EntiteRef entite,
                                  BigDecimal indice, String niveau, String niveauLibelle) {
    }

    /**
     * Un noeud de l'organigramme pour la page de choix.
     *
     * @param effectif collaborateurs du sous-arbre (le noeud et ses descendants), hors archives
     */
    public record NoeudEntite(String code, String libelle, String type, int effectif, List<NoeudEntite> enfants) {
    }
}
