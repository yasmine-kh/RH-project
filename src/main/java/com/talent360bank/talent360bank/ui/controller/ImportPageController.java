package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.ui.model.ImportPageView;
import com.talent360bank.talent360bank.ui.model.ImportPageView.Formulaire;
import com.talent360bank.talent360bank.ui.service.ImportPageService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import du classeur depuis le navigateur : formulaire, bilan, journal. Voir
 * docs/import-donnees.md, "Importer depuis l'application".
 *
 * <p>Formulaire HTML classique (multipart, jeton CSRF ajoute par th:action),
 * pas d'appel fetch. Le bilan s'affiche sur la meme page que le formulaire.
 *
 * <p>Pour ajouter l'import d'un dossier de fichiers : un second formulaire et
 * un second POST ici (par exemple /import/dossier), qui rendent la meme vue
 * avec un {@link ImportPageView.Rapport} par fichier.
 */
@Controller
public class ImportPageController {

    /** Parametre de GET /import apres un envoi refuse par la limite de taille ({@link TailleImportAdvice}). */
    static final String PARAMETRE_ERREUR = "erreur";
    static final String ERREUR_TAILLE = "taille";

    private final ImportPageService importPageService;
    private final TrimestreCourantService trimestreCourant;

    public ImportPageController(ImportPageService importPageService, TrimestreCourantService trimestreCourant) {
        this.importPageService = importPageService;
        this.trimestreCourant = trimestreCourant;
    }

    @GetMapping("/import")
    public String page(@RequestParam(name = PARAMETRE_ERREUR, required = false) String erreur, Model model) {
        return afficher(ERREUR_TAILLE.equals(erreur)
                ? importPageService.pageAvecErreur(importPageService.messageTailleMax())
                : importPageService.page(), model);
    }

    /**
     * Meme traitement que POST /api/imports (CampagneService), avec les memes
     * champs : fichier, annee, numero, simulation, calcul ; plus la date de
     * reference du trimestre. Une case decochee n'est pas envoyee par le
     * navigateur : absente = false.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String importer(@RequestParam(name = "fichier", required = false) MultipartFile fichier,
                           @RequestParam(defaultValue = "") String annee,
                           @RequestParam(defaultValue = "") String numero,
                           @RequestParam(defaultValue = "false") boolean simulation,
                           @RequestParam(defaultValue = "false") boolean calcul,
                           @RequestParam(defaultValue = "") String dateReference,
                           Model model) {
        return afficher(importPageService.importer(fichier,
                new Formulaire(annee, numero, simulation, calcul, dateReference)), model);
    }

    /**
     * Reponses brutes au questionnaire d'engagement (fichier de reponses du formulaire),
     * pour un trimestre deja importe : voir ImportQuestionnaireService. Le bilan s'affiche
     * au-dessus du formulaire, comme celui du classeur.
     */
    @PostMapping(value = "/import/questionnaire", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String importerQuestionnaire(@RequestParam(name = "fichier", required = false) MultipartFile fichier,
                                        @RequestParam(defaultValue = "") String trimestre, Model model) {
        ImportPageService.EnvoiQuestionnaire envoi = importPageService.importerQuestionnaire(fichier, trimestre);
        model.addAttribute("rapportQuestionnaire", envoi.rapport());
        model.addAttribute("erreurQuestionnaire", envoi.erreur());
        model.addAttribute("trimestreQuestionnaire", trimestre);
        return afficher(importPageService.page(), model);
    }

    private String afficher(ImportPageView vue, Model model) {
        model.addAttribute("trimestresQuestionnaire", trimestreCourant.lister());
        model.addAttribute("vue", vue);
        model.addAttribute("activePage", "import");
        return "import";
    }
}
