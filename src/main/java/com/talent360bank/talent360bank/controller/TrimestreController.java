package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CalculTrimestreResponse;
import com.talent360bank.talent360bank.controller.dto.CreationTrimestreForm;
import com.talent360bank.talent360bank.controller.dto.CreationTrimestreResponse;
import com.talent360bank.talent360bank.controller.dto.ModificationTrimestreForm;
import com.talent360bank.talent360bank.controller.dto.TrimestreResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.ResultatCreationTrimestre;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/trimestres")
public class TrimestreController {

    private final TrimestreService trimestreService;
    private final CalculTrimestreService calculTrimestreService;
    private final TrimestreRepository trimestreRepository;
    private final ParametreRepository parametreRepository;
    private final ChargeurRessources chargeur;
    private final com.talent360bank.talent360bank.service.JournalService journalService;

    public TrimestreController(TrimestreService trimestreService,
                               CalculTrimestreService calculTrimestreService,
                               TrimestreRepository trimestreRepository,
                               ParametreRepository parametreRepository,
                               ChargeurRessources chargeur,
                               com.talent360bank.talent360bank.service.JournalService journalService) {
        this.journalService = journalService;
        this.trimestreService = trimestreService;
        this.calculTrimestreService = calculTrimestreService;
        this.trimestreRepository = trimestreRepository;
        this.parametreRepository = parametreRepository;
        this.chargeur = chargeur;
    }

    /** Trimestres connus, le plus recent en premier. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<TrimestreResponse> lister() {
        return trimestreRepository.findAllByOrderByAnneeDescNumeroDesc().stream()
                .map(trimestre -> TrimestreResponse.de(trimestre, parametreRepository.existsByTrimestre(trimestre)))
                .toList();
    }

    /**
     * Ouvre un trimestre et ses reglages par defaut. Rejouable : 201 si le
     * trimestre a ete cree, 200 s'il existait deja (ses reglages sont alors
     * crees s'ils manquaient, jamais remplaces).
     */
    @PostMapping
    public ResponseEntity<CreationTrimestreResponse> creer(@Valid @RequestBody CreationTrimestreForm form) {
        ResultatCreationTrimestre resultat = trimestreService.creerSiAbsent(form.annee(), form.numero(),
                form.dateReference());
        return ResponseEntity.status(resultat.trimestreCree() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(CreationTrimestreResponse.de(resultat));
    }

    /**
     * Change la date de reference du trimestre (date a laquelle l'anciennete
     * est mesuree). Rend le trimestre mis a jour.
     *
     * <p>Aucun recalcul, contrairement a un changement de reglages (PUT
     * .../parametre) : rien d'enregistre n'en depend. Scores, cases 9-box et
     * vivier de releve ne lisent pas l'anciennete ; le critere experience du
     * matching et l'anciennete des fiches sont calcules a chaque lecture, avec
     * la date du moment.
     */
    @PutMapping("/{annee}/{numero}")
    @Transactional
    public TrimestreResponse modifier(@PathVariable int annee, @PathVariable int numero,
                                      @Valid @RequestBody ModificationTrimestreForm form) {
        Trimestre trimestre = trimestreService.modifierDateReference(
                chargeur.exigerTrimestre(annee, numero), form.dateReference());
        journalService.parametres(trimestre, "Date de référence de T" + numero + " " + annee + " : "
                + (trimestre.getDateReference() == null ? "aucune"
                : trimestre.getDateReference().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                + ".", "/parametres?trimestre=" + annee + "-" + numero);
        return TrimestreResponse.de(trimestre, parametreRepository.existsByTrimestre(trimestre));
    }

    /**
     * Calcul complet du trimestre, a lancer apres un import : recalcul des
     * scores, placement 9-box, puis vivier de releve. Les trois endpoints
     * unitaires restent disponibles pour relancer une seule etape.
     */
    @PostMapping("/{annee}/{numero}/calcul")
    public CalculTrimestreResponse calculer(@PathVariable int annee, @PathVariable int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return CalculTrimestreResponse.de(calculTrimestreService.calculer(trimestre));
    }
}
