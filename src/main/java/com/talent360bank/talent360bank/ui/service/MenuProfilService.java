package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.ElementMenu;
import com.talent360bank.talent360bank.ui.model.ProfilActif;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Le menu de la sidebar pour le profil actif, comme le prototype
 * (docs/prototype/index.html, ROLES et NAV_DEF) : memes libelles, meme ordre. Les
 * numeros ne sont plus ceux du prototype (le 02, Tableau de bord DG, a disparu) :
 * chaque menu est renumerote 01, 02, 03... dans l'ordre affiche, sans trou, entrees
 * grisees et pages propres a l'application comprises ({@link #numeroter}). Aucune requete :
 * tout vient du profil garde en session. Chaque entree mene a une page existante,
 * filtree sur la personne ou l'equipe quand la page le permet ; les modules du
 * prototype que l'application ne calcule pas encore menent a une page "En cours
 * de developpement" (PagesEnDeveloppementController). Tant qu'aucune personne n'est
 * choisie, les entrees qui en dependent sont grisees ("choisir un manager").
 */
public final class MenuProfilService {

    private MenuProfilService() {
    }

    /**
     * @param trimestre "AAAA-N" du trimestre affiche (parametre de la requete), null pour le plus recent
     */
    public static List<ElementMenu> menu(ProfilActif profil, String trimestre) {
        return numeroter(switch (profil == null ? "RH" : profil.type()) {
            case "COLLABORATEUR" -> collaborateur(profil, trimestre);
            case "MANAGER" -> manager(profil, trimestre);
            case "COMITE" -> comite(trimestre);
            default -> rh(trimestre);
        });
    }

    /**
     * Numeros "01", "02"... dans l'ordre du menu, sans trou : les numeros ecrits dans les
     * listes ci-dessous (ceux du prototype) sont remplaces.
     */
    static List<ElementMenu> numeroter(List<ElementMenu> menu) {
        return java.util.stream.IntStream.range(0, menu.size())
                .mapToObj(i -> {
                    ElementMenu e = menu.get(i);
                    return new ElementMenu(String.format(java.util.Locale.ROOT, "%02d", i + 1), e.libelle(), e.icone(),
                            e.lien(), e.page(), e.indication());
                })
                .toList();
    }

    /**
     * DRH / Talent Manager : les modules du prototype, puis les pages propres a l'application.
     * Le "02 Tableau de bord DG" du prototype est fusionne dans le tableau de bord RH : pas d'entree.
     */
    private static List<ElementMenu> rh(String t) {
        return List.of(
                new ElementMenu("01", "Tableau de bord RH", "bi-house", lien("/", t), "accueil"),
                new ElementMenu("03", "Campagne d'évaluation", "bi-clipboard-check", lien("/campagne", t), "campagne"),
                new ElementMenu("04", "Collaborateurs", "bi-person-lines-fill", lien("/collaborateurs", t), "collaborateurs"),
                new ElementMenu("05", "Matrice 9-Box", "bi-grid-3x3", lien("/9box", t), "9box"),
                new ElementMenu("06", "Compétences & Gaps", "bi-mortarboard", lien("/competences", t), "competences"),
                new ElementMenu("07", "Carrière & Mobilité", "bi-signpost-split", "/carriere-mobilite", "carriere-mobilite"),
                new ElementMenu("08", "Engagement & Fidélisation", "bi-heart", "/engagement", "engagement"),
                new ElementMenu("09", "Viviers", "bi-people", lien("/viviers", t), "viviers"),
                new ElementMenu("10", "Postes critiques & Succession", "bi-exclamation-triangle",
                        lien("/postes-critiques", t), "postes-critiques"),
                new ElementMenu("11", "Comité Talent", "bi-person-badge", lien("/comite-talent", t), "comite-talent"),
                new ElementMenu("12", "Talent Passport", "bi-person-vcard", "/fiche-collaborateur", "fiche-collaborateur"),
                new ElementMenu("13", "Alertes", "bi-bell", lien("/alertes", t), "alertes"),
                new ElementMenu("14", "Historique", "bi-clock-history", "/historique", "historique"),
                new ElementMenu("15", "Notifications", "bi-envelope", lien("/notifications", t), "notifications"),
                new ElementMenu("·", "Managers", "bi-diagram-2", lien("/managers", t), "managers"),
                new ElementMenu("·", "Organigramme", "bi-diagram-3", lien("/entites", t), "entites"),
                new ElementMenu("·", "Import", "bi-upload", "/import", "import"),
                new ElementMenu("·", "Paramètres", "bi-gear", "/parametres", "parametres"));
    }

    /**
     * Prototype : Mon profil, Mon engagement, Campagne d'evaluation, Notifications.
     * Mon profil = sa fiche ; Mon engagement (questionnaire) et Campagne (auto-evaluation)
     * sont des formulaires que l'application n'a pas encore : pages en cours de
     * developpement ; Notifications = ses alertes.
     */
    private static List<ElementMenu> collaborateur(ProfilActif p, String t) {
        if (p.matricule() == null) {
            String indication = "choisir un collaborateur";
            return List.of(
                    ElementMenu.grisee("·", "Mon profil", "bi-person-vcard", indication),
                    ElementMenu.grisee("·", "Mon engagement", "bi-heart", indication),
                    ElementMenu.grisee("03", "Campagne d'évaluation", "bi-clipboard-check", indication),
                    ElementMenu.grisee("15", "Notifications", "bi-envelope", indication));
        }
        String fiche = LiensPages.fiche(p.matricule(), t);
        UriComponentsBuilder alertes = UriComponentsBuilder.fromPath("/alertes");
        if (t != null) {
            alertes.queryParam("trimestre", t);
        }
        alertes.queryParam("q", p.matricule());
        return List.of(
                new ElementMenu("·", "Mon profil", "bi-person-vcard", fiche, "fiche-collaborateur"),
                new ElementMenu("·", "Mon engagement", "bi-heart", "/mon-engagement", "mon-engagement"),
                new ElementMenu("03", "Campagne d'évaluation", "bi-clipboard-check", "/auto-evaluation", "auto-evaluation"),
                new ElementMenu("15", "Notifications", "bi-envelope", alertes.encode().build().toUriString(), "alertes"));
    }

    /**
     * Prototype : Campagne d'evaluation, Collaborateurs, Matrice 9-Box, Talent Passport,
     * Alertes, Notifications. Equipe, 9-Box, evaluations manquantes et alertes sont des
     * sections de la Vue manager.
     */
    private static List<ElementMenu> manager(ProfilActif p, String t) {
        if (p.matricule() == null) {
            // Talent Passport et Notifications ne dependent pas du manager : actives tout de suite.
            String indication = "choisir un manager";
            return List.of(
                    ElementMenu.grisee("03", "Campagne d'évaluation", "bi-clipboard-check", indication),
                    ElementMenu.grisee("04", "Collaborateurs", "bi-person-lines-fill", indication),
                    ElementMenu.grisee("05", "Matrice 9-Box", "bi-grid-3x3", indication),
                    new ElementMenu("12", "Talent Passport", "bi-person-vcard", "/fiche-collaborateur", "fiche-collaborateur"),
                    ElementMenu.grisee("13", "Alertes", "bi-bell", indication),
                    new ElementMenu("15", "Notifications", "bi-envelope", lien("/notifications", t), "notifications"));
        }
        String vue = LiensPages.vueManager(p.matricule(), t);
        return List.of(
                new ElementMenu("03", "Campagne d'évaluation", "bi-clipboard-check", vue + "#evaluations", null),
                new ElementMenu("04", "Collaborateurs", "bi-person-lines-fill", vue + "#equipe", "vue-manager"),
                new ElementMenu("05", "Matrice 9-Box", "bi-grid-3x3", vue + "#neufbox", null),
                new ElementMenu("12", "Talent Passport", "bi-person-vcard", "/fiche-collaborateur", "fiche-collaborateur"),
                new ElementMenu("13", "Alertes", "bi-bell", vue + "#alertes", null),
                new ElementMenu("15", "Notifications", "bi-envelope", lien("/notifications", t), "notifications"));
    }

    /**
     * Prototype : Tableau de bord DG, Comite Talent, Postes critiques & Succession,
     * Historique. Le tableau de bord DG est fusionne dans le tableau de bord RH : pas
     * d'entree. Pas encore de trace des decisions : Historique est une page en cours
     * de developpement, qui renvoie au journal des imports.
     */
    private static List<ElementMenu> comite(String t) {
        return List.of(
                new ElementMenu("11", "Comité Talent", "bi-person-badge", lien("/comite-talent", t), "comite-talent"),
                new ElementMenu("10", "Postes critiques & Succession", "bi-exclamation-triangle", lien("/postes-critiques", t),
                        "postes-critiques"),
                new ElementMenu("14", "Historique", "bi-clock-history", "/historique", "historique"));
    }

    private static String lien(String chemin, String trimestre) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath(chemin);
        if (trimestre != null) {
            lien.queryParam("trimestre", trimestre);
        }
        return lien.encode().build().toUriString();
    }
}
