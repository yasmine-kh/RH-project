package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.Choix;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Liens vers les pages (jamais vers l'API), pour un trimestre : fiche d'un
 * collaborateur, vue d'un manager, vue d'une entite. Le code d'une entite
 * passe en parametre de requete : il contient des "/".
 */
public final class LiensPages {

    private LiensPages() {
    }

    public static String fiche(String matricule, String trimestre) {
        return avecTrimestre(UriComponentsBuilder.fromPath("/fiche-collaborateur").queryParam("matricule", matricule),
                trimestre);
    }

    public static String vueManager(String matricule, String trimestre) {
        return avecTrimestre(UriComponentsBuilder.fromPath("/managers/{matricule}"), trimestre, matricule);
    }

    public static String vueEntite(String code, String trimestre) {
        return avecTrimestre(UriComponentsBuilder.fromPath("/entites").queryParam("code", code), trimestre);
    }

    /**
     * Chaque niveau d'une entite, de la direction a elle-meme : le code d'un
     * ancetre est un prefixe du code (segments separes par "/", voir Entite.code).
     *
     * @param libelles libelles de la direction jusqu'a l'entite (EntiteFiche.chemin)
     */
    public static List<Choix> chemin(String code, List<String> libelles) {
        List<Choix> niveaux = new ArrayList<>();
        if (code == null || libelles == null) {
            return niveaux;
        }
        String[] segments = code.split("/");
        int decalage = segments.length - libelles.size();
        StringBuilder prefixe = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            prefixe.append(i == 0 ? "" : "/").append(segments[i]);
            if (i >= decalage) {
                niveaux.add(new Choix(prefixe.toString(), libelles.get(i - decalage)));
            }
        }
        return List.copyOf(niveaux);
    }

    private static String avecTrimestre(UriComponentsBuilder lien, String trimestre, Object... variables) {
        if (trimestre != null) {
            lien.queryParam("trimestre", trimestre);
        }
        return lien.encode().buildAndExpand(variables).toUriString();
    }
}
