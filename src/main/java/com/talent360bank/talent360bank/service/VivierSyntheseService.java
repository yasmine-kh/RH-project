package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier.GapFrequent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Une synthese par vivier : les cinq viviers thematiques puis le vivier de
 * releve (effectif, talents, hauts potentiels, Ready Now, moyennes, postes
 * critiques couverts, gaps identifies).
 *
 * <p>Ne calcule aucun statut : les membres et leurs statuts viennent de
 * {@link VivierThematiqueService} et {@link TalentService}, les successions
 * (matching, readiness, plus grand gap) de {@link PosteCritiqueService}. Ici,
 * seulement des comptes et des moyennes. Les methodes {@code resumer...}
 * travaillent sur des donnees deja chargees : le tableau de bord les appelle
 * avec ce qu'il a deja lu, sans requete de plus.
 *
 * <p>Lecture seule, un nombre fixe de requetes quelle que soit la population.
 */
@Service
public class VivierSyntheseService {

    /** Moyennes au centieme, comme les autres moyennes d'affichage. */
    private static final int PRECISION_MOYENNE = 2;

    private final VivierThematiqueService vivierThematiqueService;
    private final TalentService talentService;
    private final PosteCritiqueService posteCritiqueService;

    public VivierSyntheseService(VivierThematiqueService vivierThematiqueService, TalentService talentService,
                                 PosteCritiqueService posteCritiqueService) {
        this.vivierThematiqueService = vivierThematiqueService;
        this.talentService = talentService;
        this.posteCritiqueService = posteCritiqueService;
    }

    /**
     * Les cinq viviers thematiques, dans l'ordre de {@link VivierThematique},
     * puis le vivier de releve.
     *
     * @throws com.talent360bank.talent360bank.exception.RessourceIntrouvableException
     *         si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public List<SyntheseVivier> synthese(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        List<CouverturePoste> couvertures = posteCritiqueService.listerPostesCritiques(trimestre);
        List<SyntheseVivier> syntheses = new ArrayList<>(
                resumerThematiques(vivierThematiqueService.getViviersThematiques(trimestre), couvertures));
        syntheses.add(resumerReleve(talentService.getVivierReleve(trimestre), couvertures));
        return List.copyOf(syntheses);
    }

    /**
     * Les cinq viviers thematiques, chacun present meme vide, sans acces base.
     *
     * @param couvertures tous les postes critiques du trimestre avec leurs successeurs evalues
     */
    public List<SyntheseVivier> resumerThematiques(ResultatViviersThematiques viviers,
                                                   List<CouverturePoste> couvertures) {
        Objects.requireNonNull(viviers, "viviers");
        Objects.requireNonNull(couvertures, "couvertures");
        List<SyntheseVivier> syntheses = new ArrayList<>();
        for (VivierThematique vivier : VivierThematique.values()) {
            List<Membre> membres = viviers.membresDe(vivier).stream()
                    .map(membre -> new Membre(membre.score(), membre.talent(), membre.hautPotentiel()))
                    .toList();
            syntheses.add(resumer(vivier.getCode(), vivier.getLibelle(), false, membres, couvertures));
        }
        return List.copyOf(syntheses);
    }

    /** Le vivier de releve, sans acces base. */
    public SyntheseVivier resumerReleve(List<MembreVivierReleve> releve, List<CouverturePoste> couvertures) {
        Objects.requireNonNull(releve, "releve");
        Objects.requireNonNull(couvertures, "couvertures");
        List<Membre> membres = releve.stream()
                .map(membre -> new Membre(membre.score(), membre.talent(), membre.hautPotentiel()))
                .toList();
        return resumer(VivierReleveService.CODE_VIVIER_RELEVE, VivierReleveService.NOM_VIVIER_RELEVE, true,
                membres, couvertures);
    }

    private static SyntheseVivier resumer(String code, String libelle, boolean releve, List<Membre> membres,
                                          List<CouverturePoste> couvertures) {
        Set<String> ids = membres.stream()
                .map(membre -> membre.score().getCollaborateur().getIdCollaborateur())
                .collect(Collectors.toSet());

        List<ResultatMatching> successionsDesMembres = couvertures.stream()
                .flatMap(couverture -> couverture.successeurs().stream())
                .filter(successeur -> ids.contains(successeur.candidat().getIdCollaborateur()))
                .toList();

        int nbReadyNow = (int) successionsDesMembres.stream()
                .filter(successeur -> successeur.readiness() == NiveauReadiness.READY_NOW)
                .map(successeur -> successeur.candidat().getIdCollaborateur())
                .distinct()
                .count();
        int nbPostesCouverts = (int) couvertures.stream()
                .filter(couverture -> couverture.successeurs().stream()
                        .anyMatch(successeur -> ids.contains(successeur.candidat().getIdCollaborateur())))
                .count();

        return new SyntheseVivier(code, libelle, releve, membres.size(),
                (int) membres.stream().filter(Membre::talent).count(),
                (int) membres.stream().filter(Membre::hautPotentiel).count(),
                nbReadyNow,
                moyenne(membres, Score::getScorePerformance),
                moyenne(membres, Score::getScorePotentiel),
                nbPostesCouverts,
                gapsIdentifies(successionsDesMembres));
    }

    /** Plus grands gaps reels des successions, par competence, du plus frequent au moins frequent. */
    private static List<GapFrequent> gapsIdentifies(List<ResultatMatching> successions) {
        Map<String, PlusGrandGap> competences = new LinkedHashMap<>();
        Map<String, Integer> nombres = new LinkedHashMap<>();
        for (ResultatMatching succession : successions) {
            PlusGrandGap gap = succession.plusGrandGap();
            if (gap == null || !gap.aUnEcart()) {
                continue;
            }
            competences.putIfAbsent(gap.competenceId(), gap);
            nombres.merge(gap.competenceId(), 1, Integer::sum);
        }
        return nombres.entrySet().stream()
                .map(entree -> new GapFrequent(entree.getKey(), competences.get(entree.getKey()).competence(),
                        entree.getValue()))
                .sorted(Comparator.comparing(GapFrequent::nombre, Comparator.reverseOrder())
                        .thenComparing(GapFrequent::competence, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Moyenne au centieme des valeurs connues, null sans valeur. */
    private static BigDecimal moyenne(List<Membre> membres, Function<Score, BigDecimal> valeur) {
        List<BigDecimal> valeurs = membres.stream().map(membre -> valeur.apply(membre.score()))
                .filter(Objects::nonNull).toList();
        if (valeurs.isEmpty()) {
            return null;
        }
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valeurs.size()), PRECISION_MOYENNE, RoundingMode.HALF_UP);
    }

    /** Un membre, quel que soit son vivier : son score et ses deux statuts. */
    private record Membre(Score score, boolean talent, boolean hautPotentiel) {
    }
}
