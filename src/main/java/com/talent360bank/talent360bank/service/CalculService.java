package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calcule les scores de performance et de potentiel a partir des notes saisies
 * et des poids du {@link Parametre} du trimestre. Aucun poids n'est code ici :
 * changer une ponderation est une operation de configuration, pas de
 * developpement.
 */
@Service
public class CalculService {

    /** Deux decimales : l'arrondi decide des comparaisons aux seuils (85, 70...). */
    public static final int PRECISION_SCORE = 2;

    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;

    private final ParametreRepository parametreRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;

    public CalculService(ParametreRepository parametreRepository,
                         PerformanceRepository performanceRepository,
                         PotentielRepository potentielRepository) {
        this.parametreRepository = parametreRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
    }

    /**
     * Score de performance d'un employe sur un trimestre, sur 100.
     *
     * @throws RessourceIntrouvableException si les notes ou les reglages du
     *                                       trimestre sont absents
     */
    @Transactional(readOnly = true)
    public BigDecimal calculerScorePerformance(Employe employe, Trimestre trimestre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        Performance performance = performanceRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de performance pour " + employe.getMatricule()
                                + " sur " + decrire(trimestre)));

        return calculerScorePerformance(performance, chargerParametre(trimestre));
    }

    /**
     * Score de potentiel d'un employe sur un trimestre, sur 100.
     *
     * @throws RessourceIntrouvableException si les notes ou les reglages du
     *                                       trimestre sont absents
     */
    @Transactional(readOnly = true)
    public BigDecimal calculerScorePotentiel(Employe employe, Trimestre trimestre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        Potentiel potentiel = potentielRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de potentiel pour " + employe.getMatricule()
                                + " sur " + decrire(trimestre)));

        return calculerScorePotentiel(potentiel, chargerParametre(trimestre));
    }

    /** Variante sans acces base, pour recalculer un lot avec des reglages deja charges. */
    public BigDecimal calculerScorePerformance(Performance performance, Parametre parametre) {
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(parametre, "parametre");

        PoidsPerformance poids = exiger(parametre.getPoidsPerformance(),
                "Les poids de performance ne sont pas configures");

        return moyennePonderee(
                new BigDecimal[]{
                        performance.getNoteObjectifs(),
                        performance.getNoteCompetences(),
                        performance.getNoteComportement(),
                        performance.getNoteContribution(),
                        performance.getNoteDeveloppement()},
                new BigDecimal[]{
                        poids.getPoidsObjectifs(),
                        poids.getPoidsCompetences(),
                        poids.getPoidsComportement(),
                        poids.getPoidsContribution(),
                        poids.getPoidsDeveloppement()},
                "performance");
    }

    /** Variante sans acces base, pour recalculer un lot avec des reglages deja charges. */
    public BigDecimal calculerScorePotentiel(Potentiel potentiel, Parametre parametre) {
        Objects.requireNonNull(potentiel, "potentiel");
        Objects.requireNonNull(parametre, "parametre");

        PoidsPotentiel poids = exiger(parametre.getPoidsPotentiel(),
                "Les poids de potentiel ne sont pas configures");

        return moyennePonderee(
                new BigDecimal[]{
                        potentiel.getNoteLearning(),
                        potentiel.getNoteLeadership(),
                        potentiel.getNoteAdaptabilite(),
                        potentiel.getNoteComplexite(),
                        potentiel.getNoteMobilite(),
                        potentiel.getNoteStrategie(),
                        potentiel.getNoteAutonomie()},
                new BigDecimal[]{
                        poids.getPoidsLearning(),
                        poids.getPoidsLeadership(),
                        poids.getPoidsAdaptabilite(),
                        poids.getPoidsComplexite(),
                        poids.getPoidsMobilite(),
                        poids.getPoidsStrategie(),
                        poids.getPoidsAutonomie()},
                "potentiel");
    }

    /** Reglages applicables au trimestre. */
    @Transactional(readOnly = true)
    public Parametre chargerParametre(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        return parametreRepository.findByTrimestre(trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun parametre configure pour " + decrire(trimestre)));
    }

    /**
     * Moyenne ponderee ramenee sur 100. La division se fait par la somme reelle
     * des poids et non par la constante 100 : le resultat reste juste meme si un
     * jeu de reglages a ete ecrit hors application, sans passer par la validation.
     */
    private BigDecimal moyennePonderee(BigDecimal[] notes, BigDecimal[] poids, String contexte) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal sommePoids = BigDecimal.ZERO;

        for (int i = 0; i < notes.length; i++) {
            if (notes[i] == null) {
                throw new DonneesIncompletesException(
                        "Note manquante (critere " + (i + 1) + ") pour le calcul de " + contexte);
            }
            if (poids[i] == null) {
                throw new DonneesIncompletesException(
                        "Poids manquant (critere " + (i + 1) + ") pour le calcul de " + contexte);
            }
            total = total.add(notes[i].multiply(poids[i]));
            sommePoids = sommePoids.add(poids[i]);
        }

        if (sommePoids.signum() == 0) {
            throw new DonneesIncompletesException(
                    "Tous les poids de " + contexte + " sont a zero, le score est indefini");
        }

        return total.divide(sommePoids, PRECISION_SCORE, ARRONDI);
    }

    private <T> T exiger(T valeur, String message) {
        if (valeur == null) {
            throw new DonneesIncompletesException(message);
        }
        return valeur;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
