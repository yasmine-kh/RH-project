package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Viviers thematiques (10_TALENTS, colonne Vivier thematique) : chaque collaborateur
 * du trimestre est range dans le vivier de sa direction, talent ou non, avec
 * ses statuts de talent et de haut potentiel pour que l'ecran filtre.
 *
 * <p>Le rattachement direction / vivier vient de {@link VivierThematiqueSource}.
 * Un collaborateur dont la direction n'y figure pas est rendu a part, pas ecarte en
 * silence. Calcule a la demande, rien n'est ecrit dans AppartenanceVivier.
 */
@Service
public class VivierThematiqueService {

    private static final Logger log = LoggerFactory.getLogger(VivierThematiqueService.class);

    /** Du meilleur au moins bon en performance, scores incomplets en dernier. */
    private static final Comparator<MembreVivierThematique> PAR_PERFORMANCE_DECROISSANTE =
            Comparator.comparing((MembreVivierThematique membre) -> membre.score().getScorePerformance(),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(membre -> membre.score().getCollaborateur().getIdCollaborateur());

    private final TalentService talentService;
    private final CalculService calculService;
    private final ScoreRepository scoreRepository;
    private final VivierThematiqueSource vivierThematiqueSource;

    /**
     * Constructeur de Spring. La source du rattachement est optionnelle : tant
     * que l'import ne la fournit pas, personne n'est classe.
     */
    @Autowired
    public VivierThematiqueService(TalentService talentService,
                                   CalculService calculService,
                                   ScoreRepository scoreRepository,
                                   ObjectProvider<VivierThematiqueSource> vivierThematiqueSource) {
        this(talentService, calculService, scoreRepository, vivierThematiqueSource.getIfAvailable(() -> {
            log.warn("Aucun rattachement direction / vivier thematique : tous les collaborateurs seront "
                    + "non classes tant que l'import ne le fournit pas");
            return direction -> Optional.empty();
        }));
    }

    public VivierThematiqueService(TalentService talentService,
                                   CalculService calculService,
                                   ScoreRepository scoreRepository,
                                   VivierThematiqueSource vivierThematiqueSource) {
        this.talentService = talentService;
        this.calculService = calculService;
        this.scoreRepository = scoreRepository;
        this.vivierThematiqueSource = Objects.requireNonNull(vivierThematiqueSource, "vivierThematiqueSource");
    }

    /** Vivier du collaborateur d'apres sa direction actuelle, vide s'il n'en a pas. */
    public Optional<VivierThematique> vivierDe(Collaborateur collaborateur) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        return vivierPourDirection(collaborateur.getDirection());
    }

    /**
     * Vivier d'apres la direction figee sur le score : un trimestre passe reste
     * range selon la direction qu'avait le collaborateur a l'epoque.
     */
    public Optional<VivierThematique> vivierDe(Score score) {
        Objects.requireNonNull(score, "score");
        return vivierPourDirection(score.getDirection());
    }

    private Optional<VivierThematique> vivierPourDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Optional.empty();
        }
        return vivierThematiqueSource.vivierPourDirection(direction.trim());
    }

    /**
     * Viviers thematiques du trimestre : les collaborateurs dans le perimetre de
     * calcul ayant un score sur le trimestre.
     *
     * @throws com.talent360bank.talent360bank.exception.RessourceIntrouvableException
     *         si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public ResultatViviersThematiques getViviersThematiques(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        Parametre parametre = calculService.chargerParametre(trimestre);

        Map<VivierThematique, List<MembreVivierThematique>> membres = new EnumMap<>(VivierThematique.class);
        for (VivierThematique vivier : VivierThematique.values()) {
            membres.put(vivier, new ArrayList<>());
        }
        List<Collaborateur> nonClasses = new ArrayList<>();

        List<Score> scores = scoreRepository.findByTrimestreAvecCollaborateur(trimestre);
        // Le vivier de chaque direction en une lecture, pas une par score.
        Map<String, VivierThematique> parDirection = vivierThematiqueSource.viviersParDirection(scores.stream()
                .map(Score::getDirection)
                .filter(direction -> direction != null && !direction.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet()));

        for (Score score : scores) {
            Collaborateur collaborateur = score.getCollaborateur();
            if (!collaborateur.estCalculable()) {
                continue;
            }
            Optional<VivierThematique> vivier = score.getDirection() == null ? Optional.empty()
                    : Optional.ofNullable(parDirection.get(score.getDirection().trim()));
            if (vivier.isEmpty()) {
                nonClasses.add(collaborateur);
                continue;
            }
            // Scores incomplets : le collaborateur reste dans son vivier, sans statut,
            // comme TalentService qui ne le detecte pas.
            boolean complet = score.getScorePerformance() != null && score.getScorePotentiel() != null;
            membres.get(vivier.get()).add(new MembreVivierThematique(score,
                    complet && talentService.estTalent(score, parametre),
                    complet && talentService.estHautPotentiel(score, parametre)));
        }

        for (Map.Entry<VivierThematique, List<MembreVivierThematique>> entree : membres.entrySet()) {
            entree.getValue().sort(PAR_PERFORMANCE_DECROISSANTE);
            entree.setValue(List.copyOf(entree.getValue()));
        }
        if (!nonClasses.isEmpty()) {
            log.warn("Viviers thematiques T{} {} : {} collaborateur(s) sans vivier pour leur direction",
                    trimestre.getNumero(), trimestre.getAnnee(), nonClasses.size());
        }
        return new ResultatViviersThematiques(Collections.unmodifiableMap(membres), List.copyOf(nonClasses));
    }

    /** Membres d'un seul vivier thematique, du meilleur au moins bon en performance. */
    @Transactional(readOnly = true)
    public List<MembreVivierThematique> getVivier(VivierThematique vivier, Trimestre trimestre) {
        Objects.requireNonNull(vivier, "vivier");
        return getViviersThematiques(trimestre).membresDe(vivier);
    }
}
