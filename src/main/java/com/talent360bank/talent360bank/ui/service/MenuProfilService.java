package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.ElementMenu;
import com.talent360bank.talent360bank.ui.model.ProfilActif;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Le menu de la sidebar pour le profil actif, comme le prototype
 * (docs/prototype/index.html, ROLES et NAV_DEF). Aucune requete : tout vient du
 * profil garde en session. Chaque entree mene a une page existante, filtree sur
 * la personne ou l'equipe quand la page le permet ; sinon a la page la plus
 * proche (voir docs/guide-developpeur.md).
 */
public final class MenuProfilService {

    private MenuProfilService() {
    }

    /**
     * @param trimestre "AAAA-N" du trimestre affiche (parametre de la requete), null pour le plus recent
     */
    public static List<ElementMenu> menu(ProfilActif profil, String trimestre) {
        return switch (profil == null ? "RH" : profil.type()) {
            case "COLLABORATEUR" -> collaborateur(profil, trimestre);
            case "MANAGER" -> manager(profil, trimestre);
            case "COMITE" -> comite(trimestre);
            default -> rh(trimestre);
        };
    }

    /** DRH / Talent Manager : tout le menu. */
    private static List<ElementMenu> rh(String t) {
        return List.of(
                new ElementMenu("Accueil", "bi-house", lien("/", t), "accueil"),
                new ElementMenu("Dashboard DG", "bi-speedometer2", lien("/dashboard-dg", t), "dashboard-dg"),
                new ElementMenu("Collaborateurs", "bi-person-lines-fill", lien("/collaborateurs", t), "collaborateurs"),
                new ElementMenu("9-Box", "bi-grid-3x3", lien("/9box", t), "9box"),
                new ElementMenu("Viviers", "bi-people", lien("/viviers", t), "viviers"),
                new ElementMenu("Postes critiques", "bi-exclamation-triangle", lien("/postes-critiques", t),
                        "postes-critiques"),
                new ElementMenu("Compétences", "bi-mortarboard", lien("/competences", t), "competences"),
                new ElementMenu("Comite Talent", "bi-person-badge", lien("/comite-talent", t), "comite-talent"),
                new ElementMenu("Fiche collaborateur", "bi-person-vcard", "/fiche-collaborateur", "fiche-collaborateur"),
                new ElementMenu("Alertes", "bi-bell", lien("/alertes", t), "alertes"),
                new ElementMenu("Notifications", "bi-envelope", lien("/notifications", t), "notifications"),
                new ElementMenu("Campagne", "bi-clipboard-check", lien("/campagne", t), "campagne"),
                new ElementMenu("Import", "bi-upload", "/import", "import"),
                new ElementMenu("Parametres", "bi-gear", "/parametres", "parametres"));
    }

    /**
     * Prototype : Mon profil, Mon engagement, Campagne d'evaluation, Notifications.
     * Mon engagement = le bloc engagement de sa fiche ; Campagne = l'avancement de son
     * entite ; Notifications = ses alertes.
     */
    private static List<ElementMenu> collaborateur(ProfilActif p, String t) {
        String fiche = LiensPages.fiche(p.matricule(), t);
        UriComponentsBuilder campagne = UriComponentsBuilder.fromPath("/campagne");
        if (t != null) {
            campagne.queryParam("trimestre", t);
        }
        if (p.entiteCode() != null) {
            campagne.queryParam("entite", p.entiteCode());
        }
        UriComponentsBuilder alertes = UriComponentsBuilder.fromPath("/alertes");
        if (t != null) {
            alertes.queryParam("trimestre", t);
        }
        alertes.queryParam("q", p.matricule());
        return List.of(
                new ElementMenu("Mon profil", "bi-person-vcard", fiche, "fiche-collaborateur"),
                new ElementMenu("Mon engagement", "bi-heart", fiche + "#engagement", null),
                new ElementMenu("Campagne d'évaluation", "bi-clipboard-check", campagne.encode().build().toUriString(),
                        "campagne"),
                new ElementMenu("Notifications", "bi-envelope", alertes.encode().build().toUriString(), "alertes"));
    }

    /**
     * Prototype : Campagne d'evaluation, Collaborateurs, Matrice 9-Box, Talent Passport,
     * Alertes, Notifications. Equipe, 9-Box, evaluations manquantes et alertes sont des
     * sections de la Vue manager.
     */
    private static List<ElementMenu> manager(ProfilActif p, String t) {
        String vue = LiensPages.vueManager(p.matricule(), t);
        return List.of(
                new ElementMenu("Campagne d'évaluation", "bi-clipboard-check", vue + "#evaluations", null),
                new ElementMenu("Collaborateurs", "bi-person-lines-fill", vue + "#equipe", "vue-manager"),
                new ElementMenu("Matrice 9-Box", "bi-grid-3x3", vue + "#neufbox", null),
                new ElementMenu("Talent Passport", "bi-person-vcard", "/fiche-collaborateur", "fiche-collaborateur"),
                new ElementMenu("Alertes", "bi-bell", vue + "#alertes", null),
                new ElementMenu("Notifications", "bi-envelope", lien("/notifications", t), "notifications"));
    }

    /**
     * Prototype : Tableau de bord DG, Comite Talent, Postes critiques & Succession,
     * Historique. Pas de trace des decisions : Historique mene au journal des imports.
     */
    private static List<ElementMenu> comite(String t) {
        return List.of(
                new ElementMenu("Tableau de bord DG", "bi-speedometer2", lien("/dashboard-dg", t), "dashboard-dg"),
                new ElementMenu("Comité Talent", "bi-person-badge", lien("/comite-talent", t), "comite-talent"),
                new ElementMenu("Postes critiques & Succession", "bi-exclamation-triangle", lien("/postes-critiques", t),
                        "postes-critiques"),
                new ElementMenu("Historique (imports)", "bi-clock-history", "/import", "import"));
    }

    private static String lien(String chemin, String trimestre) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath(chemin);
        if (trimestre != null) {
            lien.queryParam("trimestre", trimestre);
        }
        return lien.encode().build().toUriString();
    }
}
