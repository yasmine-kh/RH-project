package com.talent360bank.talent360bank.service.resultat;

/**
 * Competence exigee par le poste ou le candidat est le plus loin du niveau
 * requis : colonne "Plus grand gap" de 09_SUCCESSION (M).
 *
 * <p>Comme le classeur (INDEX/MATCH sur le plus petit sous-score), a egalite
 * c'est la premiere competence dans l'ordre du poste (07_POSTES, competences
 * requises 1 a 5) qui est retenue. Sans aucun ecart, c'est donc la premiere
 * competence du poste, avec {@code ecart} = 0 : l'ecran doit alors afficher
 * "aucun ecart" plutot que le nom de la competence ({@link #aUnEcart()}).
 *
 * @param niveauActuel niveau retenu pour le candidat ; le niveau par defaut du
 *                     bareme si la competence est absente de son profil, comme
 *                     le sous-score competences
 * @param ecart        niveaux manquants : max(0, requis - actuel). Depasser le
 *                     niveau requis ne donne pas d'ecart negatif, comme le
 *                     sous-score plafonne a 100
 */
public record PlusGrandGap(String competenceId, String competence, int niveauRequis, int niveauActuel,
                           int ecart) {

    /** Vrai si le candidat manque d'au moins un niveau sur cette competence. */
    public boolean aUnEcart() {
        return ecart > 0;
    }
}
