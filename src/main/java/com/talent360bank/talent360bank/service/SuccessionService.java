package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.BaremeCompetences;
import com.talent360bank.talent360bank.entity.BaremeExperience;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
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
    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final CalculService calculService;

    public SuccessionService(PosteRepository posteRepository,
                             ScoreRepository scoreRepository,
                             PotentielRepository potentielRepository,
                             CompetenceCollaborateurRepository competenceCollaborateurRepository,
                             CalculService calculService) {
        this.posteRepository = posteRepository;
        this.scoreRepository = scoreRepository;
        this.potentielRepository = potentielRepository;
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
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
     * libelle. Une competence exigee absente du profil du candidat est supposee
     * au niveau par defaut du bareme, comme dans 09_SUCCESSION ; un niveau
     * superieur a l'exigence ne rapporte pas de bonus : depasser l'attendu ne
     * compense pas un manque ailleurs.
     *
     * @return null si le poste n'exige aucune competence chiffree : le critere
     *         est alors non evaluable et compte pour zero dans le matching
     * @throws DonneesIncompletesException si le bareme n'est pas configure
     */
    public BigDecimal scoreCompetences(Poste poste, List<CompetenceCollaborateur> competencesCandidat,
                                       BaremeCompetences bareme) {
        Objects.requireNonNull(poste, "poste");
        if (bareme == null || bareme.getPointsParNiveauManquant() == null
                || bareme.getNiveauParDefaut() == null) {
            throw new DonneesIncompletesException("Le bareme des competences n'est pas configure");
        }

        Map<String, Integer> acquis = new HashMap<>();
        if (competencesCandidat != null) {
            for (CompetenceCollaborateur skill : competencesCandidat) {
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

            int actuel = acquis.getOrDefault(exigence.competence().getCompetenceId(),
                    bareme.getNiveauParDefaut());
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
     * dans 01_COLLABORATEURS : ROUND((date de reference - date d'entree) / 365.25, 1).
     * Les annees revolues de {@link Collaborateur#getAnciennete(LocalDate)}
     * feraient perdre jusqu'a une annee de points.
     *
     * @param dateReference date de reference du trimestre
     *                      ({@link Trimestre#getDateReference()}), jamais la date
     *                      du jour : le resultat ne depend pas du jour du calcul
     * @return null si la date d'entree est inconnue
     * @throws DonneesIncompletesException si le bareme n'est pas configure
     */
    public BigDecimal scoreExperience(Collaborateur candidat, BaremeExperience bareme, LocalDate dateReference) {
        Objects.requireNonNull(candidat, "candidat");
        Objects.requireNonNull(dateReference, "dateReference");
        if (bareme == null || bareme.getPointsParAnnee() == null || bareme.getPlafond() == null) {
            throw new DonneesIncompletesException("Le bareme d'experience n'est pas configure");
        }

        BigDecimal anciennete = ancienneteEnAnnees(candidat, dateReference);
        if (anciennete == null) {
            return null;
        }
        if (anciennete.signum() <= 0) {
            return BigDecimal.ZERO.setScale(CalculService.PRECISION_SCORE, ARRONDI);
        }
        return anciennete
                .multiply(bareme.getPointsParAnnee())
                .min(bareme.getPlafond())
                .setScale(CalculService.PRECISION_SCORE, ARRONDI);
    }

    /**
     * Anciennete en annees decimales arrondies au dixieme, comme la colonne G de
     * 01_COLLABORATEURS : ROUND((date de reference - date d'entree) / 365.25, 1).
     * Base du critere experience ; la fiche collaborateur l'affiche telle quelle.
     *
     * @param dateReference date de reference du trimestre, jamais la date du jour
     * @return null si la date d'entree est inconnue ; negative si l'entree suit la date de reference
     */
    public BigDecimal ancienneteEnAnnees(Collaborateur collaborateur, LocalDate dateReference) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(dateReference, "dateReference");
        if (collaborateur.getDateEntree() == null) {
            return null;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(collaborateur.getDateEntree(), dateReference))
                .divide(JOURS_PAR_AN, 1, ARRONDI);
    }

    /**
     * Matching d'un candidat sur un poste, sans acces base : pour classer un lot
     * avec des reglages et des donnees deja charges.
     *
     * <p>Un critere non evaluable (pas de {@link Potentiel}, pas de date
     * d'entree, poste sans competence chiffree) compte pour zero dans le score,
     * comme une cellule vide dans 09_SUCCESSION ; le detail le rend null pour
     * qu'on distingue un zero d'une donnee absente.
     *
     * @param dateReference date de reference du trimestre, pour l'experience
     */
    public ResultatMatching evaluer(Collaborateur candidat, Poste poste, Score score, Potentiel potentiel,
                                    List<CompetenceCollaborateur> competencesCandidat, Parametre parametre,
                                    LocalDate dateReference) {
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
                scoreExperience(candidat, parametre.getBaremeExperience(), dateReference),
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
    public ResultatMatching evaluer(Collaborateur candidat, String posteId, Trimestre trimestre) {
        Objects.requireNonNull(candidat, "candidat");
        Objects.requireNonNull(trimestre, "trimestre");

        Poste poste = chargerPoste(posteId);
        Score score = scoreRepository.findByCollaborateurAndTrimestre(candidat, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun score calcule pour " + candidat.getIdCollaborateur()
                                + " sur " + decrire(trimestre)));

        return evaluer(candidat, poste, score,
                potentielRepository.findByCollaborateurAndTrimestreAndSource(candidat, trimestre,
                        SourceEvaluation.MANAGER).orElse(null),
                competenceCollaborateurRepository.findByCollaborateurAvecCompetence(candidat),
                calculService.chargerParametre(trimestre), trimestre.getDateReference());
    }

    /**
     * Candidats du trimestre classes sur un poste, du meilleur matching au
     * moins bon.
     *
     * <p>Lecture seule : rien n'est ecrit. Le titulaire actuel du poste est
     * ecarte, il ne peut pas etre son propre successeur ; les collaborateurs hors
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

        for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            Collaborateur collaborateur = score.getCollaborateur();
            if (!collaborateur.estCalculable()
                    || collaborateur.getIdCollaborateur().equals(poste.getTitulaireId())) {
                continue;
            }
            if (score.getScorePerformance() == null || score.getScorePotentiel() == null) {
                incomplets++;
                continue;
            }
            retenus.add(score);
        }

        Map<String, Potentiel> potentielParCollaborateur = indexerPotentiels(trimestre);
        Map<String, List<CompetenceCollaborateur>> competencesParCollaborateur = indexerCompetences(retenus);

        List<ResultatMatching> classement = new ArrayList<>();
        for (Score score : retenus) {
            Collaborateur candidat = score.getCollaborateur();
            String idCollaborateur = candidat.getIdCollaborateur();
            classement.add(evaluer(candidat, poste, score,
                    potentielParCollaborateur.get(idCollaborateur),
                    competencesParCollaborateur.getOrDefault(idCollaborateur, List.of()),
                    parametre, trimestre.getDateReference()));
        }

        // A egalite de score, l'idCollaborateur departage : sans cela deux appels
        // successifs pourraient rendre le meme classement dans un autre ordre.
        classement.sort(Comparator
                .comparing(ResultatMatching::scoreMatching, Comparator.reverseOrder())
                .thenComparing(resultat -> resultat.candidat().getIdCollaborateur()));

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
     * Moyenne ponderee des six criteres, ramenee sur 100. Un critere absent
     * compte pour zero et garde son poids, comme dans 09_SUCCESSION : son poids
     * n'est pas redistribue sur les autres.
     */
    private BigDecimal moyennePonderee(String[] noms, BigDecimal[] sousScores, BigDecimal[] poids) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal sommePoids = BigDecimal.ZERO;

        for (int i = 0; i < sousScores.length; i++) {
            if (poids[i] == null) {
                throw new DonneesIncompletesException(
                        "Poids manquant (" + noms[i] + ") pour le calcul du matching");
            }
            if (sousScores[i] != null) {
                total = total.add(sousScores[i].multiply(poids[i]));
            }
            sommePoids = sommePoids.add(poids[i]);
        }

        if (sommePoids.signum() == 0) {
            throw new DonneesIncompletesException(
                    "Poids du matching tous nuls, le score de matching est indefini");
        }

        return total.divide(sommePoids, CalculService.PRECISION_SCORE, ARRONDI);
    }

    private Poste chargerPoste(String posteId) {
        Objects.requireNonNull(posteId, "posteId");
        return posteRepository.findByIdAvecCompetences(posteId)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun poste " + posteId));
    }

    /** Notes de potentiel de l'evaluation du manager : leadership et mobilite du matching. */
    private Map<String, Potentiel> indexerPotentiels(Trimestre trimestre) {
        Map<String, Potentiel> index = new HashMap<>();
        for (Potentiel potentiel
                : potentielRepository.findByTrimestreAvecCollaborateur(trimestre, SourceEvaluation.MANAGER)) {
            index.put(potentiel.getCollaborateur().getIdCollaborateur(), potentiel);
        }
        return index;
    }

    private Map<String, List<CompetenceCollaborateur>> indexerCompetences(List<Score> retenus) {
        if (retenus.isEmpty()) {
            return Map.of();
        }
        Set<String> idsCollaborateurs = new HashSet<>();
        for (Score score : retenus) {
            idsCollaborateurs.add(score.getCollaborateur().getIdCollaborateur());
        }

        Map<String, List<CompetenceCollaborateur>> index = new HashMap<>();
        for (CompetenceCollaborateur skill
                : competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(idsCollaborateurs)) {
            index.computeIfAbsent(skill.getCollaborateur().getIdCollaborateur(), cle -> new ArrayList<>())
                    .add(skill);
        }
        return index;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
