package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synthese d'un trimestre pour le tableau de bord : un seul appel, des
 * chiffres tous produits par le moteur (talents, hauts potentiels, vigilance,
 * couverture des postes critiques, repartition 9-box).
 *
 * <p>Ne calcule rien lui-meme : il assemble ce que rendent {@link TalentService},
 * {@link VigilanceService} et {@link PosteCritiqueService}, et compte les cases
 * 9-box deja posees par {@link NeufBoxService}. Lecture seule.
 *
 * <p>Nomme TableauDeBordService et non DashboardService : ce nom est deja
 * celui du service d'affichage de l'ecran d'accueil (ui.service), et deux
 * beans du meme nom empecheraient l'application de demarrer.
 */
@Service
public class TableauDeBordService {

    private final TalentService talentService;
    private final VigilanceService vigilanceService;
    private final PosteCritiqueService posteCritiqueService;
    private final ScoreRepository scoreRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;

    public TableauDeBordService(TalentService talentService,
                                VigilanceService vigilanceService,
                                PosteCritiqueService posteCritiqueService,
                                ScoreRepository scoreRepository,
                                Matrice9BoxRepository matrice9BoxRepository) {
        this.talentService = talentService;
        this.vigilanceService = vigilanceService;
        this.posteCritiqueService = posteCritiqueService;
        this.scoreRepository = scoreRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
    }

    /**
     * Chiffres cles du trimestre.
     *
     * @throws com.talent360bank.talent360bank.exception.RessourceIntrouvableException
     *         si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public SyntheseTableauDeBord synthese(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        // Un seul passage sur les scores : le vivier de releve porte deja les
        // deux statuts de chacun de ses membres.
        List<MembreVivierReleve> vivier = talentService.getVivierReleve(trimestre);
        int nbTalents = (int) vivier.stream().filter(MembreVivierReleve::talent).count();
        int nbHautsPotentiels = (int) vivier.stream().filter(MembreVivierReleve::hautPotentiel).count();

        Map<NiveauVigilance, Integer> vigilance = new EnumMap<>(NiveauVigilance.class);
        for (NiveauVigilance niveau : NiveauVigilance.values()) {
            vigilance.put(niveau, 0);
        }
        for (ResultatVigilance resultat : vigilanceService.evaluerTrimestre(trimestre)) {
            vigilance.merge(resultat.niveau(), 1, Integer::sum);
        }

        List<CouverturePoste> couvertures = posteCritiqueService.listerPostesCritiques(trimestre);
        List<CouverturePoste> alertes = couvertures.stream().filter(CouverturePoste::estEnAlerte).toList();

        Map<String, Integer> repartition = new LinkedHashMap<>();
        matrice9BoxRepository.findAll().stream()
                .sorted(Comparator.comparing(Matrice9Box::getNiveauPerformance, Comparator.reverseOrder())
                        .thenComparing(Matrice9Box::getNiveauPotentiel, Comparator.reverseOrder()))
                .forEach(case9Box -> repartition.put(case9Box.getCategorie(), 0));
        int nonPlaces = 0;
        for (Score score : scoreRepository.findByTrimestreAvecEmploye(trimestre)) {
            if (score.getPositionBox() == null) {
                nonPlaces++;
            } else {
                repartition.merge(score.getPositionBox(), 1, Integer::sum);
            }
        }

        return new SyntheseTableauDeBord(nbTalents, nbHautsPotentiels,
                Collections.unmodifiableMap(vigilance),
                couvertures.size(), posteCritiqueService.tauxCouverture(couvertures), alertes,
                Collections.unmodifiableMap(repartition), nonPlaces);
    }
}
