package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Persiste les scores calcules par {@link CalculService} dans {@link Score}.
 * La separation est volontaire : CalculService reste une fonction pure des
 * notes et des reglages, ce service porte l'acces base et la transaction.
 *
 * <p><strong>Score officiel.</strong> Un collaborateur peut avoir, par trimestre,
 * une evaluation de son manager et une auto-evaluation. Le score de chacune est
 * calcule par la meme formule (poids des criteres), puis les deux sont
 * melanges selon {@link com.talent360bank.talent360bank.entity.PonderationSources}
 * ({@link CalculService#scoreOfficiel}). Sans auto-evaluation, ou avec un poids
 * auto nul, le score officiel est celui du manager, comme avant. Sans
 * evaluation du manager (et un poids manager non nul), pas de score officiel :
 * le collaborateur est ignore avec {@link #MOTIF_MANAGER_ABSENT}.
 */
@Service
public class ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

    /** Motif d'un collaborateur sans score officiel faute d'evaluation du manager. */
    public static final String MOTIF_MANAGER_ABSENT =
            "Évaluation du manager absente : auto-évaluation seule, pas de score officiel";

    private final ScoreRepository scoreRepository;
    private final CalculService calculService;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;

    public ScoreService(ScoreRepository scoreRepository,
                        CalculService calculService,
                        PerformanceRepository performanceRepository,
                        PotentielRepository potentielRepository) {
        this.scoreRepository = scoreRepository;
        this.calculService = calculService;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
    }

    /**
     * Calcule les deux scores officiels d'un collaborateur sur un trimestre et
     * les enregistre. Un recalcul met a jour la ligne existante : il n'y a
     * qu'un Score par collaborateur et par trimestre.
     *
     * @throws RessourceIntrouvableException si les notes, l'evaluation du manager
     *                                       (poids manager non nul) ou les reglages sont absents
     */
    @Transactional
    public Score calculerEtEnregistrer(Collaborateur collaborateur, Trimestre trimestre) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);

        Evaluations evaluations = new Evaluations(collaborateur);
        performanceRepository.findByCollaborateurAndTrimestreOrderBySource(collaborateur, trimestre)
                .forEach(evaluations::ajouter);
        potentielRepository.findByCollaborateurAndTrimestreOrderBySource(collaborateur, trimestre)
                .forEach(evaluations::ajouter);
        if (!evaluations.aDesNotesDePerformance()) {
            throw new RessourceIntrouvableException("Aucune note de performance pour "
                    + collaborateur.getIdCollaborateur() + " sur " + decrire(trimestre));
        }
        if (!evaluations.aDesNotesDePotentiel()) {
            throw new RessourceIntrouvableException("Aucune note de potentiel pour "
                    + collaborateur.getIdCollaborateur() + " sur " + decrire(trimestre));
        }
        ScoresOfficiels scores = scoresOfficiels(evaluations, parametre);
        if (scores == null) {
            throw new RessourceIntrouvableException(MOTIF_MANAGER_ABSENT + " pour "
                    + collaborateur.getIdCollaborateur() + " sur " + decrire(trimestre));
        }

        return enregistrer(collaborateur, trimestre, scores, parametre,
                scoreRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre).orElse(null));
    }

    /**
     * Recalcule tout un trimestre. Les reglages et les notes sont charges une
     * seule fois, puis ponderees en memoire.
     *
     * <p>Un collaborateur n'est score que s'il est actif, a des notes de
     * performance et de potentiel, et que chaque axe a un score officiel (voir
     * la classe : pas sans le manager tant que son poids n'est pas nul). Les
     * autres sont remontes dans le bilan avec leur motif.
     *
     * <p>Idempotent : les scores du trimestre que ce recalcul ne produit pas
     * (collaborateur passe inactif, notes retirees par un fichier corrige) sont
     * supprimes, avec leur case 9-box. Un second passage rend les memes lignes.
     *
     * <p><strong>Requetes.</strong> Un nombre fixe de lectures quelle que soit
     * la population (reglages, notes des deux sources, scores existants du
     * trimestre), puis une ecriture par score (insertion ou mise a jour) et une
     * suppression groupee.
     */
    @Transactional
    public ResultatRecalcul recalculerTrimestre(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);

        // Notes des deux sources, regroupees par collaborateur : d'abord ceux qui
        // ont des notes de performance, dans l'ordre de la requete, puis les autres.
        Map<String, Evaluations> parMatricule = new LinkedHashMap<>();
        for (Performance performance : performanceRepository.findByTrimestreToutesSources(trimestre)) {
            evaluationsDe(parMatricule, performance.getCollaborateur()).ajouter(performance);
        }
        for (Potentiel potentiel : potentielRepository.findByTrimestreToutesSources(trimestre)) {
            evaluationsDe(parMatricule, potentiel.getCollaborateur()).ajouter(potentiel);
        }
        // Scores deja enregistres sur le trimestre, lus en une fois : un recalcul
        // les met a jour au lieu d'en chercher un par collaborateur.
        Map<String, Score> existants = new HashMap<>();
        for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            existants.put(score.getCollaborateur().getIdCollaborateur(), score);
        }

        List<Score> enregistres = new ArrayList<>();
        List<ResultatRecalcul.CollaborateurIgnore> ignores = new ArrayList<>();

        for (Map.Entry<String, Evaluations> entree : parMatricule.entrySet()) {
            String matricule = entree.getKey();
            Evaluations evaluations = entree.getValue();
            Collaborateur collaborateur = evaluations.collaborateur;

            // Notes en potentiel mais pas en performance : sans les deux axes,
            // la matrice 9-box ne peut pas les placer.
            if (!evaluations.aDesNotesDePerformance()) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule, "Notes de performance absentes"));
                continue;
            }
            if (!collaborateur.estCalculable()) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Statut " + collaborateur.getStatut() + ", hors périmètre de calcul"));
                continue;
            }
            if (!evaluations.aDesNotesDePotentiel()) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule, "Notes de potentiel absentes"));
                continue;
            }
            ScoresOfficiels scores = scoresOfficiels(evaluations, parametre);
            if (scores == null) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule, MOTIF_MANAGER_ABSENT));
                continue;
            }

            enregistres.add(enregistrer(collaborateur, trimestre, scores, parametre, existants.get(matricule)));
        }

        List<Integer> conserves = enregistres.stream().map(Score::getIdScore).filter(Objects::nonNull).toList();
        int supprimes = conserves.isEmpty()
                ? scoreRepository.supprimerTous(trimestre)
                : scoreRepository.supprimerSaufCeux(trimestre, conserves);

        log.info("Recalcul {} : {} score(s) enregistre(s), {} collaborateur(s) ignore(s), {} score(s) obsolete(s) "
                + "supprime(s)", decrire(trimestre), enregistres.size(), ignores.size(), supprimes);

        return new ResultatRecalcul(enregistres, ignores);
    }

    @Transactional(readOnly = true)
    public Optional<Score> rechercher(Collaborateur collaborateur, Trimestre trimestre) {
        return scoreRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre);
    }

    /** Historique d'un collaborateur, du trimestre le plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Score> historique(Collaborateur collaborateur) {
        return scoreRepository.findHistorique(collaborateur);
    }

    /** Les deux scores officiels d'un collaborateur, null si un axe n'en a pas. */
    private ScoresOfficiels scoresOfficiels(Evaluations evaluations, Parametre parametre) {
        BigDecimal performance = calculService.scoreOfficiel(
                evaluations.performanceManager == null ? null
                        : calculService.calculerScorePerformance(evaluations.performanceManager, parametre),
                evaluations.performanceAuto == null ? null
                        : calculService.calculerScorePerformance(evaluations.performanceAuto, parametre),
                parametre.getPonderationSources());
        BigDecimal potentiel = calculService.scoreOfficiel(
                evaluations.potentielManager == null ? null
                        : calculService.calculerScorePotentiel(evaluations.potentielManager, parametre),
                evaluations.potentielAuto == null ? null
                        : calculService.calculerScorePotentiel(evaluations.potentielAuto, parametre),
                parametre.getPonderationSources());
        return performance == null || potentiel == null ? null : new ScoresOfficiels(performance, potentiel);
    }

    private static Evaluations evaluationsDe(Map<String, Evaluations> parMatricule, Collaborateur collaborateur) {
        return parMatricule.computeIfAbsent(collaborateur.getIdCollaborateur(), id -> new Evaluations(collaborateur));
    }

    private record ScoresOfficiels(BigDecimal performance, BigDecimal potentiel) {
    }

    /** Notes d'un collaborateur sur le trimestre, par source ; null quand une evaluation manque. */
    private static final class Evaluations {

        private final Collaborateur collaborateur;
        private Performance performanceManager;
        private Performance performanceAuto;
        private Potentiel potentielManager;
        private Potentiel potentielAuto;

        private Evaluations(Collaborateur collaborateur) {
            this.collaborateur = collaborateur;
        }

        void ajouter(Performance performance) {
            if (performance.getSource() == SourceEvaluation.AUTO) {
                performanceAuto = performance;
            } else {
                performanceManager = performance;
            }
        }

        void ajouter(Potentiel potentiel) {
            if (potentiel.getSource() == SourceEvaluation.AUTO) {
                potentielAuto = potentiel;
            } else {
                potentielManager = potentiel;
            }
        }

        boolean aDesNotesDePerformance() {
            return performanceManager != null || performanceAuto != null;
        }

        boolean aDesNotesDePotentiel() {
            return potentielManager != null || potentielAuto != null;
        }
    }

    /**
     * Cree ou met a jour la ligne de Score avec les scores officiels.
     * positionBox est laissee intacte : elle releve de NeufBoxService, qui la
     * posera a partir de ces deux scores.
     *
     * @param existant score deja enregistre du collaborateur sur le trimestre, null s'il n'en a pas
     */
    private Score enregistrer(Collaborateur collaborateur, Trimestre trimestre, ScoresOfficiels scores,
                              Parametre parametre, Score existant) {
        Score score = existant;
        if (score == null) {
            score = new Score();
            score.setCollaborateur(collaborateur);
            score.setTrimestre(trimestre);
        }

        score.setScorePerformance(scores.performance());
        score.setCategoriePerformance(calculService.categoriePerformance(
                scores.performance(), parametre.getSeuilsCategoriePerformance()));
        score.setScorePotentiel(scores.potentiel());
        score.setDateCalcul(LocalDate.now());
        // Direction et manager du moment : l'import suivant ecrasera ceux du collaborateur.
        // Entite et manager sont LAZY : seules leurs references sont recopiees, sans lecture.
        score.figerOrganisation();

        return scoreRepository.save(score);
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
