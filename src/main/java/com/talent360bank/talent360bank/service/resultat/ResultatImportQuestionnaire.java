package com.talent360bank.talent360bank.service.resultat;

import java.util.List;

/**
 * Bilan de l'import d'un fichier de reponses au questionnaire d'engagement.
 *
 * @param statut           SUCCES, PARTIEL (des lignes ecartees) ou ECHEC (rien d'enregistre)
 * @param nbCollaborateurs collaborateurs dont les reponses ont ete enregistrees
 * @param nbReponses       reponses enregistrees (une par question repondue)
 * @param nbQuestions      questions lues dans l'en-tete du fichier
 * @param erreurs          lignes ecartees ou remplacees, une phrase chacune
 * @param message          cause d'un echec complet (fichier illisible, pas de colonne Matricule), null sinon
 */
public record ResultatImportQuestionnaire(String nomFichier, String trimestreLibelle, String trimestreValeur,
                                          String statut, int nbCollaborateurs, int nbReponses, int nbQuestions,
                                          List<String> erreurs, String message) {

    /** Reussi, Partiel ou Echec. */
    public String statutLibelle() {
        return com.talent360bank.talent360bank.entity.StatutImport.libelle(statut);
    }
}
