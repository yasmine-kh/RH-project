package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ParametreForm;
import com.talent360bank.talent360bank.controller.dto.ParametreResponse;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
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
 */
@RestController
@RequestMapping("/api/trimestres/{annee}/{numero}/parametre")
public class ParametreController {

    private final ParametreRepository parametreRepository;
    private final ChargeurRessources chargeur;
    private final Validator validator;

    public ParametreController(ParametreRepository parametreRepository,
                               ChargeurRessources chargeur,
                               Validator validator) {
        this.parametreRepository = parametreRepository;
        this.chargeur = chargeur;
        this.validator = validator;
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
                    "Des reglages existent deja pour T" + numero + " " + annee);
        }
        Parametre cree = parametreRepository.save(Parametre.parDefaut(trimestre));
        return ResponseEntity.status(HttpStatus.CREATED).body(ParametreResponse.de(cree));
    }

    /**
     * Remplace les dix blocs de reglages du trimestre.
     *
     * <p>Les blocs sont valides un a un par @Valid, mais les regles qui
     * croisent plusieurs blocs vivent sur Parametre : elles ne peuvent etre
     * verifiees qu'apres recopie, d'ou la validation explicite avant
     * enregistrement plutot qu'au flush.
     */
    @PutMapping
    @Transactional
    public ParametreResponse modifier(@PathVariable int annee, @PathVariable int numero,
                                      @Valid @RequestBody ParametreForm form) {
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

        return ParametreResponse.de(parametreRepository.save(parametre));
    }

    private Parametre exiger(int annee, int numero) {
        return parametreRepository.findByNumeroEtAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun parametre configure pour T" + numero + " " + annee));
    }
}
