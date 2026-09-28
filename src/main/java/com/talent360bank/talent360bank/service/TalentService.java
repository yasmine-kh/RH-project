package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsTalent;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Detection automatique des talents et des hauts potentiels, d'apres les
 * {@link SeuilsTalent} du {@link Parametre} du trimestre, et vivier de releve
 * qui les reunit (10_TALENTS).
 *
 * <p>Chaque regle est un ET, pas une moyenne : un score exceptionnel ne
 * rattrape pas l'autre. Talent et haut potentiel sont independants, un
 * employe peut cumuler les deux.
 */
@Service
public class TalentService {

    private static final Logger log = LoggerFactory.getLogger(TalentService.class);

    /** Du meilleur au moins bon en performance, ordre commun a toutes les listes. */
    private static final Comparator<Score> PAR_PERFORMANCE_DECROISSANTE =
            Comparator.comparing(Score::getScorePerformance, Comparator.reverseOrder());

    private final ScoreRepository scoreRepository;
    private final CalculService calculService;

    public TalentService(ScoreRepository scoreRepository, CalculService calculService) {
        this.scoreRepository = scoreRepository;
        this.calculService = calculService;
    }

    // --- talent --------------------------------------------------------------

    /**
     * Regle de detection, bornes inclusives : un score egal au seuil suffit.
     *
     * @throws DonneesIncompletesException si un score ou un seuil manque
     */
    public boolean estTalent(BigDecimal scorePerformance, BigDecimal scorePotentiel, SeuilsTalent seuils) {
        Objects.requireNonNull(seuils, "seuils");
        return atteintLesSeuils(scorePerformance, scorePotentiel,
                seuils.getSeuilPerformance(), seuils.getSeuilPotentiel(), "talent");
    }

    /** Variante sans acces base, pour trancher un lot avec des reglages deja charges. */
    public boolean estTalent(Score score, Parametre parametre) {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(parametre, "parametre");
        return estTalent(score.getScorePerformance(), score.getScorePotentiel(),
                parametre.getSeuilsTalent());
    }

    /**
     * Statut de talent d'un employe sur un trimestre.
     *
     * @throws RessourceIntrouvableException si le score ou les reglages sont absents
     */
    @Transactional(readOnly = true)
    public boolean estTalent(Employe employe, Trimestre trimestre) {
        return estTalent(chargerScore(employe, trimestre), calculService.chargerParametre(trimestre));
    }

    /**
     * Scores des talents du trimestre, du meilleur au moins bon en performance.
     *
     * <p>Lecture seule : rien n'est ecrit. Les employes hors perimetre et les
     * scores incomplets sont comptes et logues plutot que de faire echouer la
     * detection pour tout le monde.
     */
    @Transactional(readOnly = true)
    public List<Score> detecterTalents(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        Parametre parametre = calculService.chargerParametre(trimestre);

        List<Score> talents = new ArrayList<>();
        for (Score score : scoresEvaluables(trimestre, "talents")) {
            if (estTalent(score, parametre)) {
                talents.add(score);
            }
        }
        talents.sort(PAR_PERFORMANCE_DECROISSANTE);

        log.info("Detection des talents {} : {} talent(s)", decrire(trimestre), talents.size());
        return talents;
    }

    @Transactional(readOnly = true)
    public int compterTalents(Trimestre trimestre) {
        return detecterTalents(trimestre).size();
    }

    // --- haut potentiel ------------------------------------------------------

    /**
     * Regle du haut potentiel (00_PARAMETRES B40 et B41, 10_TALENTS!F) :
     * potentiel ET performance a leurs seuils, bornes inclusives.
     *
     * @throws DonneesIncompletesException si un score ou un seuil manque
     */
    public boolean estHautPotentiel(BigDecimal scorePerformance, BigDecimal scorePotentiel,
                                    SeuilsTalent seuils) {
        Objects.requireNonNull(seuils, "seuils");
        return atteintLesSeuils(scorePerformance, scorePotentiel,
                seuils.getSeuilHautPotentielPerformance(), seuils.getSeuilHautPotentielPotentiel(),
                "haut potentiel");
    }

    /** Variante sans acces base, pour trancher un lot avec des reglages deja charges. */
    public boolean estHautPotentiel(Score score, Parametre parametre) {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(parametre, "parametre");
        return estHautPotentiel(score.getScorePerformance(), score.getScorePotentiel(),
                parametre.getSeuilsTalent());
    }

    /**
     * Statut de haut potentiel d'un employe sur un trimestre.
     *
     * @throws RessourceIntrouvableException si le score ou les reglages sont absents
     */
    @Transactional(readOnly = true)
    public boolean estHautPotentiel(Employe employe, Trimestre trimestre) {
        return estHautPotentiel(chargerScore(employe, trimestre), calculService.chargerParametre(trimestre));
    }

    /** Scores des hauts potentiels du trimestre, du meilleur au moins bon en performance. */
    @Transactional(readOnly = true)
    public List<Score> detecterHautsPotentiels(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        Parametre parametre = calculService.chargerParametre(trimestre);

        List<Score> hautsPotentiels = new ArrayList<>();
        for (Score score : scoresEvaluables(trimestre, "hauts potentiels")) {
            if (estHautPotentiel(score, parametre)) {
                hautsPotentiels.add(score);
            }
        }
        hautsPotentiels.sort(PAR_PERFORMANCE_DECROISSANTE);

        log.info("Detection des hauts potentiels {} : {} haut(s) potentiel(s)",
                decrire(trimestre), hautsPotentiels.size());
        return hautsPotentiels;
    }

    // --- vivier de releve ----------------------------------------------------

    /**
     * Vivier de releve du trimestre : talents OU hauts potentiels
     * (10_TALENTS!J), chacun une seule fois avec la raison de sa presence, du
     * meilleur au moins bon en performance.
     *
     * <p>Calcule a la demande, rien n'est ecrit : l'alimentation de Vivier /
     * AppartenanceVivier reste a faire.
     */
    @Transactional(readOnly = true)
    public List<MembreVivierReleve> getVivierReleve(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        Parametre parametre = calculService.chargerParametre(trimestre);

        List<MembreVivierReleve> vivier = new ArrayList<>();
        for (Score score : scoresEvaluables(trimestre, "vivier de releve")) {
            boolean talent = estTalent(score, parametre);
            boolean hautPotentiel = estHautPotentiel(score, parametre);
            if (talent || hautPotentiel) {
                vivier.add(new MembreVivierReleve(score, talent, hautPotentiel));
            }
        }
        vivier.sort(Comparator.comparing(MembreVivierReleve::score, PAR_PERFORMANCE_DECROISSANTE));

        log.info("Vivier de releve {} : {} membre(s)", decrire(trimestre), vivier.size());
        return vivier;
    }

    // --- outils --------------------------------------------------------------

    private boolean atteintLesSeuils(BigDecimal scorePerformance, BigDecimal scorePotentiel,
                                     BigDecimal seuilPerformance, BigDecimal seuilPotentiel,
                                     String statut) {
        if (scorePerformance == null || scorePotentiel == null) {
            throw new DonneesIncompletesException(
                    "Les deux scores sont necessaires pour statuer sur le " + statut);
        }
        if (seuilPerformance == null || seuilPotentiel == null) {
            throw new DonneesIncompletesException("Les seuils de " + statut + " ne sont pas configures");
        }
        return scorePerformance.compareTo(seuilPerformance) >= 0
                && scorePotentiel.compareTo(seuilPotentiel) >= 0;
    }

    private Score chargerScore(Employe employe, Trimestre trimestre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");
        return scoreRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun score calcule pour " + employe.getEmployeeId()
                                + " sur " + decrire(trimestre)));
    }

    /**
     * Scores du trimestre sur lesquels on peut statuer : employes dans le
     * perimetre de calcul, les deux scores presents. Les incomplets sont
     * comptes et logues.
     */
    private List<Score> scoresEvaluables(Trimestre trimestre, String detection) {
        List<Score> evaluables = new ArrayList<>();
        int incomplets = 0;

        for (Score score : scoreRepository.findByTrimestreAvecEmploye(trimestre)) {
            if (!score.getEmploye().estCalculable()) {
                continue;
            }
            if (score.getScorePerformance() == null || score.getScorePotentiel() == null) {
                incomplets++;
                continue;
            }
            evaluables.add(score);
        }

        if (incomplets > 0) {
            log.warn("Detection des {} {} : {} score(s) incomplet(s) ecarte(s)",
                    detection, decrire(trimestre), incomplets);
        }
        return evaluables;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
