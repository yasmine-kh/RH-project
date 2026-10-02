package com.talent360bank.talent360bank.ui.model;

/**
 * Une alerte du trimestre affiche, prete pour l'ecran Alertes et le panneau du
 * tableau de bord.
 *
 * @param sujet           collaborateur ("Prenom Nom") ou poste critique (nom du poste)
 * @param matricule       matricule du collaborateur, ou Poste_ID pour un poste
 * @param entite          libelle de l'entite la plus fine (agence, region...), null si inconnue
 * @param direction       direction du collaborateur ou du poste, null si inconnue (filtre de l'ecran)
 * @param lien            ou aller pour traiter l'alerte (fiche collaborateur, postes critiques ou
 *                        comite), deja pour le trimestre affiche ; jamais l'API
 * @param entiteCode      code de cette entite (lien vers la Vue entite), null si inconnue
 * @param managerMatricule manager du collaborateur concerne (lien vers la Vue manager), null sinon
 * @param managerNom      "Prenom Nom" de ce manager, null sinon
 */
public record AlerteVue(TypeAlerte type, SeveriteAlerte severite, String sujet, String matricule, String entite,
                        String direction, String message, String lien, String lienLibelle, String entiteCode,
                        String managerMatricule, String managerNom) {

    /** Sans entite ni manager lies. */
    public AlerteVue(TypeAlerte type, SeveriteAlerte severite, String sujet, String matricule, String entite,
                     String direction, String message, String lien, String lienLibelle) {
        this(type, severite, sujet, matricule, entite, direction, message, lien, lienLibelle, null, null, null);
    }
}
