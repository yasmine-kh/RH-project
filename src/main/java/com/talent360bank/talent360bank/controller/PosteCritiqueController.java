package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CouverturePosteResponse;
import com.talent360bank.talent360bank.controller.dto.SyntheseCouvertureResponse;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Postes critiques : couverture par les successeurs identifies et alertes. */
@RestController
@RequestMapping("/api/postes-critiques")
public class PosteCritiqueController {

    private final PosteCritiqueService posteCritiqueService;
    private final ChargeurRessources chargeur;

    public PosteCritiqueController(PosteCritiqueService posteCritiqueService, ChargeurRessources chargeur) {
        this.posteCritiqueService = posteCritiqueService;
        this.chargeur = chargeur;
    }

    /** Couverture de tous les postes critiques (08_POSTES_CRITIQUES). */
    @GetMapping
    public List<CouverturePosteResponse> postesCritiques(@RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return posteCritiqueService.listerPostesCritiques(trimestre).stream()
                .map(CouverturePosteResponse::de).toList();
    }

    /** Postes critiques en alerte, a traiter en priorite. */
    @GetMapping("/alertes")
    public List<CouverturePosteResponse> alertes(@RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return posteCritiqueService.detecterAlertes(trimestre).stream()
                .map(CouverturePosteResponse::de).toList();
    }

    /** Chiffres cles pour le tableau de bord, en un seul calcul. */
    @GetMapping("/synthese")
    public SyntheseCouvertureResponse synthese(@RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        List<CouverturePoste> couvertures = posteCritiqueService.listerPostesCritiques(trimestre);
        List<CouverturePosteResponse> alertes = couvertures.stream()
                .filter(CouverturePoste::estEnAlerte)
                .map(CouverturePosteResponse::de)
                .toList();
        return new SyntheseCouvertureResponse(couvertures.size(), alertes.size(),
                posteCritiqueService.tauxCouverture(couvertures), alertes);
    }

    /** Couverture d'un seul poste critique. */
    @GetMapping("/{posteId}")
    public CouverturePosteResponse posteCritique(@PathVariable String posteId,
                                                 @RequestParam int annee, @RequestParam int numero) {
        Trimestre trimestre = chargeur.exigerTrimestre(annee, numero);
        return CouverturePosteResponse.de(posteCritiqueService.evaluerCouverture(posteId, trimestre));
    }
}
