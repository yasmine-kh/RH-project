package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Poste cible de chaque collaborateur : le poste critique ou son matching de
 * succession est le meilleur, avec ce matching et sa readiness. Sert la fiche
 * (Talent Passport), la liste des collaborateurs et le tableau de bord DG.
 *
 * <p>Tous les postes critiques sont essayes, pas seulement ceux ou le RH a
 * designe le collaborateur successeur : le resultat dit s'il l'est
 * ({@link ResultatPosteCible#successeurIdentifie()}). Le titulaire d'un poste
 * n'est pas candidat a ce poste, comme dans le classement des candidats.
 * A matching egal, le plus petit Poste_ID l'emporte.
 *
 * <p>Le matching est celui de {@link SuccessionService#evaluer} avec le
 * {@link Parametre} et la date de reference du trimestre : aucune regle
 * nouvelle. Lecture seule, un nombre fixe de requetes pour tout le trimestre.
 */
@Service
public class PosteCibleService {

    private static final Logger log = LoggerFactory.getLogger(PosteCibleService.class);

    /** Meilleur matching d'abord ; a egalite, le plus petit Poste_ID. */
    private static final Comparator<ResultatPosteCible> MEILLEUR_D_ABORD = Comparator
            .comparing((ResultatPosteCible cible) -> cible.matching().scoreMatching(), Comparator.reverseOrder())
            .thenComparing(cible -> cible.poste().getPosteId());

    private final PosteRepository posteRepository;
    private final ScoreRepository scoreRepository;
    private final PotentielRepository potentielRepository;
    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final SuccessionService successionService;
    private final CalculService calculService;
    private final SuccesseurIdentifieSource successeurIdentifieSource;

    /**
     * Constructeur de Spring. La source des successeurs est optionnelle, comme
     * pour {@link PosteCritiqueService} : sans elle, personne n'est successeur identifie.
     */
    @Autowired
    public PosteCibleService(PosteRepository posteRepository, ScoreRepository scoreRepository,
                             PotentielRepository potentielRepository,
                             CompetenceCollaborateurRepository competenceCollaborateurRepository,
                             SuccessionService successionService, CalculService calculService,
                             ObjectProvider<SuccesseurIdentifieSource> successeurIdentifieSource) {
        this(posteRepository, scoreRepository, potentielRepository, competenceCollaborateurRepository,
                successionService, calculService, successeurIdentifieSource.getIfAvailable(() -> posteId -> List.of()));
    }

    public PosteCibleService(PosteRepository posteRepository, ScoreRepository scoreRepository,
                             PotentielRepository potentielRepository,
                             CompetenceCollaborateurRepository competenceCollaborateurRepository,
                             SuccessionService successionService, CalculService calculService,
                             SuccesseurIdentifieSource successeurIdentifieSource) {
        this.posteRepository = posteRepository;
        this.scoreRepository = scoreRepository;
        this.potentielRepository = potentielRepository;
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
        this.successionService = successionService;
        this.calculService = calculService;
        this.successeurIdentifieSource = Objects.requireNonNull(successeurIdentifieSource,
                "successeurIdentifieSource");
    }

    /**
     * Poste cible d'un collaborateur, sans acces base : pour un lot ou une
     * fiche qui ont deja leurs donnees.
     *
     * @param postes            postes candidats, competences requises chargees ; seuls
     *                          les postes critiques sont retenus
     * @param postesIdentifies  Poste_ID des postes ou il est successeur identifie
     * @return vide si le collaborateur n'est pas actif, si son score du trimestre
     *         est absent ou incomplet, ou s'il n'y a aucun poste critique dont il
     *         n'est pas titulaire
     * @throws com.talent360bank.talent360bank.exception.DonneesIncompletesException
     *         si les reglages du matching manquent
     */
    public Optional<ResultatPosteCible> meilleurPoste(Collaborateur collaborateur, Score score, Potentiel potentiel,
                                                      List<CompetenceCollaborateur> competences, List<Poste> postes,
                                                      Set<String> postesIdentifies, Parametre parametre,
                                                      LocalDate dateReference) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(postes, "postes");
        Objects.requireNonNull(parametre, "parametre");
        if (!collaborateur.estCalculable() || score == null || score.getScorePerformance() == null
                || score.getScorePotentiel() == null) {
            return Optional.empty();
        }
        ResultatPosteCible meilleur = null;
        for (Poste poste : postes) {
            if (!PosteCritiqueService.estCritique(poste)
                    || collaborateur.getIdCollaborateur().equals(poste.getTitulaireId())) {
                continue;
            }
            ResultatMatching matching = successionService.evaluer(collaborateur, poste, score, potentiel,
                    competences == null ? List.of() : competences, parametre, dateReference);
            ResultatPosteCible cible = new ResultatPosteCible(collaborateur, poste, matching,
                    postesIdentifies != null && postesIdentifies.contains(poste.getPosteId()));
            if (meilleur == null || MEILLEUR_D_ABORD.compare(cible, meilleur) < 0) {
                meilleur = cible;
            }
        }
        return Optional.ofNullable(meilleur);
    }

    /**
     * Poste cible de chaque collaborateur actif ayant un score complet sur le
     * trimestre, par matricule. Six lectures quelle que soit la population :
     * reglages, postes, scores, potentiels, competences, successeurs identifies.
     *
     * @throws com.talent360bank.talent360bank.exception.RessourceIntrouvableException
     *         si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public List<ResultatPosteCible> postesCibles(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        Parametre parametre = calculService.chargerParametre(trimestre);
        List<Poste> critiques = posteRepository.findAllAvecCompetences().stream()
                .filter(PosteCritiqueService::estCritique)
                .toList();
        if (critiques.isEmpty()) {
            return List.of();
        }

        List<Score> scores = scoreRepository.findByTrimestreAvecCollaborateur(trimestre).stream()
                .filter(score -> score.getCollaborateur().estCalculable())
                .filter(score -> score.getScorePerformance() != null && score.getScorePotentiel() != null)
                .sorted(Comparator.comparing(score -> score.getCollaborateur().getIdCollaborateur()))
                .toList();
        if (scores.isEmpty()) {
            return List.of();
        }

        Map<String, Potentiel> potentiels = new HashMap<>();
        // Leadership et mobilite du matching : evaluation du manager, comme SuccessionService.
        for (Potentiel potentiel
                : potentielRepository.findByTrimestreAvecCollaborateur(trimestre, SourceEvaluation.MANAGER)) {
            potentiels.put(potentiel.getCollaborateur().getIdCollaborateur(), potentiel);
        }
        Set<String> ids = new HashSet<>();
        scores.forEach(score -> ids.add(score.getCollaborateur().getIdCollaborateur()));
        Map<String, List<CompetenceCollaborateur>> competences = new HashMap<>();
        for (CompetenceCollaborateur skill : competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(ids)) {
            competences.computeIfAbsent(skill.getCollaborateur().getIdCollaborateur(), cle -> new ArrayList<>())
                    .add(skill);
        }
        Map<String, Set<String>> postesIdentifies = new HashMap<>();
        successeurIdentifieSource.successeursParPoste(critiques.stream().map(Poste::getPosteId).toList())
                .forEach((posteId, successeurs) -> successeurs.forEach(id ->
                        postesIdentifies.computeIfAbsent(id, cle -> new HashSet<>()).add(posteId)));

        List<ResultatPosteCible> cibles = new ArrayList<>();
        for (Score score : scores) {
            Collaborateur collaborateur = score.getCollaborateur();
            String id = collaborateur.getIdCollaborateur();
            meilleurPoste(collaborateur, score, potentiels.get(id), competences.getOrDefault(id, List.of()),
                    critiques, postesIdentifies.getOrDefault(id, Set.of()), parametre, trimestre.getDateReference())
                    .ifPresent(cibles::add);
        }
        log.info("Postes cibles T{} {} : {} collaborateur(s)", trimestre.getNumero(), trimestre.getAnnee(),
                cibles.size());
        return List.copyOf(cibles);
    }
}
