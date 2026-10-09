package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.DecisionsComiteService;
import com.talent360bank.talent360bank.service.DecisionsComiteService.DecisionEnregistree;
import com.talent360bank.talent360bank.service.DecisionsComiteService.DecisionRefuseeException;
import com.talent360bank.talent360bank.service.enums.DecisionSuccession;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Saisie des decisions du Comite Talent depuis l'ecran /comite-talent : formulaires HTML en POST
 * (jeton CSRF ajoute par th:action), sans JavaScript. Apres l'enregistrement, retour sur la meme
 * section de la page avec un message.
 *
 * <p>"Ne pas retenir" demande une confirmation : sans {@code confirme=oui}, rien n'est enregistre et
 * la page revient avec la ligne a confirmer ({@code ?confirmer=} ou {@code ?confirmerSuccession=}).
 */
@Controller
public class ComiteDecisionsController {

    static final String SUCCES = "succesDecision";
    static final String ERREUR = "erreurDecision";
    static final String COMMENTAIRE = "commentaireDecision";
    private static final String CONFIRME = "oui";

    private final DecisionsComiteService decisionsComiteService;
    private final TrimestreCourantService trimestreCourant;

    public ComiteDecisionsController(DecisionsComiteService decisionsComiteService,
                                     TrimestreCourantService trimestreCourant) {
        this.decisionsComiteService = decisionsComiteService;
        this.trimestreCourant = trimestreCourant;
    }

    /** Valider (OUI), Reevaluer (EN_ATTENTE) ou Ne pas retenir (NON) un talent propose. */
    @PostMapping("/comite-talent/talents")
    public String deciderTalent(@RequestParam String trimestre, @RequestParam String matricule,
                                @RequestParam String decision,
                                @RequestParam(required = false) String commentaire,
                                @RequestParam(required = false) String confirme,
                                @RequestParam(required = false) String statut,
                                RedirectAttributes redirection) {
        Trimestre choisi = trimestre(trimestre);
        UriComponentsBuilder retour = retour(trimestre).queryParamIfPresent("statut",
                java.util.Optional.ofNullable(statut).filter(s -> !s.isBlank()));
        StatutValidationComite statutDecision = valeur(StatutValidationComite.class, decision);
        if (statutDecision == null) {
            redirection.addFlashAttribute(ERREUR, "Décision inconnue : rien n'a été enregistré.");
            return redirect(retour, "talents");
        }
        if (statutDecision == StatutValidationComite.NON && !CONFIRME.equals(confirme)) {
            redirection.addFlashAttribute(COMMENTAIRE, commentaire);
            return redirect(retour.queryParam("confirmer", matricule), "talent-" + matricule);
        }
        return enregistrer(redirection, retour, "talents",
                () -> decisionsComiteService.deciderTalent(choisi, matricule, statutDecision, commentaire));
    }

    /** Une des cinq decisions du prototype sur un successeur d'un poste critique. */
    @PostMapping("/comite-talent/successions")
    public String deciderSuccession(@RequestParam String trimestre, @RequestParam String poste,
                                    @RequestParam String matricule, @RequestParam String decision,
                                    @RequestParam(required = false) String commentaire,
                                    @RequestParam(required = false) String confirme,
                                    RedirectAttributes redirection) {
        Trimestre choisi = trimestre(trimestre);
        UriComponentsBuilder retour = retour(trimestre);
        DecisionSuccession choix = valeur(DecisionSuccession.class, decision);
        if (choix == null) {
            redirection.addFlashAttribute(ERREUR, "Décision inconnue : rien n'a été enregistré.");
            return redirect(retour, "successions");
        }
        if (choix == DecisionSuccession.NE_PAS_RETENIR && !CONFIRME.equals(confirme)) {
            redirection.addFlashAttribute(COMMENTAIRE, commentaire);
            return redirect(retour.queryParam("confirmerSuccession", poste + ":" + matricule),
                    "succession-" + poste + "-" + matricule);
        }
        return enregistrer(redirection, retour, "successions",
                () -> decisionsComiteService.deciderSuccession(choisi, poste, matricule, choix, commentaire));
    }

    private static String enregistrer(RedirectAttributes redirection, UriComponentsBuilder retour, String section,
                                      Supplier<DecisionEnregistree> decision) {
        try {
            DecisionEnregistree enregistree = decision.get();
            redirection.addFlashAttribute(SUCCES, "Décision enregistrée : " + enregistree.nomComplet() + " — "
                    + enregistree.libelleDecision() + ".");
        } catch (DecisionRefuseeException | RessourceIntrouvableException | DonneesIncompletesException e) {
            redirection.addFlashAttribute(ERREUR, e.getMessage());
        }
        return redirect(retour, section);
    }

    /** Le trimestre de la page ; 404 s'il est absent, mal ecrit ou inconnu. */
    private Trimestre trimestre(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trimestre absent");
        }
        return trimestreCourant.resoudre(valeur)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trimestre inconnu"));
    }

    private static UriComponentsBuilder retour(String trimestre) {
        return UriComponentsBuilder.fromPath("/comite-talent").queryParam("trimestre", trimestre);
    }

    private static String redirect(UriComponentsBuilder retour, String ancre) {
        return "redirect:" + retour.fragment(ancre).encode().build().toUriString();
    }

    private static <E extends Enum<E>> E valeur(Class<E> type, String code) {
        return code == null ? null : Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equals(code.trim()))
                .findFirst().orElse(null);
    }
}
