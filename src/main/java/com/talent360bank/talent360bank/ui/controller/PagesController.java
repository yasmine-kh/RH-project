package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.VivierSyntheseService;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.MatriceNeufBox;
import com.talent360bank.talent360bank.ui.service.LiensPages;
import com.talent360bank.talent360bank.ui.service.ComiteTalentViewService;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurPageViewService;
import com.talent360bank.talent360bank.ui.service.NineBoxViewService;
import com.talent360bank.talent360bank.ui.service.PosteCritiqueViewService;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurViewService;
import com.talent360bank.talent360bank.ui.service.ReponsesQuestionnaireViewService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import com.talent360bank.talent360bank.ui.service.VivierService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ecrans 9-Box, Viviers, Postes critiques, Comite Talent, Fiche collaborateur
 * et Parametres. Le tableau de bord et les alertes ont leur propre controleur
 * (DashboardController, AlertesController).
 *
 * <p>Les ecrans par trimestre acceptent {@code ?trimestre=AAAA-N} (404 si
 * inconnu ; sinon le plus recent qui a des scores) et posent les attributs
 * "trimestre" et "trimestres" (voir {@link TrimestreCourantService}). La fiche
 * collaborateur n'a pas encore ce selecteur : elle montre toujours le trimestre
 * le plus recent (voir {@link FicheCollaborateurPageViewService}).
 */
@Controller
public class PagesController {

    private final NineBoxViewService nineBoxViewService;
    private final VivierService vivierService;
    private final ComiteTalentViewService comiteTalentViewService;
    private final PosteCritiqueViewService posteCritiqueViewService;
    private final TrimestreCourantService trimestreCourant;
    private final FicheCollaborateurPageViewService ficheCollaborateurPageViewService;
    private final VivierSyntheseService vivierSyntheseService;
    private final ReponsesQuestionnaireViewService reponsesQuestionnaire;

    public PagesController(NineBoxViewService nineBoxViewService, VivierService vivierService,
                           ComiteTalentViewService comiteTalentViewService,
                           PosteCritiqueViewService posteCritiqueViewService,
                           TrimestreCourantService trimestreCourant,
                           FicheCollaborateurPageViewService ficheCollaborateurPageViewService,
                           VivierSyntheseService vivierSyntheseService,
                           ReponsesQuestionnaireViewService reponsesQuestionnaire) {
        this.nineBoxViewService = nineBoxViewService;
        this.vivierService = vivierService;
        this.comiteTalentViewService = comiteTalentViewService;
        this.posteCritiqueViewService = posteCritiqueViewService;
        this.trimestreCourant = trimestreCourant;
        this.ficheCollaborateurPageViewService = ficheCollaborateurPageViewService;
        this.vivierSyntheseService = vivierSyntheseService;
        this.reponsesQuestionnaire = reponsesQuestionnaire;
    }

    /**
     * La 9-Box et le panneau de detail d'une case. Sans JavaScript, chaque case est un
     * lien vers {@code ?case=N#detail} ; {@code q} (nom, matricule, entite) et {@code tri}
     * (NOM, PERFORMANCE, POTENTIEL) filtrent et trient les listes du panneau, sans requete.
     *
     * @param caseNeufBox case ouverte, 1 a 9 ; Talent cle (9) par defaut ou si hors bornes
     */
    @GetMapping("/9box")
    public String neufBox(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          @RequestParam(name = "case", required = false) Integer caseNeufBox,
                          @RequestParam(required = false) String q,
                          @RequestParam(required = false) String tri,
                          Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        int selectionnee = caseNeufBox != null && caseNeufBox >= 1 && caseNeufBox <= 9 ? caseNeufBox : 9;
        MatriceNeufBox matrice = nineBoxViewService.buildMatrice(selection.trimestre(), selectionnee);
        String recherche = q == null || q.isBlank() ? null : q.trim();
        String ordre = tri == null ? "NOM" : tri.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("NOM", "PERFORMANCE", "POTENTIEL").contains(ordre)) {
            ordre = "NOM";
        }
        Map<Integer, List<MatriceNeufBox.Membre>> panneaux = new HashMap<>();
        for (MatriceNeufBox.CaseMatrice c : matrice.cases()) {
            panneaux.put(c.numero(), filtrerEtTrier(c.membres(), recherche, ordre));
        }
        model.addAttribute("matrice", matrice);
        model.addAttribute("panneaux", panneaux);
        model.addAttribute("recherche", recherche);
        model.addAttribute("tri", ordre);
        model.addAttribute("activePage", "9box");
        return "9box";
    }

    /** Filtre (nom, matricule ou entite, sans accents ni casse) puis trie les membres d'une case. */
    static List<MatriceNeufBox.Membre> filtrerEtTrier(List<MatriceNeufBox.Membre> membres, String recherche,
                                                      String tri) {
        String texte = recherche == null ? null : sansAccents(recherche);
        Comparator<MatriceNeufBox.Membre> parNom = Comparator.comparing(m -> sansAccents(m.nomComplet()));
        Comparator<MatriceNeufBox.Membre> ordre = switch (tri) {
            case "PERFORMANCE" -> Comparator.comparing(MatriceNeufBox.Membre::performance,
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(parNom);
            case "POTENTIEL" -> Comparator.comparing(MatriceNeufBox.Membre::potentiel,
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(parNom);
            default -> parNom;
        };
        return membres.stream()
                .filter(m -> texte == null || sansAccents(m.nomComplet()).contains(texte)
                        || sansAccents(m.matricule()).contains(texte) || sansAccents(m.entite()).contains(texte))
                .sorted(ordre)
                .toList();
    }

    private static String sansAccents(String texte) {
        return texte == null ? "" : java.text.Normalizer.normalize(texte.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    @GetMapping("/viviers")
    public String viviers(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                          Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("rows", vivierService.buildRows(selection.trimestre()));
        // Une carte par vivier thematique puis le vivier de releve (VivierSyntheseService, Jas).
        List<SyntheseVivier> syntheses = List.of();
        if (selection.trimestre() != null) {
            try {
                syntheses = vivierSyntheseService.synthese(selection.trimestre());
            } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
                model.addAttribute("erreur", e.getMessage());
            }
        }
        model.addAttribute("syntheses", syntheses);
        model.addAttribute("activePage", "viviers");
        return "viviers";
    }

    @GetMapping("/postes-critiques")
    public String postesCritiques(
            @RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre, Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("rows", posteCritiqueViewService.buildRows(selection.trimestre()));
        model.addAttribute("activePage", "postes-critiques");
        return "postes-critiques";
    }

    @GetMapping("/comite-talent")
    public String comiteTalent(@RequestParam(required = false) String trimestre,
                               @RequestParam(required = false) String statut,
                               Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("vue", comiteTalentViewService.build(selection, statut));
        model.addAttribute("activePage", "comite-talent");
        return "comite-talent";
    }

    /**
     * Le Talent Passport. {@code questionnaire} (AAAA-N) choisit le trimestre de la section
     * "Reponses au questionnaire" ; par defaut, celui de la fiche. L'engagement n'y est pas
     * affiche : la ligne "pas de questionnaire d'engagement" est ecartee des donnees manquantes.
     */
    @GetMapping("/fiche-collaborateur")
    public String ficheCollaborateur(@RequestParam(required = false) String matricule,
                                     @RequestParam(required = false) String trimestre,
                                     @RequestParam(required = false) String questionnaire, Model model) {
        if (matricule != null && !matricule.isBlank()) {
            try {
                // ?trimestre=AAAA-N (liens des autres ecrans) : ce trimestre ; sinon le plus recent.
                model.addAttribute("fiche", trimestre == null || trimestre.isBlank()
                        ? ficheCollaborateurPageViewService.construire(matricule.trim())
                        : ficheCollaborateurPageViewService.construire(matricule.trim(),
                        trimestreCourant.selectionner(trimestre).trimestre()));
            } catch (RessourceIntrouvableException e) {
                model.addAttribute("erreur", e.getMessage());
            }
        }
        model.addAttribute("matricule", matricule);
        model.addAttribute("profilCible", matricule);
        if (model.getAttribute("fiche") instanceof FicheCollaborateur fiche) {
            // Liens de la fiche (vue manager, vue entite) pour le trimestre de la fiche.
            model.addAttribute("trimestreFiche", fiche.trimestre().annee() + "-" + fiche.trimestre().numero());
            model.addAttribute("cheminEntite", fiche.identite().entite() == null ? List.of()
                    : LiensPages.chemin(fiche.identite().entite().code(), fiche.identite().entite().chemin()));
            model.addAttribute("donneesManquantes", fiche.donneesManquantes().stream()
                    .filter(m -> !FicheCollaborateurViewService.MANQUE_ENGAGEMENT.equals(m)).toList());
            // Reponses au questionnaire : trimestre choisi dans la section (404 si inconnu), sinon celui de la fiche.
            TrimestreCourantService.Selection choix = trimestreCourant.selectionner(
                    questionnaire == null || questionnaire.isBlank()
                            ? fiche.trimestre().annee() + "-" + fiche.trimestre().numero() : questionnaire);
            model.addAttribute("questionnaire", reponsesQuestionnaire.construire(fiche.identite().matricule(),
                    choix.trimestre()));
            model.addAttribute("trimestresQuestionnaire", choix.trimestres());
        }
        model.addAttribute("activePage", "fiche-collaborateur");
        return "fiche-collaborateur";
    }

    /**
     * Reglages du moteur du trimestre choisi. La page lit et ecrit elle-meme
     * via l'API GET/PUT /api/trimestres/{annee}/{numero}/parametre (voir
     * docs/requetes-ecriture.md) : rien n'est lu ni calcule ici.
     */
    @GetMapping("/parametres")
    public String parametres(@RequestParam(name = TrimestreCourantService.PARAMETRE, required = false) String trimestre,
                             Model model) {
        TrimestreCourantService.Selection selection = trimestreCourant.selectionner(trimestre);
        selection.exposer(model);
        model.addAttribute("enTeteEcriture", ProtectionRequetesFilter.EN_TETE_ECRITURE);
        model.addAttribute("activePage", "parametres");
        return "parametres";
    }
}