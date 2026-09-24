package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.BaremeCompetences;
import com.talent360bank.talent360bank.entity.BaremeExperience;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.NiveauReadiness;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rapproche des candidats d'un poste cible : score de matching sur 100 et
 * delai de disponibilite (readiness) qui en decoule.
 *
 * <p>Six criteres, ponderes par le {@link PoidsSuccession} du
 * {@link Parametre} du trimestre (25 / 20 / 20 / 15 / 10 / 10 par defaut) :
 * competences, performance, potentiel, experience, leadership, mobilite.
 * Comme ailleurs dans le moteur, aucun poids n'est ecrit ici : le RH les
 * change par configuration.
 *
 * <p>Service de lecture seule : il calcule et classe, il n'ecrit rien. La
 * constitution automatique des viviers a partir de ces classements reste a
 * arbitrer et n'est pas faite ici.
 */
@Service
public class SuccessionService {

    private static final Logger log = LoggerFactory.getLogger(SuccessionService.class);

    private static final BigDecimal CENT = new BigDecimal("100");
    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;
    private static final BigDecimal JOURS_PAR_AN = new BigDecimal("365.25");

    private final PosteRepository posteRepository;
    private final ScoreRepository scoreRepository;
    private final PotentielRepository potentielRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final CalculService calculService;

    public SuccessionService(PosteRepository posteRepository,
                             ScoreRepository scoreRepository,
                             PotentielRepository potentielRepository,
                             EmployeeSkillRepository employeeSkillRepository,
                             CalculService calculService) {
        this.posteRepository = posteRepository;
        this.scoreRepository = scoreRepository;
        this.potentielRepository = potentielRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.calculService = calculService;
    }

    /**
     * Delai avant qu'un candidat soit pret, d'apres son score de matching.
     * Bornes inclusives : un score egal au seuil suffit a decrocher le niveau.
     *
     * @throws DonneesIncompletesException si le score ou les seuils manquent
     */
    public NiveauReadiness readinessPour(BigDecimal scoreMatching, SeuilsReadiness seuils) {
        Objects.requireNonNull(seuils, "seuils");
        if (scoreMatching == null) {
            throw new DonneesIncompletesException("Score de matching absent, readiness indeterminable");
        }
        if (seuils.getSeuilReadyNow() == null || seuils.getSeuilMoins1An() == null
                || seuils.getSeuilEntre1Et2Ans() == null) {
            throw new DonneesIncompletesException("Les seuils de readiness ne sont pas configures");
        }
        if (scoreMatching.compareTo(seuils.getSeuilReadyNow()) >= 0) {
            return NiveauReadiness.READY_NOW;
        }
        if (scoreMatching.compareTo(seuils.getSeuilMoins1An()) >= 0) {
            return NiveauReadiness.MOINS_1_AN;
        }
        if (scoreMatching.compareTo(seuils.getSeuilEntre1Et2Ans()) >= 0) {
            return NiveauReadiness.ENTRE_1_ET_2_ANS;
        }
        return NiveauReadiness.PLUS_2_ANS;
    }

    /**
     * Couverture des competences exigees par le poste, sur 100, selon le
     * {@link BaremeCompetences} du Parametre et comme dans 09_SUCCESSION :
     * chaque competence vaut max(0, 100 - points par niveau manquant x
     * max(0, requis - actuel)), puis moyenne sur les competences exigees.
     *
     * <p>Le rapprochement se fait par identifiant de competence, pas par
     * libelle. Une competence exigee que le candidat n'a pas compte pour zero
     * (le classeur la suppose au niveau 3 : en attente d'arbitrage client) ;
     * un niveau superieur a l'exigence ne rapporte pas de bonus : depasser
     * l'attendu ne compense pas un manque ailleurs.
     *
     * @return null si le poste n'exige aucune competence chiffree, le critere
     *         est alors ecarte de la moyenne au lieu de compter pour zero
     * @throws DonneesIncompletesException si le bareme n'est pas configure
     */
    public BigDecimal scoreCompetences(Poste poste, List<EmployeeSkill> competencesCandidat,
                                       BaremeCompetences bareme) {
        Objects.requireNonNull(poste, "poste");
        if (bareme == null || bareme.getPointsParNiveauManquant() == null) {
            throw new DonneesIncompletesException("Le bareme des competences n'est pas configure");
        }

        Map<String, Integer> acquis = new HashMap<>();
        if (competencesCandidat != null) {
            for (EmployeeSkill skill : competencesCandidat) {
                Competence competence = skill.getCompetence();
                if (competence == null || skill.getNiveauActuel() == null) {
                    continue;
                }
                acquis.merge(competence.getCompetenceId(), skill.getNiveauActuel(), Math::max);
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        int exigencesChiffrees = 0;

        for (Poste.ExigenceCompetence exigence : poste.getExigencesCompetences()) {
            Integer requis = exigence.niveauRequis();
            if (requis == null || requis <= 0) {
                continue;
            }
            exigencesChiffrees++;

            Integer actuel = acquis.get(exigence.competence().getCompetenceId());
            if (actuel == null || actuel <= 0) {
                continue;
            }
            int niveauxManquants = Math.max(0, requis - actuel);
            BigDecimal couverture = CENT.subtract(
                    bareme.getPointsParNiveauManquant().multiply(BigDecimal.valueOf(niveauxManquants)));
            total = total.add(couverture.max(BigDecimal.ZERO));
        }

        if (exigencesChiffrees == 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(exigencesChiffrees),
                CalculService.PRECISION_SCORE, ARRONDI);
    }

    /**
     * Critere experience selon le {@link BaremeExperience} du Parametre :
     * min(plafond, annees d'anciennete x points par annee).
     *
     * <p>L'anciennete est en annees decimales arrondies au dixieme, comme
     * dans 01_COLLABORATEURS : ROUND((aujourd'hui - date d'entree) / 365.25, 1).
     * Les annees revolues de {@link Employe#getAnciennete()} feraient perdre
     * jusqu'a une annee de points.
     *
     * @return null si la date d'entree est inconnue
     * @throws DonneesIncompletesException si le bareme n'est pas configure
     */
    public BigDecimal scoreExperience(Employe candidat, BaremeExperience bareme) {
        Objects.requireNonNull(candidat, "candidat");
        if (bareme == null || bareme.getPointsParAnnee() == null || bareme.getPlafond() == null) {
            throw new DonneesIncompletesException("Le bareme d'experience n'est pas configure");
        }

        if (candidat.getDateEntree() == null) {
            return null;
        }
        BigDecimal anciennete = BigDecimal.valueOf(
                        ChronoUnit.DAYS.between(candidat.getDateEntree(), LocalDate.now()))
                .divide(JOURS_PAR_AN, 1, ARRONDI);
        if (anciennete.signum() <= 0) {
            return BigDecimal.ZERO.setScale(CalculService.PRECISION_SCORE, ARRONDI);
        }
        return anciennete
                .multiply(bareme.getPointsParAnnee())
                .min(bareme.getPlafond())
                .setScale(CalculService.PRECISION_SCORE, ARRONDI);
    }

    /**
     * Matching d'un candidat sur un poste, sans acces base : pour classer un lot
     * avec des reglages et des donnees deja charges.
     *
     * <p>Le {@link Potentiel} peut etre absent : leadership et mobilite sont
     * alors ecartes de la moyenne, les quatre autres criteres suffisent a
     * produire un classement plutot que de rejeter le candidat.
     */
    public ResultatMatching evaluer(Employe candidat, Poste poste, Score score, Potentiel potentiel,
                                    List<EmployeeSkill> competencesCandidat, Parametre parametre) {
        Objects.requireNonNull(candidat, "candidat");
        Objects.requireNonNull(poste, "poste");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(parametre, "parametre");

        PoidsSuccession poids = parametre.getPoidsSuccession();
        if (poids == null) {
            throw new DonneesIncompletesException(
                    "Les poids du matching succession ne sont pas configures");
        }

        ResultatMatching.DetailMatching detail = new ResultatMatching.DetailMatching(
                scoreCompetences(poste, competencesCandidat, parametre.getBaremeCompetences()),
                score.getScorePerformance(),
                score.getScorePotentiel(),
                scoreExperience(candidat, parametre.getBaremeExperience()),
                potentiel == null ? null : potentiel.getNoteLeadership(),
                potentiel == null ? null : potentiel.getNoteMobilite());

        BigDecimal scoreMatching = moyennePonderee(
                new String[]{"competences", "performance", "potentiel", "experience",
                        "leadership", "mobilite"},
                new BigDecimal[]{detail.competences(), detail.performance(), detail.potentiel(),
                        detail.experience(), detail.leadership(), detail.mobilite()},
                new BigDecimal[]{poids.getPoidsCompetences(), poids.getPoidsPerformance(),
                        poids.getPoidsPotentiel(), poids.getPoidsExperience(),
                        poids.getPoidsLeadership(), poids.getPoidsMobilite()});

        return new ResultatMatching(candidat, scoreMatching,
                readinessPour(scoreMatching, parametre.getSeuilsReadiness()), detail);
    }

    /**
     * Matching d'un candidat sur un poste pour un trimestre.
     *
     * @throws RessourceIntrouvableException si le poste, les reglages ou le
     *                                       score du candidat sont absents
     */
    @Transactional(readOnly = true)
    public ResultatMatching evaluer(Employe candidat, String posteId, Trimestre trimestre) {
        Objects.requireNonNull(candidat, "candidat");
        Objects.requireNonNull(trimestre, "trimestre");

        Poste poste = chargerPoste(posteId);
        Score score = scoreRepository.findByEmployeAndTrimestre(candidat, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun score calcule pour " + candidat.getEmployeeId()
                                + " sur " + decrire(trimestre)));

        return evaluer(candidat, poste, score,
                potentielRepository.findByEmployeAndTrimestre(candidat, trimestre).orElse(null),
                employeeSkillRepository.findByEmployeAvecCompetence(candidat),
                calculService.chargerParametre(trimestre));
    }

    /**
     * Candidats du trimestre classes sur un poste, du meilleur matching au
     * moins bon.
     *
     * <p>Lecture seule : rien n'est ecrit. Le titulaire actuel du poste est
     * ecarte, il ne peut pas etre son propre successeur ; les employes hors
     * perimetre et les scores incomplets sont comptes et logues plutot que de
     * faire echouer le classement pour tout le monde.
     */
    @Transactional(readOnly = true)
    public List<ResultatMatching> classerCandidats(String posteId, Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        Poste poste = chargerPoste(posteId);
        Parametre parametre = calculService.chargerParametre(trimestre);

        List<Score> retenus = new ArrayList<>();
        int incomplets = 0;

        for (Score score : scoreRepository.findByTrimestreAvecEmploye(trimestre)) {
            Employe employe = score.getEmploye();
            if (!employe.estCalculable()
                    || employe.getEmployeeId().equals(poste.getTitulaireId())) {
                continue;
            }
            if (score.getScorePerformance() == null || score.getScorePotentiel() == null) {
                incomplets++;
                continue;
            }
            retenus.add(score);
        }

        Map<String, Potentiel> potentielParEmploye = indexerPotentiels(trimestre);
        Map<String, List<EmployeeSkill>> competencesParEmploye = indexerCompetences(retenus);

        List<ResultatMatching> classement = new ArrayList<>();
        for (Score score : retenus) {
            Employe candidat = score.getEmploye();
            String employeeId = candidat.getEmployeeId();
            classement.add(evaluer(candidat, poste, score,
                    potentielParEmploye.get(employeeId),
                    competencesParEmploye.getOrDefault(employeeId, List.of()),
                    parametre));
        }

        // A egalite de score, l'employeeId departage : sans cela deux appels
        // successifs pourraient rendre le meme classement dans un autre ordre.
        classement.sort(Comparator
                .comparing(ResultatMatching::scoreMatching, Comparator.reverseOrder())
                .thenComparing(resultat -> resultat.candidat().getEmployeeId()));

        if (incomplets > 0) {
            log.warn("Succession {} {} : {} score(s) incomplet(s) ecarte(s)",
                    poste.getPosteId(), decrire(trimestre), incomplets);
        }
        log.info("Succession {} {} : {} candidat(s) classe(s)",
                poste.getPosteId(), decrire(trimestre), classement.size());

        return classement;
    }

    /** Les {@code limite} meilleurs candidats, pour une short-list de comite. */
    @Transactional(readOnly = true)
    public List<ResultatMatching> classerCandidats(String posteId, Trimestre trimestre, int limite) {
        if (limite < 0) {
            throw new IllegalArgumentException("La limite ne peut pas etre negative");
        }
        List<ResultatMatching> classement = classerCandidats(posteId, trimestre);
        return classement.size() <= limite ? classement : List.copyOf(classement.subList(0, limite));
    }

    /**
     * Moyenne ponderee des criteres evaluables, ramenee sur 100. La division se
     * fait par la somme des poids effectivement retenus : un critere absent est
     * neutre, il ne tire pas le score vers le bas.
     */
    private BigDecimal moyennePonderee(String[] noms, BigDecimal[] sousScores, BigDecimal[] poids) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal sommePoids = BigDecimal.ZERO;

        for (int i = 0; i < sousScores.length; i++) {
            if (poids[i] == null) {
                throw new DonneesIncompletesException(
                        "Poids manquant (" + noms[i] + ") pour le calcul du matching");
            }
            if (sousScores[i] == null) {
                continue;
            }
            total = total.add(sousScores[i].multiply(poids[i]));
            sommePoids = sommePoids.add(poids[i]);
        }

        if (sommePoids.signum() == 0) {
            throw new DonneesIncompletesException(
                    "Aucun critere evaluable, le score de matching est indefini");
        }

        return total.divide(sommePoids, CalculService.PRECISION_SCORE, ARRONDI);
    }

    private Poste chargerPoste(String posteId) {
        Objects.requireNonNull(posteId, "posteId");
        return posteRepository.findByIdAvecCompetences(posteId)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun poste " + posteId));
    }

    private Map<String, Potentiel> indexerPotentiels(Trimestre trimestre) {
        Map<String, Potentiel> index = new HashMap<>();
        for (Potentiel potentiel : potentielRepository.findByTrimestreAvecEmploye(trimestre)) {
            index.put(potentiel.getEmploye().getEmployeeId(), potentiel);
        }
        return index;
    }

    private Map<String, List<EmployeeSkill>> indexerCompetences(List<Score> retenus) {
        if (retenus.isEmpty()) {
            return Map.of();
        }
        Set<String> employeeIds = new HashSet<>();
        for (Score score : retenus) {
            employeeIds.add(score.getEmploye().getEmployeeId());
        }

        Map<String, List<EmployeeSkill>> index = new HashMap<>();
        for (EmployeeSkill skill : employeeSkillRepository.findByEmployeIdsAvecCompetence(employeeIds)) {
            index.computeIfAbsent(skill.getEmploye().getEmployeeId(), cle -> new ArrayList<>())
                    .add(skill);
        }
        return index;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
