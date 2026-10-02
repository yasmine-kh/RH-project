package com.talent360bank.talent360bank.service.resultat;

/**
 * Une competence exigee par un poste (07_POSTES) face au niveau d'un candidat,
 * comme une colonne N a R de 09_SUCCESSION.
 *
 * @param niveauActuel niveau retenu pour le candidat ; le niveau par defaut du bareme
 *                     si la competence est absente de son profil
 * @param ecart        niveaux manquants : max(0, requis - actuel)
 */
public record EcartExigence(String competenceId, String competence, int niveauRequis, int niveauActuel,
                            int ecart) {
}
