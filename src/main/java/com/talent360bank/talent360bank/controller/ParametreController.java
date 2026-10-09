package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ModificationParametreResponse;
import com.talent360bank.talent360bank.controller.dto.ParametreForm;
import com.talent360bank.talent360bank.controller.dto.ParametreResponse;
import com.talent360bank.talent360bank.controller.dto.RecalculReglagesResponse;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Reglages du moteur de calcul, un jeu par trimestre.
 *
 * <p>C'est le seul controleur en ecriture sur la configuration : changer une
 * ponderation doit rester une operation de parametrage, jamais une livraison
 * de code.
 *
 * <p>Changer les reglages relance le calcul du trimestre (voir {@link #modifier}) :
 * scores, cases 9-box et vivier de releve enregistres ne restent jamais sur
 * d'anciens reglages, et la page 9-box, la fiche et la vue manager concordent.
 */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/parametre")
public class ParametreController {

    private static final Logger log = LoggerFactory.getLogger(ParametreController.class);

    private final ParametreRepository parametreRepository;
    private final ChargeurRessources chargeur;
    private final Validator validator;
    private final CalculTrimestreService calculTrimestreService;
    private final VerrouCalculTrimestre verrou;
    private final com.talent360bank.talent360bank.service.JournalService journalService;

    public ParametreController(ParametreRepository parametreRepository,
                               ChargeurRessources chargeur,
                               Validator validator,
                               CalculTrimestreService calculTrimestreService,
                               VerrouCalculTrimestre verrou,
                               com.talent360bank.talent360bank.service.JournalService journalService) {
        this.journalService = journalService;
        this.parametreRepository = parametreRepository;
        this.chargeur = chargeur;
        this.validator = validator;
        this.calculTrimestreService = calculTrimestreService;
        this.verrou = verrou;
    }

    /** Reglages du trimestre. */
    @GetMapping
    @Transactional(readOnly = true)
    public ParametreResponse lire(@PathVariable int annee, @PathVariable int numero) {
        return ParametreResponse.de(exiger(annee, numero));
    }

    /**
     * Cree les reglages du trimestre aux valeurs par defaut de la
     * specification. Permet a l'UI d'ouvrir un trimestre neuf sans que le RH
     * ait a saisir les quarante champs a la main.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ParametreResponse> creerParDefaut(@PathVariable int annee,
                                                            @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        if (parametreRepository.existsByTrimestre(trimestre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Des réglages existent déjà pour T" + numero + " " + annee);
        }
        Parametre cree = parametreRepository.save(Parametre.parDefaut(trimestre));
        journalService.parametres(trimestre, "Réglages par défaut créés pour T" + numero + " " + annee + ".",
                "/parametres?trimestre=" + annee + "-" + numero);
        return ResponseEntity.status(HttpStatus.CREATED).body(ParametreResponse.de(cree));
    }

    /**
     * Remplace les blocs de reglages du trimestre. Les blocs facultatifs
     * (voir {@link ParametreForm}) absents du corps gardent leur valeur.
     *
     * <p>Les blocs sont valides un a un par @Valid, mais les regles qui
     * croisent plusieurs blocs vivent sur Parametre : elles ne peuvent etre
     * verifiees qu'apres recopie, d'ou la validation explicite avant
     * enregistrement plutot qu'au flush.
     *
     * <p><strong>Recalcul.</strong> Une fois les reglages enregistres (la
     * sauvegarde a sa propre transaction, validee avant la suite), le calcul
     * complet du trimestre est relance : scores, placement 9-box, vivier de
     * releve. Dans cet ordre, jamais l'un sans l'autre :
     * <ul>
     *   <li>reglages invalides : 400, rien n'est enregistre ni recalcule ;</li>
     *   <li>recalcul en echec : les reglages restent enregistres et la reponse
     *   (200) le dit dans {@code recalcul.erreur}, jamais un 500 ;</li>
     *   <li>recalcul du trimestre deja en cours (double clic) : 409, rien n'est
     *   enregistre. Le verrou est pris avant l'enregistrement : sinon le second
     *   appel enregistrerait ses reglages pendant que le premier calcul tourne
     *   encore sur les anciens.</li>
     * </ul>
     *
     * <p>Pas de @Transactional ici : la sauvegarde doit etre validee avant que
     * le recalcul, qui a ses propres transactions, relise les reglages.
     */
    @PutMapping
    public ModificationParametreResponse modifier(@PathVariable int annee, @PathVariable int numero,
                                                  @Valid @RequestBody ParametreForm form) {
        Trimestre trimestre = exiger(annee, numero).getTrimestre();
        return verrou.executer(trimestre, () -> {
            Parametre parametre = exiger(annee, numero);
            form.appliquerA(parametre);

            Set<ConstraintViolation<Parametre>> violations = validator.validate(parametre);
            if (!violations.isEmpty()) {
                List<String> messages = violations.stream()
                        .map(violation -> violation.getPropertyPath() + " : " + violation.getMessage())
                        .sorted()
                        .toList();
                throw new ParametreInvalideException(messages);
            }

            parametreRepository.save(parametre);
            // save fusionne une copie dont le trimestre est un proxy LAZY, illisible
            // hors transaction : la reponse part des reglages valides, au meme etat.
            ParametreResponse enregistre = ParametreResponse.de(parametre);
            RecalculReglagesResponse recalcul = recalculer(trimestre);
            journalService.parametres(trimestre, "Réglages du moteur modifiés pour T" + numero + " " + annee
                            + (recalcul.recalcule() ? " ; trimestre recalculé (" + recalcul.nbCollaborateursScores()
                            + " score(s))." : " ; le recalcul a échoué."),
                    "/parametres?trimestre=" + annee + "-" + numero);
            return new ModificationParametreResponse(enregistre, recalcul);
        });
    }

    /**
     * Calcul complet du trimestre apres un changement de reglages. Un echec est
     * rendu dans le bilan, pas leve : les reglages, eux, sont enregistres.
     */
    private RecalculReglagesResponse recalculer(Trimestre trimestre) {
        long debut = System.nanoTime();
        try {
            return RecalculReglagesResponse.reussi(calculTrimestreService.calculer(trimestre), duree(debut));
        } catch (DonneesIncompletesException | RessourceIntrouvableException e) {
            log.warn("Recalcul T{} {} apres changement de reglages : echec ({})",
                    trimestre.getNumero(), trimestre.getAnnee(), e.getMessage());
            return RecalculReglagesResponse.echoue(messageEchec(trimestre, e.getMessage()), duree(debut));
        } catch (RuntimeException e) {
            log.error("Recalcul T{} {} apres changement de reglages : echec", trimestre.getNumero(),
                    trimestre.getAnnee(), e);
            return RecalculReglagesResponse.echoue("Réglages enregistrés, mais le recalcul du trimestre a échoué. "
                    + "Vérifiez les réglages et réessayez.", duree(debut));
        }
    }

    private static String messageEchec(Trimestre trimestre, String cause) {
        return "Réglages enregistrés, mais le recalcul du trimestre a échoué : " + cause
                + ". Corrigez les réglages puis enregistrez-les de nouveau pour relancer le calcul.";
    }

    private static long duree(long debut) {
        return (System.nanoTime() - debut) / 1_000_000;
    }

    private Parametre exiger(int annee, int numero) {
        return parametreRepository.findByNumeroEtAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun paramètre configuré pour T" + numero + " " + annee));
    }
}
