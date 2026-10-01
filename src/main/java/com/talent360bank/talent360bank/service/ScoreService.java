package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persiste les scores calcules par {@link CalculService} dans {@link Score}.
 * La separation est volontaire : CalculService reste une fonction pure des
 * notes et des reglages, ce service porte l'acces base et la transaction.
 */
@Service
public class ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

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
     * Calcule les deux scores d'un collaborateur sur un trimestre et les enregistre.
     * Un recalcul met a jour la ligne existante : il n'y a qu'un Score par
     * collaborateur et par trimestre.
     *
     * @throws RessourceIntrouvableException si les notes ou les reglages sont absents
     */
    @Transactional
    public Score calculerEtEnregistrer(Collaborateur collaborateur, Trimestre trimestre) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);

        Performance performance = performanceRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de performance pour " + collaborateur.getIdCollaborateur()
                                + " sur " + decrire(trimestre)));
        Potentiel potentiel = potentielRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de potentiel pour " + collaborateur.getIdCollaborateur()
                                + " sur " + decrire(trimestre)));

        return enregistrer(collaborateur, trimestre, performance, potentiel, parametre);
    }

    /**
     * Recalcule tout un trimestre. Les reglages et les notes sont charges une
     * seule fois, puis ponderees en memoire.
     *
     * <p>Un collaborateur n'est score que s'il est actif et dispose des deux jeux de
     * notes. Les autres sont remontes dans le bilan avec leur motif.
     *
     * <p>Idempotent : les scores du trimestre que ce recalcul ne produit pas
     * (collaborateur passe inactif, notes retirees par un fichier corrige) sont
     * supprimes, avec leur case 9-box. Un second passage rend les memes lignes.
     */
    @Transactional
    public ResultatRecalcul recalculerTrimestre(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);

        List<Performance> performances = performanceRepository.findByTrimestreAvecCollaborateur(trimestre);
        List<Potentiel> potentiels = potentielRepository.findByTrimestreAvecCollaborateur(trimestre);

        Map<String, Potentiel> potentielParMatricule = new HashMap<>();
        for (Potentiel potentiel : potentiels) {
            potentielParMatricule.put(potentiel.getCollaborateur().getIdCollaborateur(), potentiel);
        }

        List<Score> enregistres = new ArrayList<>();
        List<ResultatRecalcul.CollaborateurIgnore> ignores = new ArrayList<>();
        Set<String> matriculesVus = new HashSet<>();

        for (Performance performance : performances) {
            Collaborateur collaborateur = performance.getCollaborateur();
            String matricule = collaborateur.getIdCollaborateur();
            matriculesVus.add(matricule);

            if (!collaborateur.estCalculable()) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Statut " + collaborateur.getStatut() + ", hors perimetre de calcul"));
                continue;
            }

            Potentiel potentiel = potentielParMatricule.get(matricule);
            if (potentiel == null) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Notes de potentiel absentes"));
                continue;
            }

            enregistres.add(enregistrer(collaborateur, trimestre, performance, potentiel, parametre));
        }

        // Collaborateurs notes en potentiel mais pas en performance : sans les deux
        // axes, la matrice 9-box ne peut pas les placer.
        for (Potentiel potentiel : potentiels) {
            String matricule = potentiel.getCollaborateur().getIdCollaborateur();
            if (!matriculesVus.contains(matricule)) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Notes de performance absentes"));
            }
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

    /**
     * Cree ou met a jour la ligne de Score. positionBox est laissee intacte :
     * elle releve de NeufBoxService, qui la posera a partir de ces deux scores.
     */
    private Score enregistrer(Collaborateur collaborateur, Trimestre trimestre, Performance performance,
                              Potentiel potentiel, Parametre parametre) {
        Score score = scoreRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseGet(() -> {
                    Score nouveau = new Score();
                    nouveau.setCollaborateur(collaborateur);
                    nouveau.setTrimestre(trimestre);
                    return nouveau;
                });

        BigDecimal scorePerformance = calculService.calculerScorePerformance(performance, parametre);
        score.setScorePerformance(scorePerformance);
        score.setCategoriePerformance(calculService.categoriePerformance(
                scorePerformance, parametre.getSeuilsCategoriePerformance()));
        score.setScorePotentiel(calculService.calculerScorePotentiel(potentiel, parametre));
        score.setDateCalcul(LocalDate.now());
        // Direction et manager du moment : l'import suivant ecrasera ceux du collaborateur.
        score.figerOrganisation();

        return scoreRepository.save(score);
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
