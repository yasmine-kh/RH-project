package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PointsVigilance;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsVigilance;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Indice de vigilance : le risque qu'un employe quitte la banque, note de 0 a
 * 100 par addition des points des signaux declenches, puis classe en FAIBLE,
 * MODEREE ou ELEVEE.
 *
 * <p>Sept signaux, dont les points viennent du {@link PointsVigilance} du
 * {@link Parametre} du trimestre (25 / 20 / 15 / 15 / 10 / 10 / 5 par defaut)
 * et les seuils du {@link SeuilsVigilance} : 30 et 60 pour le classement, 60
 * en dessous duquel l'engagement est juge faible. Comme ailleurs dans le
 * moteur, aucun bareme n'est ecrit ici.
 *
 * <p><strong>Origine des signaux.</strong> ENGAGEMENT_FAIBLE vient du score
 * du QuestionnaireEngagement compare au seuil. Les cinq signaux de mobilite,
 * developpement, reconnaissance et formation viennent des faits importes
 * ({@link FaitsVigilanceSource}, 12_VIGILANCE F a K). BAISSE_PERFORMANCE se
 * mesure sur l'historique des Score quand l'employe a un score au trimestre
 * precedent ; sinon, c'est le drapeau importe qui decide. Sans source de faits
 * declaree, seuls l'engagement et la baisse mesuree peuvent etre leves.
 *
 * <p>Service de lecture seule : il calcule et classe, il n'ecrit rien. La
 * creation des Alerte a partir des niveaux ELEVEE n'est pas faite ici.
 */
@Service
public class VigilanceService {

    private static final Logger log = LoggerFactory.getLogger(VigilanceService.class);

    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;

    /** Signaux leves uniquement par les faits importes. */
    private static final Set<SignalVigilance> SIGNAUX_IMPORTES = EnumSet.of(
            SignalVigilance.SANS_MOBILITE_4_ANS,
            SignalVigilance.MOBILITE_NON_TRAITEE,
            SignalVigilance.SANS_DEVELOPPEMENT_RECENT,
            SignalVigilance.FAIBLE_RECONNAISSANCE,
            SignalVigilance.FORMATION_NON_FAITE);

    private final ScoreRepository scoreRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final TrimestreRepository trimestreRepository;
    private final CalculService calculService;
    private final FaitsVigilanceSource faitsVigilanceSource;

    /**
     * Constructeur de Spring. La source des faits est optionnelle : tant que
     * l'import ne la fournit pas, les signaux importes ne sont jamais leves.
     */
    @Autowired
    public VigilanceService(ScoreRepository scoreRepository,
                            QuestionnaireEngagementRepository questionnaireRepository,
                            TrimestreRepository trimestreRepository,
                            CalculService calculService,
                            ObjectProvider<FaitsVigilanceSource> faitsVigilanceSource) {
        this(scoreRepository, questionnaireRepository, trimestreRepository, calculService,
                faitsVigilanceSource.getIfAvailable(() -> {
                    log.warn("Aucune source de faits de vigilance : mobilite, developpement, "
                            + "reconnaissance et formation ne seront pas leves tant que l'import "
                            + "ne les fournit pas");
                    return trimestre -> Map.of();
                }));
    }

    public VigilanceService(ScoreRepository scoreRepository,
                            QuestionnaireEngagementRepository questionnaireRepository,
                            TrimestreRepository trimestreRepository,
                            CalculService calculService,
                            FaitsVigilanceSource faitsVigilanceSource) {
        this.scoreRepository = scoreRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.trimestreRepository = trimestreRepository;
        this.calculService = calculService;
        this.faitsVigilanceSource = Objects.requireNonNull(faitsVigilanceSource, "faitsVigilanceSource");
    }

    /**
     * Somme des points des signaux declenches. Un ensemble vide vaut zero :
     * aucun signal n'est un resultat valide, pas une donnee manquante.
     *
     * @throws DonneesIncompletesException si les points d'un signal declenche
     *                                     ne sont pas configures
     */
    public BigDecimal calculerIndice(Set<SignalVigilance> signaux, PointsVigilance points) {
        Objects.requireNonNull(signaux, "signaux");
        if (points == null) {
            throw new DonneesIncompletesException("Les points de vigilance ne sont pas configures");
        }

        BigDecimal indice = BigDecimal.ZERO;
        for (SignalVigilance signal : signaux) {
            BigDecimal point = signal.pointsDans(points);
            if (point == null) {
                throw new DonneesIncompletesException(
                        "Points manquants pour le signal " + signal + ", indice indefini");
            }
            indice = indice.add(point);
        }
        return indice.setScale(CalculService.PRECISION_SCORE, ARRONDI);
    }

    /**
     * Niveau de vigilance d'un indice. Bornes basses inclusives, hautes
     * exclusives : sous le seuil modere c'est FAIBLE, sous le seuil eleve
     * c'est MODEREE, au seuil eleve et au-dela c'est ELEVEE.
     *
     * @throws DonneesIncompletesException si l'indice ou les seuils manquent
     */
    public NiveauVigilance niveauPour(BigDecimal indice, SeuilsVigilance seuils) {
        Objects.requireNonNull(seuils, "seuils");
        if (indice == null) {
            throw new DonneesIncompletesException("Indice absent, niveau de vigilance indeterminable");
        }
        if (seuils.getSeuilModere() == null || seuils.getSeuilEleve() == null) {
            throw new DonneesIncompletesException("Les seuils de vigilance ne sont pas configures");
        }
        if (indice.compareTo(seuils.getSeuilModere()) < 0) {
            return NiveauVigilance.FAIBLE;
        }
        if (indice.compareTo(seuils.getSeuilEleve()) < 0) {
            return NiveauVigilance.MODEREE;
        }
        return NiveauVigilance.ELEVEE;
    }

    /**
     * Signaux reconnaissables a partir des scores et du questionnaire seuls,
     * sans faits importes.
     *
     * @throws DonneesIncompletesException si un questionnaire est exploitable
     *                                     mais que le seuil n'est pas configure
     */
    public Set<SignalVigilance> detecterSignaux(Score scoreCourant, Score scorePrecedent,
                                                QuestionnaireEngagement engagement,
                                                SeuilsVigilance seuils) {
        return detecterSignaux(scoreCourant, scorePrecedent, engagement, FaitsVigilance.AUCUN, seuils);
    }

    /**
     * Signaux reconnaissables a partir des donnees deja chargees.
     *
     * <p>Une donnee absente ne leve pas de signal : sans questionnaire, on ne
     * sait pas si l'engagement est faible, on ne peut pas le supposer ; un fait
     * importe inconnu (null) vaut non. Le seuil d'engagement est une borne
     * basse exclusive, comme les seuils de l'indice : un score egal au seuil ne
     * declenche pas le signal.
     *
     * <p>Baisse de performance : si les deux scores sont connus, l'historique
     * decide seul, drapeau importe compris ; seul un recul strict compte, la
     * specification ne fixant pas d'amplitude minimale. Sans score precedent
     * comparable, c'est le drapeau importe qui decide.
     *
     * @param faits faits importes de l'employe, {@link FaitsVigilance#AUCUN} si inconnus
     * @throws DonneesIncompletesException si un questionnaire est exploitable
     *                                     mais que le seuil n'est pas configure
     */
    public Set<SignalVigilance> detecterSignaux(Score scoreCourant, Score scorePrecedent,
                                                QuestionnaireEngagement engagement,
                                                FaitsVigilance faits,
                                                SeuilsVigilance seuils) {
        Objects.requireNonNull(seuils, "seuils");
        FaitsVigilance faitsConnus = faits == null ? FaitsVigilance.AUCUN : faits;
        Set<SignalVigilance> signaux = EnumSet.noneOf(SignalVigilance.class);

        if (engagement != null && engagement.getScoreEngagement() != null) {
            if (seuils.getSeuilEngagementFaible() == null) {
                throw new DonneesIncompletesException(
                        "Le seuil d'engagement faible n'est pas configure");
            }
            if (engagement.getScoreEngagement().compareTo(seuils.getSeuilEngagementFaible()) < 0) {
                signaux.add(SignalVigilance.ENGAGEMENT_FAIBLE);
            }
        }

        boolean historiqueComparable = scoreCourant != null && scorePrecedent != null
                && scoreCourant.getScorePerformance() != null
                && scorePrecedent.getScorePerformance() != null;
        boolean baisse = historiqueComparable
                ? scoreCourant.getScorePerformance().compareTo(scorePrecedent.getScorePerformance()) < 0
                : faitsConnus.estDeclare(SignalVigilance.BAISSE_PERFORMANCE);
        if (baisse) {
            signaux.add(SignalVigilance.BAISSE_PERFORMANCE);
        }

        for (SignalVigilance signal : SIGNAUX_IMPORTES) {
            if (faitsConnus.estDeclare(signal)) {
                signaux.add(signal);
            }
        }

        return signaux;
    }

    /**
     * Signaux d'un employe sur un trimestre, donnees chargees depuis la base.
     */
    @Transactional(readOnly = true)
    public Set<SignalVigilance> detecterSignaux(Employe employe, Trimestre trimestre) {
        return detecterSignaux(employe, trimestre,
                calculService.chargerParametre(trimestre).getSeuilsVigilance());
    }

    private Set<SignalVigilance> detecterSignaux(Employe employe, Trimestre trimestre,
                                                 SeuilsVigilance seuils) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        Score scoreCourant = scoreRepository.findByEmployeAndTrimestre(employe, trimestre)
                .orElse(null);
        Score scorePrecedent = trimestrePrecedent(trimestre)
                .flatMap(precedent -> scoreRepository.findByEmployeAndTrimestre(employe, precedent))
                .orElse(null);

        return detecterSignaux(scoreCourant, scorePrecedent,
                questionnaireRepository.findByEmployeAndTrimestre(employe, trimestre).orElse(null),
                faitsVigilanceSource.faits(employe.getEmployeeId(), trimestre),
                seuils);
    }

    /**
     * Indice et niveau a partir d'un jeu de signaux deja etabli, sans acces
     * base. Permet aussi de saisir des signaux a la main, hors import.
     */
    public ResultatVigilance evaluer(Employe employe, Set<SignalVigilance> signaux,
                                     Parametre parametre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(signaux, "signaux");
        Objects.requireNonNull(parametre, "parametre");

        BigDecimal indice = calculerIndice(signaux, parametre.getPointsVigilance());

        // Copie figee : le resultat ne doit pas bouger si l'appelant reutilise
        // son EnumSet pour l'employe suivant.
        return new ResultatVigilance(employe, indice,
                niveauPour(indice, parametre.getSeuilsVigilance()), Set.copyOf(signaux));
    }

    /**
     * Vigilance d'un employe sur un trimestre.
     *
     * @throws RessourceIntrouvableException si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public ResultatVigilance evaluer(Employe employe, Trimestre trimestre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        // Les reglages sont charges une fois et servent a la detection comme au
        // classement : les relire pour chaque etape ferait deux requetes.
        Parametre parametre = calculService.chargerParametre(trimestre);

        return evaluer(employe,
                detecterSignaux(employe, trimestre, parametre.getSeuilsVigilance()), parametre);
    }

    /**
     * Vigilance de tous les employes scores du trimestre, du plus a risque au
     * moins a risque.
     *
     * <p>Lecture seule : rien n'est ecrit. Le perimetre est celui des employes
     * ayant un Score sur le trimestre, comme pour le placement 9-box et la
     * detection des talents ; les employes hors perimetre de calcul sont
     * comptes et logues plutot qu'ecartes en silence.
     */
    @Transactional(readOnly = true)
    public List<ResultatVigilance> evaluerTrimestre(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);
        Map<String, Score> scoresPrecedents = indexerScoresPrecedents(trimestre);
        Map<String, QuestionnaireEngagement> engagements = indexerEngagements(trimestre);
        Map<String, FaitsVigilance> faits = faitsVigilanceSource.faitsDuTrimestre(trimestre);
        if (faits == null) {
            faits = Map.of();
        }

        List<ResultatVigilance> resultats = new ArrayList<>();
        int horsPerimetre = 0;

        for (Score score : scoreRepository.findByTrimestreAvecEmploye(trimestre)) {
            Employe employe = score.getEmploye();
            if (!employe.estCalculable()) {
                horsPerimetre++;
                continue;
            }
            String employeeId = employe.getEmployeeId();
            resultats.add(evaluer(employe,
                    detecterSignaux(score, scoresPrecedents.get(employeeId),
                            engagements.get(employeeId),
                            faits.getOrDefault(employeeId, FaitsVigilance.AUCUN),
                            parametre.getSeuilsVigilance()),
                    parametre));
        }

        // A egalite d'indice, l'employeeId departage : sans cela deux appels
        // successifs pourraient rendre la meme liste dans un autre ordre.
        resultats.sort(Comparator
                .comparing(ResultatVigilance::indice, Comparator.reverseOrder())
                .thenComparing(resultat -> resultat.employe().getEmployeeId()));

        if (horsPerimetre > 0) {
            log.warn("Vigilance {} : {} employe(s) hors perimetre de calcul ecarte(s)",
                    decrire(trimestre), horsPerimetre);
        }
        log.info("Vigilance {} : {} employe(s) evalue(s), dont {} a risque",
                decrire(trimestre), resultats.size(),
                resultats.stream().filter(ResultatVigilance::estARisque).count());

        return resultats;
    }

    /** Employes dont la vigilance atteint au moins le niveau demande. */
    @Transactional(readOnly = true)
    public List<ResultatVigilance> evaluerTrimestre(Trimestre trimestre, NiveauVigilance minimum) {
        Objects.requireNonNull(minimum, "minimum");

        List<ResultatVigilance> retenus = new ArrayList<>();
        for (ResultatVigilance resultat : evaluerTrimestre(trimestre)) {
            if (resultat.niveau().compareTo(minimum) >= 0) {
                retenus.add(resultat);
            }
        }
        return retenus;
    }

    private Optional<Trimestre> trimestrePrecedent(Trimestre trimestre) {
        List<Trimestre> precedents = trimestreRepository.findPrecedents(
                trimestre.getAnnee(), trimestre.getNumero(), PageRequest.of(0, 1));
        return precedents.isEmpty() ? Optional.empty() : Optional.of(precedents.get(0));
    }

    private Map<String, Score> indexerScoresPrecedents(Trimestre trimestre) {
        Optional<Trimestre> precedent = trimestrePrecedent(trimestre);
        if (precedent.isEmpty()) {
            return Map.of();
        }
        Map<String, Score> index = new HashMap<>();
        for (Score score : scoreRepository.findByTrimestreAvecEmploye(precedent.get())) {
            index.put(score.getEmploye().getEmployeeId(), score);
        }
        return index;
    }

    private Map<String, QuestionnaireEngagement> indexerEngagements(Trimestre trimestre) {
        Map<String, QuestionnaireEngagement> index = new HashMap<>();
        for (QuestionnaireEngagement engagement
                : questionnaireRepository.findByTrimestreAvecEmploye(trimestre)) {
            index.put(engagement.getEmploye().getEmployeeId(), engagement);
        }
        return index;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
