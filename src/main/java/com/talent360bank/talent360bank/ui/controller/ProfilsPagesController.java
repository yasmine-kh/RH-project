package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.ui.model.ProfilActif;
import com.talent360bank.talent360bank.ui.service.LiensPages;
import com.talent360bank.talent360bank.ui.service.OrganigrammeViewService;
import com.talent360bank.talent360bank.ui.service.ProfilsPagesService;
import com.talent360bank.talent360bank.ui.service.ProfilsService;
import jakarta.servlet.http.HttpSession;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;

/**
 * Pages des "profils" que le RH ouvre (ce ne sont pas des connexions : un seul
 * compte RH) : Vue manager, Vue entite, leurs pages de choix, et la
 * redirection du selecteur de profil de la sidebar.
 *
 * <p>Chaque page accepte {@code ?trimestre=AAAA-N}. Le code d'une entite passe
 * en parametre ({@code /entites?code=...}) : il contient des "/". Un matricule
 * ou un code inconnu donne la page 404 (ProfilsAdvice).
 */
@Controller
public class ProfilsPagesController {

    private final TrimestreCourantService trimestreCourant;
    private final ProfilsPagesService pages;
    private final ProfilsService profilsService;
    private final OrganigrammeViewService organigrammeService;

    public ProfilsPagesController(TrimestreCourantService trimestreCourant, ProfilsPagesService pages,
                                  ProfilsService profilsService, OrganigrammeViewService organigrammeService) {
        this.trimestreCourant = trimestreCourant;
        this.pages = pages;
        this.profilsService = profilsService;
        this.organigrammeService = organigrammeService;
    }

    /**
     * Le selecteur de profil. Le profil choisi est garde en session : seul le menu
     * de la sidebar change, toutes les pages restent accessibles.
     * <ul>
     *   <li>RH : profil RH, personne effacee ("Revenir a la vue RH"), vers l'accueil ;</li>
     *   <li>Comite : profil Comite, vers le Comite Talent ;</li>
     *   <li>Collaborateur / Manager sans personne : le profil s'applique tout de suite (menu
     *   grise jusqu'au choix), on reste sur la page ({@code retour}) ; la personne deja
     *   choisie pour ce profil est gardee ;</li>
     *   <li>Collaborateur / Manager avec une personne : ce profil et cette personne (nom lu une
     *   fois), vers sa fiche ou sa vue manager ; une personne inconnue ne change rien (la page
     *   dira qu'elle est introuvable).</li>
     * </ul>
     * Entite n'est plus un profil ; l'ancien lien {@code profil=ENTITE} ouvre la vue de
     * l'entite en vue RH.
     *
     * @param retour page courante (chemin relatif de l'application), pour rester dessus
     */
    @GetMapping("/profil")
    public String profil(@RequestParam(required = false) String profil, @RequestParam(required = false) String cible,
                         @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                         @RequestParam(required = false) String retour,
                         HttpSession session) {
        String choix = cible == null || cible.isBlank() ? null : cible.trim().split("\\s+")[0];
        String t = trimestre == null || trimestre.isBlank() ? null : trimestre.trim();
        String type = profil == null ? "RH" : profil.trim().toUpperCase(Locale.ROOT);
        Object enSession = session.getAttribute(ProfilActif.SESSION);
        ProfilActif actuel = enSession instanceof ProfilActif p ? p : ProfilActif.RH;
        if (choix != null && !type.equals(actuel.type()) && choix.equals(actuel.matricule())) {
            // Sans JavaScript, le champ garde la personne du profil precedent : on change seulement de profil.
            choix = null;
        }
        String cibleUrl = switch (type) {
            case "COLLABORATEUR", "MANAGER" -> {
                if (choix == null) {
                    if (!type.equals(actuel.type())) {
                        session.setAttribute(ProfilActif.SESSION, ProfilActif.sansPersonne(type));
                    }
                    yield pageRetour(retour);
                }
                profilsService.profil(type, choix).ifPresent(p -> session.setAttribute(ProfilActif.SESSION, p));
                yield "COLLABORATEUR".equals(type) ? LiensPages.fiche(choix, t) : LiensPages.vueManager(choix, t);
            }
            case "ENTITE" -> {
                session.setAttribute(ProfilActif.SESSION, ProfilActif.RH);
                yield choix == null ? avecTrimestre("/entites", t) : LiensPages.vueEntite(choix, t);
            }
            case "COMITE" -> {
                session.setAttribute(ProfilActif.SESSION, ProfilActif.COMITE);
                yield avecTrimestre("/comite-talent", t);
            }
            default -> {
                session.setAttribute(ProfilActif.SESSION, ProfilActif.RH);
                yield avecTrimestre("/", t);
            }
        };
        return "redirect:" + cibleUrl;
    }

    /** Une page de l'application seulement (chemin relatif), jamais une adresse externe ni /profil. */
    static String pageRetour(String retour) {
        if (retour == null || !retour.startsWith("/") || retour.startsWith("//") || retour.contains("\\")
                || retour.startsWith("/profil") || retour.startsWith("/api")) {
            return "/";
        }
        return retour;
    }

    @GetMapping("/managers")
    public String managers(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                           Model model) {
        Trimestre choisi = selectionner(trimestre, model, "managers");
        if (choisi != null) {
            model.addAttribute("managers", pages.managers(choisi));
        }
        return "managers";
    }

    @GetMapping("/managers/{matricule}")
    public String vueManager(@PathVariable String matricule,
                             @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                             @RequestParam(name = "case", required = false) Integer caseNeufBox,
                             Model model) {
        Trimestre choisi = selectionner(trimestre, model, "vue-manager");
        model.addAttribute("profilCible", matricule);
        // Clic sur une case de la 9-Box de l'equipe : le tableau de l'equipe ne montre que cette case.
        model.addAttribute("caseFiltre", caseNeufBox != null && caseNeufBox >= 1 && caseNeufBox <= 9 ? caseNeufBox : null);
        if (choisi != null) {
            model.addAttribute("page", pages.vueManager(matricule, choisi));
        }
        return "vue-manager";
    }

    /** Sans {@code code} : l'organigramme (cartes par direction) ; avec : la vue de cette entite. */
    @GetMapping("/entites")
    public String entites(@RequestParam(required = false) String code,
                          @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          Model model) {
        if (code == null || code.isBlank()) {
            Trimestre choisi = selectionner(trimestre, model, "entites");
            if (choisi != null) {
                model.addAttribute("organigramme", organigrammeService.organigramme(choisi));
            }
            return "entites";
        }
        Trimestre choisi = selectionner(trimestre, model, "vue-entite");
        model.addAttribute("profilCible", code.trim());
        if (choisi != null) {
            model.addAttribute("page", pages.vueEntite(code.trim(), choisi));
        }
        return "vue-entite";
    }

    private Trimestre selectionner(String trimestre, Model model, String page) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("activePage", page);
        return selection.trimestre();
    }

    private static String avecTrimestre(String chemin, String trimestre) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath(chemin);
        if (trimestre != null) {
            lien.queryParam(TrimestreCourantService.PARAMETRE, trimestre);
        }
        return lien.encode().build().toUriString();
    }
}
