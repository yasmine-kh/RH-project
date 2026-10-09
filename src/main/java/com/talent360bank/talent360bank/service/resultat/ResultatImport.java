package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.StatutImport;

import java.util.List;

/**
 * Bilan d'un import de classeur, tel que rendu au RH et journalise dans
 * ImportExcel.
 *
 * @param idImport   identifiant de l'entree du journal, nul pour une simulation
 * @param simulation import valide puis annule : rien n'est enregistre
 * @param nbLignes   lignes ecrites en base, toutes feuilles confondues
 * @param feuilles   bilan de chaque feuille, dans l'ordre d'import
 * @param erreurs    lignes ou feuilles ecartees, avec leur motif
 * @param desactives collaborateurs absents de 01_COLLABORATEURS, passes INACTIF
 * @param reactives  collaborateurs qui n'etaient pas actifs et que le fichier remet ACTIF
 * @param message    cause d'un echec complet, nul sinon
 * @param decisionsConservees decisions du Comite saisies dans l'application que le classeur contredit
 *                   ou ne porte plus : gardees, signalees ici (sans compter comme erreurs)
 */
public record ResultatImport(Integer idImport, String nomFichier, int annee, int numero, boolean simulation,
                             StatutImport statut, int nbLignes, List<BilanFeuille> feuilles,
                             List<ErreurImport> erreurs, List<String> desactives, List<String> reactives,
                             String message, List<ErreurImport> decisionsConservees) {

    /** Sans decision de l'application a signaler. */
    public ResultatImport(Integer idImport, String nomFichier, int annee, int numero, boolean simulation,
                          StatutImport statut, int nbLignes, List<BilanFeuille> feuilles,
                          List<ErreurImport> erreurs, List<String> desactives, List<String> reactives,
                          String message) {
        this(idImport, nomFichier, annee, numero, simulation, statut, nbLignes, feuilles, erreurs, desactives,
                reactives, message, List.of());
    }

    /**
     * @param presente   la feuille et son en-tete ont ete trouves
     * @param nbLignes   lignes ecrites en base
     * @param nbErreurs  lignes signalees (ecartees, ou importees sans une valeur)
     * @param nbRetirees lignes du trimestre supprimees : leur collaborateur n'est plus dans la feuille
     */
    public record BilanFeuille(String feuille, boolean presente, int nbLignes, int nbErreurs, int nbRetirees) {
    }

    /**
     * @param ligne numero de ligne Excel (1 pour la premiere), nul pour une
     *              erreur qui porte sur toute la feuille
     */
    public record ErreurImport(String feuille, Integer ligne, String message) {
    }

    public int nbErreurs() {
        return erreurs.size();
    }
}
