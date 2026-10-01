package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsCouverture;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Couverture des postes critiques par leurs successeurs identifies et alertes
 * sur les postes insuffisamment couverts (08_POSTES_CRITIQUES et le bloc
 * "postes sans successeur" de 00_DASHBOARD).
 *
 * <p>Un poste est critique quand son indicateur Poste critique vaut Oui ; sa
 * criticite (Tres elevee, Elevee...) n'entre pas dans le calcul, le classeur
 * ne l'utilise pas non plus. La couverture se juge sur les successeurs que le
 * RH a identifies ({@link SuccesseurIdentifieSource}), pas sur le classement de
 * tous les collaborateurs : ce classement propose toujours quelqu'un et ne
 * declencherait jamais d'alerte.
 *
 * <p>Les seuils viennent du {@link Parametre} du trimestre : le minimum de
 * successeurs de {@link SeuilsCouverture}, puis les seuils Ready Now et
 * Ready &lt; 1 an de {@link SeuilsReadiness} appliques au meilleur matching.
 *
 * <p>Service de lecture seule : les alertes sont recalculees a chaque appel,
 * rien n'est persiste.
 */
@Service
public class PosteCritiqueService {

    private static final Logger log = LoggerFactory.getLogger(PosteCritiqueService.class);

    private static final String OUI = "Oui";
    private static final BigDecimal CENT = new BigDecimal("100");

    private final PosteRepository posteRepository;
    private final CollaborateurRepository collaborateurRepository;
    private final ScoreRepository scoreRepository;
    private final PotentielRepository potentielRepository;
    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final SuccessionService successionService;
    private final CalculService calculService;
    private final SuccesseurIdentifieSource successeurIdentifieSource;

    /**
     * Constructeur de Spring. La source des successeurs est optionnelle : tant
     * que l'import ne la fournit pas, aucun successeur n'est identifie.
     */
    @Autowired
    public PosteCritiqueService(PosteRepository posteRepository,
                                CollaborateurRepository collaborateurRepository,
                                ScoreRepository scoreRepository,
                                PotentielRepository potentielRepository,
                                CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                SuccessionService successionService,
                                CalculService calculService,
                                ObjectProvider<SuccesseurIdentifieSource> successeurIdentifieSource) {
        this(posteRepository, collaborateurRepository, scoreRepository, potentielRepository,
                competenceCollaborateurRepository, successionService, calculService,
                successeurIdentifieSource.getIfAvailable(() -> {
                    log.warn("Aucune source de successeurs identifies : tous les postes critiques "
                            + "seront en alerte tant que l'import ne les fournit pas");
                    return posteId -> List.of();
                }));
    }

    public PosteCritiqueService(PosteRepository posteRepository,
                                CollaborateurRepository collaborateurRepository,
                                ScoreRepository scoreRepository,
                                PotentielRepository potentielRepository,
                                CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                SuccessionService successionService,
                                CalculService calculService,
                                SuccesseurIdentifieSource successeurIdentifieSource) {
        this.posteRepository = posteRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.scoreRepository = scoreRepository;
        this.potentielRepository = potentielRepository;
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
        this.successionService = successionService;
        this.calculService = calculService;
        this.successeurIdentifieSource = Objects.requireNonNull(successeurIdentifieSource,
                "successeurIdentifieSource");
    }

    /**
     * Niveau de couverture d'un poste, comme la colonne Couverture de
     * 08_POSTES_CRITIQUES. Bornes inclusives, comme pour la readiness.
     *
     * @param meilleurMatching null si aucun successeur n'a pu etre evalue : la
     *                         couverture est alors partielle, faute de preuve
     * @throws DonneesIncompletesException si les seuils manquent
     */
    public NiveauCouverture couverturePour(int nbSuccesseurs, BigDecimal meilleurMatching, Parametre parametre) {
        Objects.requireNonNull(parametre, "parametre");
        SeuilsCouverture seuilsCouverture = parametre.getSeuilsCouverture();
        if (seuilsCouverture == null || seuilsCouverture.getNbMinSuccesseurs() == null) {
            throw new DonneesIncompletesException("Le seuil de couverture des postes critiques n'est pas configure");
        }
        SeuilsReadiness seuils = parametre.getSeuilsReadiness();
        if (seuils == null || seuils.getSeuilReadyNow() == null || seuils.getSeuilMoins1An() == null) {
            throw new DonneesIncompletesException("Les seuils de readiness ne sont pas configures");
        }

        if (nbSuccesseurs < seuilsCouverture.getNbMinSuccesseurs()) {
            return NiveauCouverture.ALERTE;
        }
        if (meilleurMatching == null) {
            return NiveauCouverture.PARTIELLE;
        }
        if (meilleurMatching.compareTo(seuils.getSeuilReadyNow()) >= 0) {
            return NiveauCouverture.READY_NOW;
        }
        if (meilleurMatching.compareTo(seuils.getSeuilMoins1An()) >= 0) {
            return NiveauCouverture.MOINS_1_AN;
        }
        return NiveauCouverture.PARTIELLE;
    }

    /**
     * Couverture d'un poste, sans acces base : pour evaluer un lot avec des
     * donnees deja chargees.
     *
     * <p>Un successeur identifie est ecarte s'il est le titulaire, inconnu ou
     * hors perimetre (non actif) : il ne couvre pas le poste. Un successeur
     * actif sans score complet ce trimestre compte dans le nombre de
     * successeurs, comme une ligne de 09_SUCCESSION, mais pas dans le
     * meilleur matching.
     *
     * @param collaborateurs     collaborateurs connus, par Employee_ID
     * @param scores       scores du trimestre, par Employee_ID
     * @param potentiels   notes de potentiel du trimestre, par Employee_ID
     * @param competences  competences des collaborateurs, par Employee_ID
     * @param dateReference date de reference du trimestre, pour l'experience du matching
     */
    public CouverturePoste evaluerCouverture(Poste poste, List<String> successeursIdentifies,
                                             Map<String, Collaborateur> collaborateurs, Map<String, Score> scores,
                                             Map<String, Potentiel> potentiels,
                                             Map<String, List<CompetenceCollaborateur>> competences,
                                             Parametre parametre, LocalDate dateReference) {
        Objects.requireNonNull(poste, "poste");
        Objects.requireNonNull(successeursIdentifies, "successeursIdentifies");
        Objects.requireNonNull(parametre, "parametre");

        List<ResultatMatching> successeurs = new ArrayList<>();
        List<CouverturePoste.SuccesseurIgnore> ignores = new ArrayList<>();
        int nbSuccesseurs = 0;

        for (String idCollaborateur : new LinkedHashSet<>(successeursIdentifies)) {
            Collaborateur collaborateur = collaborateurs.get(idCollaborateur);
            if (idCollaborateur.equals(poste.getTitulaireId())) {
                ignores.add(new CouverturePoste.SuccesseurIgnore(idCollaborateur, "Titulaire du poste"));
                continue;
            }
            if (collaborateur == null) {
                ignores.add(new CouverturePoste.SuccesseurIgnore(idCollaborateur, "Collaborateur inconnu"));
                continue;
            }
            if (!collaborateur.estCalculable()) {
                ignores.add(new CouverturePoste.SuccesseurIgnore(idCollaborateur,
                        "Hors perimetre (statut " + collaborateur.getStatut() + ")"));
                continue;
            }

            nbSuccesseurs++;
            Score score = scores.get(idCollaborateur);
            if (score == null || score.getScorePerformance() == null || score.getScorePotentiel() == null) {
                ignores.add(new CouverturePoste.SuccesseurIgnore(idCollaborateur,
                        "Score absent ou incomplet sur le trimestre"));
                continue;
            }
            successeurs.add(successionService.evaluer(collaborateur, poste, score,
                    potentiels.get(idCollaborateur), competences.getOrDefault(idCollaborateur, List.of()), parametre,
                    dateReference));
        }

        // Meme ordre que le classement des candidats : l'idCollaborateur departage.
        successeurs.sort(Comparator
                .comparing(ResultatMatching::scoreMatching, Comparator.reverseOrder())
                .thenComparing(resultat -> resultat.candidat().getIdCollaborateur()));

        BigDecimal meilleur = successeurs.isEmpty() ? null : successeurs.get(0).scoreMatching();
        return new CouverturePoste(poste, nbSuccesseurs, List.copyOf(successeurs), List.copyOf(ignores),
                couverturePour(nbSuccesseurs, meilleur, parametre));
    }

    /**
     * Part des postes critiques hors alerte, sur 100, comme le taux de
     * couverture de 00_DASHBOARD (qui l'arrondit a l'unite).
     *
     * @return null s'il n'y a aucun poste critique : le taux est indefini
     */
    public BigDecimal tauxCouverture(List<CouverturePoste> couvertures) {
        Objects.requireNonNull(couvertures, "couvertures");
        if (couvertures.isEmpty()) {
            return null;
        }
        long couverts = couvertures.stream().filter(couverture -> !couverture.estEnAlerte()).count();
        return BigDecimal.valueOf(couverts).multiply(CENT)
                .divide(BigDecimal.valueOf(couvertures.size()), CalculService.PRECISION_SCORE, RoundingMode.HALF_UP);
    }

    public static boolean estCritique(Poste poste) {
        return poste.getPosteCritique() != null && OUI.equalsIgnoreCase(poste.getPosteCritique().trim());
    }

    /** Couverture d'un poste critique sur un trimestre. */
    @Transactional(readOnly = true)
    public CouverturePoste evaluerCouverture(String posteId, Trimestre trimestre) {
        Objects.requireNonNull(posteId, "posteId");
        Poste poste = posteRepository.findByIdAvecCompetences(posteId)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun poste " + posteId));
        if (!estCritique(poste)) {
            throw new RessourceIntrouvableException("Le poste " + posteId + " n'est pas un poste critique");
        }
        return evaluer(List.of(poste), trimestre).get(0);
    }

    /** Couverture de tous les postes critiques, par Poste_ID croissant. */
    @Transactional(readOnly = true)
    public List<CouverturePoste> listerPostesCritiques(Trimestre trimestre) {
        List<Poste> critiques = posteRepository.findAll().stream()
                .filter(PosteCritiqueService::estCritique)
                .sorted(Comparator.comparing(Poste::getPosteId))
                .toList();
        return evaluer(critiques, trimestre);
    }

    /** Postes critiques en alerte : moins de successeurs identifies que le minimum. */
    @Transactional(readOnly = true)
    public List<CouverturePoste> detecterAlertes(Trimestre trimestre) {
        List<CouverturePoste> alertes = listerPostesCritiques(trimestre).stream()
                .filter(CouverturePoste::estEnAlerte)
                .toList();
        log.info("Postes critiques {} : {} alerte(s)", decrire(trimestre), alertes.size());
        return alertes;
    }

    private List<CouverturePoste> evaluer(List<Poste> postes, Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        if (postes.isEmpty()) {
            return List.of();
        }
        Parametre parametre = calculService.chargerParametre(trimestre);

        Map<String, List<String>> successeursParPoste = new HashMap<>();
        Set<String> tousSuccesseurs = new LinkedHashSet<>();
        for (Poste poste : postes) {
            List<String> identifies = successeurIdentifieSource.successeursIdentifies(poste.getPosteId());
            successeursParPoste.put(poste.getPosteId(), identifies);
            tousSuccesseurs.addAll(identifies);
        }

        Map<String, Collaborateur> collaborateurs = new HashMap<>();
        Map<String, List<CompetenceCollaborateur>> competences = new HashMap<>();
        if (!tousSuccesseurs.isEmpty()) {
            for (Collaborateur collaborateur : collaborateurRepository.findAllById(tousSuccesseurs)) {
                collaborateurs.put(collaborateur.getIdCollaborateur(), collaborateur);
            }
            for (CompetenceCollaborateur skill
                    : competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(tousSuccesseurs)) {
                competences.computeIfAbsent(skill.getCollaborateur().getIdCollaborateur(), cle -> new ArrayList<>())
                        .add(skill);
            }
        }

        Map<String, Score> scores = new HashMap<>();
        for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            scores.put(score.getCollaborateur().getIdCollaborateur(), score);
        }
        Map<String, Potentiel> potentiels = new HashMap<>();
        for (Potentiel potentiel : potentielRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            potentiels.put(potentiel.getCollaborateur().getIdCollaborateur(), potentiel);
        }

        List<CouverturePoste> couvertures = new ArrayList<>();
        for (Poste poste : postes) {
            CouverturePoste couverture = evaluerCouverture(poste, successeursParPoste.get(poste.getPosteId()),
                    collaborateurs, scores, potentiels, competences, parametre, trimestre.getDateReference());
            if (!couverture.ignores().isEmpty()) {
                log.warn("Poste critique {} {} : {} successeur(s) identifie(s) hors matching {}",
                        poste.getPosteId(), decrire(trimestre), couverture.ignores().size(), couverture.ignores());
            }
            couvertures.add(couverture);
        }
        return couvertures;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
