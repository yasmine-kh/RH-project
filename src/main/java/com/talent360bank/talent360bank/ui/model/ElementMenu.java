package com.talent360bank.talent360bank.ui.model;

/**
 * Une entree du menu de la sidebar.
 *
 * @param numero numero affiche devant le libelle : "01", "02"... dans l'ordre du menu (MenuProfilService.numeroter)
 * @param icone  classe Bootstrap Icons (ex. "bi-house") ; le menu affiche le numero, l'icone reste en titre
 * @param lien   la page (jamais l'API), filtree sur la personne ou l'equipe du profil
 * @param page   l'identifiant de page ("activePage") qui surligne cette entree ; null si
 *               l'entree mene a une section d'une page deja surlignee par une autre entree
 * @param indication pour une entree grisee (lien null) : ce qui manque, ex. "choisir un manager"
 */
public record ElementMenu(String numero, String libelle, String icone, String lien, String page, String indication) {

    public ElementMenu(String numero, String libelle, String icone, String lien, String page) {
        this(numero, libelle, icone, lien, page, null);
    }

    /** Entree grisee : le profil demande d'abord une personne. */
    public static ElementMenu grisee(String numero, String libelle, String icone, String indication) {
        return new ElementMenu(numero, libelle, icone, null, null, indication);
    }

    public boolean active() {
        return lien != null;
    }
}
