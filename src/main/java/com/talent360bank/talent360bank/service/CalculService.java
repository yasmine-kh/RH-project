package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.SeuilsCategoriePerformance;
import com.talent360bank.talent360bank.entity.SeuilsGapCompetence;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
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
     * Score de performance d'un collaborateur sur un trimestre, sur 100.
     *
     * @throws RessourceIntrouvableException si les notes ou les reglages du
     *                                       trimestre sont absents
     */
    @Transactional(readOnly = true)
    public BigDecimal calculerScorePerformance(Collaborateur collaborateur, Trimestre trimestre) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(trimestre, "trimestre");

        Performance performance = performanceRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de performance pour " + collaborateur.getIdCollaborateur()
                                + " sur " + decrire(trimestre)));

        return calculerScorePerformance(performance, chargerParametre(trimestre));
    }

    /**
     * Score de potentiel d'un collaborateur sur un trimestre, sur 100.
     *
     * @throws RessourceIntrouvableException si les notes ou les reglages du
     *                                       trimestre sont absents
     */
    @Transactional(readOnly = true)
    public BigDecimal calculerScorePotentiel(Collaborateur collaborateur, Trimestre trimestre) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(trimestre, "trimestre");

        Potentiel potentiel = potentielRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune note de potentiel pour " + collaborateur.getIdCollaborateur()
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

    /**
     * Categorie d'un score de performance, comme la colonne J de
     * 02_PERFORMANCE. Bornes inclusives : un score egal au seuil decroche la
     * categorie. Le score est compare tel qu'arrondi a deux decimales.
     *
     * @throws DonneesIncompletesException si le score ou les seuils manquent
     */
    public CategoriePerformance categoriePerformance(BigDecimal scorePerformance,
                                                     SeuilsCategoriePerformance seuils) {
        if (scorePerformance == null) {
            throw new DonneesIncompletesException("Score de performance absent, categorie indeterminable");
        }
        if (seuils == null || seuils.getSeuilExceptionnelle() == null || seuils.getSeuilElevee() == null
                || seuils.getSeuilSolide() == null || seuils.getSeuilARenforcer() == null) {
            throw new DonneesIncompletesException(
                    "Les seuils des categories de performance ne sont pas configures");
        }
        if (scorePerformance.compareTo(seuils.getSeuilExceptionnelle()) >= 0) {
            return CategoriePerformance.EXCEPTIONNELLE;
        }
        if (scorePerformance.compareTo(seuils.getSeuilElevee()) >= 0) {
            return CategoriePerformance.ELEVEE;
        }
        if (scorePerformance.compareTo(seuils.getSeuilSolide()) >= 0) {
            return CategoriePerformance.SOLIDE;
        }
        if (scorePerformance.compareTo(seuils.getSeuilARenforcer()) >= 0) {
            return CategoriePerformance.A_RENFORCER;
        }
        return CategoriePerformance.INSUFFISANTE;
    }

    /**
     * Statut du gap d'une competence, comme la colonne H de 06_EMPLOYEE_SKILLS :
     * gap = niveau cible - niveau actuel ; gap <= 0 Maitrise ; gap = 1 A
     * developper, quel que soit le seuil (le classeur teste ce cas en premier) ;
     * gap >= seuil Prioritaire ; sinon A developper.
     *
     * @return null si l'un des deux niveaux est inconnu : le gap l'est aussi
     * @throws DonneesIncompletesException si le seuil n'est pas configure
     */
    public StatutGapCompetence statutGap(Integer niveauActuel, Integer niveauCible, SeuilsGapCompetence seuils) {
        if (niveauActuel == null || niveauCible == null) {
            return null;
        }
        if (seuils == null || seuils.getSeuilPrioritaire() == null) {
            throw new DonneesIncompletesException("Le seuil de gap de competence n'est pas configure");
        }
        int gap = niveauCible - niveauActuel;
        if (gap <= 0) {
            return StatutGapCompetence.MAITRISE;
        }
        if (gap == 1) {
            return StatutGapCompetence.A_DEVELOPPER;
        }
        return gap >= seuils.getSeuilPrioritaire() ? StatutGapCompetence.PRIORITAIRE : StatutGapCompetence.A_DEVELOPPER;
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
