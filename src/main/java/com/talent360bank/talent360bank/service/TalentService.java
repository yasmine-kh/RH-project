package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsTalent;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detection automatique des talents : un employe est talent quand ses deux
 * scores atteignent leur seuil, d'apres les {@link SeuilsTalent} du
 * {@link Parametre} du trimestre.
 *
 * <p>La regle est un ET, pas une moyenne : un score de potentiel exceptionnel
 * ne rattrape pas une performance insuffisante.
 */
@Service
public class TalentService {

    private static final Logger log = LoggerFactory.getLogger(TalentService.class);

    private final ScoreRepository scoreRepository;
    private final CalculService calculService;

    public TalentService(ScoreRepository scoreRepository, CalculService calculService) {
        this.scoreRepository = scoreRepository;
        this.calculService = calculService;
    }

    /**
     * Regle de detection, bornes inclusives : un score egal au seuil suffit.
     *
     * @throws DonneesIncompletesException si un score ou un seuil manque
     */
    public boolean estTalent(BigDecimal scorePerformance, BigDecimal scorePotentiel, SeuilsTalent seuils) {
        Objects.requireNonNull(seuils, "seuils");
        if (scorePerformance == null || scorePotentiel == null) {
            throw new DonneesIncompletesException(
                    "Les deux scores sont necessaires pour statuer sur le talent");
        }
        if (seuils.getSeuilPerformance() == null || seuils.getSeuilPotentiel() == null) {
            throw new DonneesIncompletesException("Les seuils de talent ne sont pas configures");
        }
        return scorePerformance.compareTo(seuils.getSeuilPerformance()) >= 0
                && scorePotentiel.compareTo(seuils.getSeuilPotentiel()) >= 0;
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
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        Score score = scoreRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun score calcule pour " + employe.getEmployeeId()
                                + " sur " + decrire(trimestre)));

        return estTalent(score, calculService.chargerParametre(trimestre));
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
        int incomplets = 0;

        for (Score score : scoreRepository.findByTrimestreAvecEmploye(trimestre)) {
            if (!score.getEmploye().estCalculable()) {
                continue;
            }
            if (score.getScorePerformance() == null || score.getScorePotentiel() == null) {
                incomplets++;
                continue;
            }
            if (estTalent(score, parametre)) {
                talents.add(score);
            }
        }

        talents.sort((premier, second) ->
                second.getScorePerformance().compareTo(premier.getScorePerformance()));

        if (incomplets > 0) {
            log.warn("Detection des talents {} : {} score(s) incomplet(s) ecarte(s)",
                    decrire(trimestre), incomplets);
        }
        log.info("Detection des talents {} : {} talent(s)", decrire(trimestre), talents.size());

        return talents;
    }

    @Transactional(readOnly = true)
    public int compterTalents(Trimestre trimestre) {
        return detecterTalents(trimestre).size();
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
