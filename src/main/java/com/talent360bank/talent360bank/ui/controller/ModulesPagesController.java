package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.CompetenceSyntheseService;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.MatriceNeufBox;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Criteres;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Tri;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg;
import com.talent360bank.talent360bank.ui.service.ListeCollaborateursViewService;
import com.talent360bank.talent360bank.ui.service.NotificationsViewService;
import com.talent360bank.talent360bank.ui.service.OptionsFiltresService;
import com.talent360bank.talent360bank.ui.service.SuiviCampagneViewService;
import com.talent360bank.talent360bank.ui.service.TableauDeBordDgViewService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ecrans des modules de Jas : Collaborateurs, Dashboard DG, Competences,
 * Notifications et Campagne. Chacun accepte {@code ?trimestre=AAAA-N} (404 si
 * inconnu ; sinon le plus recent qui a des scores) et pose "trimestre" et
 * "trimestres" (voir {@link TrimestreCourantService}). Sans trimestre, le
 * template affiche l'etat vide avec le lien vers l'import.
 *
 * <p>Aucun calcul ici : chaque ecran appelle directement son service de vue
 * (les memes que l'API JSON) ; seuls les filtres sont lus et les liens de
 * pagination et de tri construits.
 */
@Controller
public class ModulesPagesController {

    private final TrimestreCourantService trimestreCourant;
    private final ListeCollaborateursViewService listeCollaborateurs;
    private final TableauDeBordDgViewService tableauDeBordDg;
    private final CompetenceSyntheseService competences;
    private final NotificationsViewService notifications;
    private final SuiviCampagneViewService campagne;
    private final OptionsFiltresService options;

    public ModulesPagesController(TrimestreCourantService trimestreCourant,
                                  ListeCollaborateursViewService listeCollaborateurs,
                                  TableauDeBordDgViewService tableauDeBordDg, CompetenceSyntheseService competences,
                                  NotificationsViewService notifications, SuiviCampagneViewService campagne,
                                  OptionsFiltresService options) {
        this.trimestreCourant = trimestreCourant;
        this.listeCollaborateurs = listeCollaborateurs;
        this.tableauDeBordDg = tableauDeBordDg;
        this.competences = competences;
        this.notifications = notifications;
        this.campagne = campagne;
        this.options = options;
    }

    /** Un lien de pagination. */
    public record PageLien(int numero, String url, boolean courante) {
    }

    // --- Collaborateurs --------------------------------------------------------------------------

    @GetMapping("/collaborateurs")
    public String collaborateurs(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                                 @RequestParam(required = false) String entite,
                                 @RequestParam(name = "case", required = false) String caseNeufBox,
                                 @RequestParam(required = false) String talent,
                                 @RequestParam(required = false) String vivier,
                                 @RequestParam(required = false) String readiness,
                                 @RequestParam(required = false) String vigilance,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false) String tri,
                                 @RequestParam(required = false) String ordre,
                                 @RequestParam(required = false) String page,
                                 Model model) {
        Trimestre choisi = selectionner(trimestre, model, "collaborateurs");
        Map<String, String> filtres = new LinkedHashMap<>();
        filtres.put("entite", vide(entite));
        filtres.put("case", vide(caseNeufBox));
        filtres.put("talent", vide(talent));
        filtres.put("vivier", vide(vivier));
        filtres.put("readiness", vide(readiness));
        filtres.put("vigilance", vide(vigilance));
        filtres.put("q", vide(q));
        model.addAttribute("filtres", filtres);
        if (choisi == null) {
            return "collaborateurs";
        }
        model.addAttribute("optionsEntites", options.entites());
        model.addAttribute("optionsCases", options.casesNeufBox());
        model.addAttribute("optionsViviers", OptionsFiltresService.viviers());
        model.addAttribute("optionsReadiness", OptionsFiltresService.readiness());
        model.addAttribute("optionsVigilance", OptionsFiltresService.vigilance());

        Tri colonne = Arrays.stream(Tri.values()).filter(t -> t.name().equalsIgnoreCase(vide(tri) == null ? "" : tri))
                .findFirst().orElse(Tri.NOM);
        boolean decroissant = ordre == null || ordre.isBlank()
                ? colonne != Tri.NOM && colonne != Tri.MATRICULE : "desc".equalsIgnoreCase(ordre);
        try {
            Criteres criteres = new Criteres(filtres.get("entite"), entier(filtres.get("case")),
                    booleen(filtres.get("talent")), majuscules(filtres.get("vivier")),
                    majuscules(filtres.get("readiness")), majuscules(filtres.get("vigilance")), filtres.get("q"),
                    colonne, decroissant, Math.max(1, entier(vide(page)) == null ? 1 : entier(page)),
                    Criteres.TAILLE_PAR_DEFAUT);
            ListeCollaborateurs vue = listeCollaborateurs.construire(choisi.getAnnee(), choisi.getNumero(), criteres);
            model.addAttribute("vue", vue);
            model.addAttribute("pages", pages(trimestre(choisi), filtres, colonne, decroissant, vue));
            model.addAttribute("tris", tris(trimestre(choisi), filtres, colonne, decroissant));
        } catch (IllegalArgumentException | RessourceIntrouvableException e) {
            model.addAttribute("erreur", e.getMessage());
        }
        return "collaborateurs";
    }

    /** Liens vers chaque page, filtres et tri conserves. */
    private static List<PageLien> pages(String trimestre, Map<String, String> filtres, Tri tri, boolean decroissant,
                                        ListeCollaborateurs vue) {
        List<PageLien> pages = new ArrayList<>();
        for (int numero = 1; numero <= vue.nbPages(); numero++) {
            pages.add(new PageLien(numero, url(trimestre, filtres, tri, decroissant, numero),
                    numero == vue.criteres().page()));
        }
        return pages;
    }

    /** Pour chaque colonne triable, le lien qui trie dessus (inverse l'ordre si elle l'est deja). */
    private static Map<String, String> tris(String trimestre, Map<String, String> filtres, Tri courant,
                                            boolean decroissant) {
        Map<String, String> liens = new LinkedHashMap<>();
        for (Tri tri : Tri.values()) {
            boolean ordreParDefaut = tri != Tri.NOM && tri != Tri.MATRICULE;
            liens.put(tri.name(), url(trimestre, filtres, tri, tri == courant ? !decroissant : ordreParDefaut, 1));
        }
        return liens;
    }

    private static String url(String trimestre, Map<String, String> filtres, Tri tri, boolean decroissant, int page) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/collaborateurs")
                .queryParam(TrimestreCourantService.PARAMETRE, trimestre);
        filtres.forEach((nom, valeur) -> {
            if (valeur != null) {
                url.queryParam(nom, valeur);
            }
        });
        return url.queryParam("tri", tri.name()).queryParam("ordre", decroissant ? "desc" : "asc")
                .queryParam("page", page).encode().build().toUriString();
    }

    // --- Dashboard DG ---------------------------------------------------------------------------------

    @GetMapping("/dashboard-dg")
    public String dashboardDg(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                              Model model) {
        Trimestre choisi = selectionner(trimestre, model, "dashboard-dg");
        if (choisi != null) {
            TableauDeBordDg vue = tableauDeBordDg.construire(choisi, TableauDeBordDg.TOP_TALENTS_PAR_DEFAUT);
            model.addAttribute("vue", vue);
            model.addAttribute("matrice", MatriceNeufBox.depuis(vue.neufBox(), 0, trimestre(choisi)));
        }
        return "dashboard-dg";
    }

    // --- Competences ------------------------------------------------------------------------------------

    @GetMapping("/competences")
    public String competences(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                              @RequestParam(required = false) String entite,
                              @RequestParam(required = false) String vivier,
                              @RequestParam(required = false) String poste,
                              @RequestParam(required = false) String ordre,
                              Model model) {
        Trimestre choisi = selectionner(trimestre, model, "competences");
        Map<String, String> filtres = new LinkedHashMap<>();
        filtres.put("entite", vide(entite));
        filtres.put("vivier", majuscules(vivier));
        filtres.put("poste", majuscules(poste));
        filtres.put("ordre", "asc".equalsIgnoreCase(ordre) ? "asc" : "desc");
        model.addAttribute("filtres", filtres);
        if (choisi == null) {
            return "competences";
        }
        model.addAttribute("optionsEntites", options.entites());
        model.addAttribute("optionsViviers", OptionsFiltresService.viviers());
        model.addAttribute("optionsPostes", options.postesCritiques());
        try {
            model.addAttribute("vue", competences.synthese(choisi, new SyntheseCompetences.Criteres(
                    filtres.get("entite"), filtres.get("vivier"), filtres.get("poste"),
                    "asc".equals(filtres.get("ordre")), SyntheseCompetences.TOP_PAR_DEFAUT)));
        } catch (IllegalArgumentException | RessourceIntrouvableException | DonneesIncompletesException e) {
            model.addAttribute("erreur", e.getMessage());
        }
        return "competences";
    }

    // --- Notifications ------------------------------------------------------------------------------------

    @GetMapping("/notifications")
    public String notifications(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                                Model model) {
        Trimestre choisi = selectionner(trimestre, model, "notifications");
        if (choisi != null) {
            model.addAttribute("vue", notifications.notifications(choisi));
        }
        return "notifications";
    }

    // --- Campagne ---------------------------------------------------------------------------------------------

    @GetMapping("/campagne")
    public String campagne(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                           @RequestParam(required = false) String entite, Model model) {
        Trimestre choisi = selectionner(trimestre, model, "campagne");
        if (choisi != null) {
            try {
                model.addAttribute("vue", campagne.construire(choisi.getAnnee(), choisi.getNumero(), vide(entite)));
            } catch (RessourceIntrouvableException e) {
                model.addAttribute("erreur", e.getMessage());
            }
        }
        return "campagne";
    }

    // --- outils ---------------------------------------------------------------------------------------------------

    private Trimestre selectionner(String trimestre, Model model, String page) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("activePage", page);
        return selection.trimestre();
    }

    private static String trimestre(Trimestre trimestre) {
        return TrimestreCourantService.valeur(trimestre);
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static String majuscules(String valeur) {
        String texte = vide(valeur);
        return texte == null ? null : texte.toUpperCase(Locale.ROOT);
    }

    /** Un nombre ; un texte qui n'en est pas un est refuse (400 cote API, message sur l'ecran). */
    private static Integer entier(String valeur) {
        if (valeur == null) {
            return null;
        }
        try {
            return Integer.valueOf(valeur.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nombre attendu : " + valeur);
        }
    }

    private static Boolean booleen(String valeur) {
        return valeur == null ? null : Boolean.valueOf(valeur.trim());
    }
}
