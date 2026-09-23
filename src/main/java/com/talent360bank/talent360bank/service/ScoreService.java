package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Calcule les deux scores d'un employe sur un trimestre et les enregistre.
     * Un recalcul met a jour la ligne existante : il n'y a qu'un Score par
     * employe et par trimestre.
     *
     * @throws RessourceIntrouvableException si les notes ou les reglages sont absents
     */
    @Transactional
    public Score calculerEtEnregistrer(Employe employe, Trimestre trimestre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);

        Performance performance = performanceRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de performance pour " + employe.getEmployeeId()
                                + " sur " + decrire(trimestre)));
        Potentiel potentiel = potentielRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de potentiel pour " + employe.getEmployeeId()
                                + " sur " + decrire(trimestre)));

        return enregistrer(employe, trimestre, performance, potentiel, parametre);
    }

    /**
     * Recalcule tout un trimestre. Les reglages et les notes sont charges une
     * seule fois, puis ponderees en memoire.
     *
     * <p>Un employe n'est score que s'il est actif et dispose des deux jeux de
     * notes. Les autres sont remontes dans le bilan avec leur motif.
     */
    @Transactional
    public ResultatRecalcul recalculerTrimestre(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);

        List<Performance> performances = performanceRepository.findByTrimestreAvecEmploye(trimestre);
        List<Potentiel> potentiels = potentielRepository.findByTrimestreAvecEmploye(trimestre);

        Map<String, Potentiel> potentielParMatricule = new HashMap<>();
        for (Potentiel potentiel : potentiels) {
            potentielParMatricule.put(potentiel.getEmploye().getEmployeeId(), potentiel);
        }

        List<Score> enregistres = new ArrayList<>();
        List<ResultatRecalcul.EmployeIgnore> ignores = new ArrayList<>();
        Set<String> matriculesVus = new HashSet<>();

        for (Performance performance : performances) {
            Employe employe = performance.getEmploye();
            String matricule = employe.getEmployeeId();
            matriculesVus.add(matricule);

            if (!employe.estCalculable()) {
                ignores.add(new ResultatRecalcul.EmployeIgnore(matricule,
                        "Statut " + employe.getStatut() + ", hors perimetre de calcul"));
                continue;
            }

            Potentiel potentiel = potentielParMatricule.get(matricule);
            if (potentiel == null) {
                ignores.add(new ResultatRecalcul.EmployeIgnore(matricule,
                        "Notes de potentiel absentes"));
                continue;
            }

            enregistres.add(enregistrer(employe, trimestre, performance, potentiel, parametre));
        }

        // Employes notes en potentiel mais pas en performance : sans les deux
        // axes, la matrice 9-box ne peut pas les placer.
        for (Potentiel potentiel : potentiels) {
            String matricule = potentiel.getEmploye().getEmployeeId();
            if (!matriculesVus.contains(matricule)) {
                ignores.add(new ResultatRecalcul.EmployeIgnore(matricule,
                        "Notes de performance absentes"));
            }
        }

        log.info("Recalcul {} : {} score(s) enregistre(s), {} employe(s) ignore(s)",
                decrire(trimestre), enregistres.size(), ignores.size());

        return new ResultatRecalcul(enregistres, ignores);
    }

    @Transactional(readOnly = true)
    public Optional<Score> rechercher(Employe employe, Trimestre trimestre) {
        return scoreRepository.findByEmployeAndTrimestre(employe, trimestre);
    }

    /** Historique d'un employe, du trimestre le plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Score> historique(Employe employe) {
        return scoreRepository.findHistorique(employe);
    }

    /**
     * Cree ou met a jour la ligne de Score. positionBox est laissee intacte :
     * elle releve de NeufBoxService, qui la posera a partir de ces deux scores.
     */
    private Score enregistrer(Employe employe, Trimestre trimestre, Performance performance,
                              Potentiel potentiel, Parametre parametre) {
        Score score = scoreRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseGet(() -> {
                    Score nouveau = new Score();
                    nouveau.setEmploye(employe);
                    nouveau.setTrimestre(trimestre);
                    return nouveau;
                });

        score.setScorePerformance(calculService.calculerScorePerformance(performance, parametre));
        score.setScorePotentiel(calculService.calculerScorePotentiel(potentiel, parametre));
        score.setDateCalcul(LocalDate.now());

        return scoreRepository.save(score);
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
