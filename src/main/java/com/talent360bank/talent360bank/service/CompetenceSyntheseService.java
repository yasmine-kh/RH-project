package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.EcartExigence;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.CompteNiveau;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Criteres;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Exigence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.LigneCompetence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.PosteCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier.GapFrequent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module Competences : par competence du referentiel, niveaux moyens, gap
 * moyen et repartition sur une population ; les plus grands gaps ; et pour
 * chaque poste critique, ce qui manque a ses successeurs.
 *
 * <p><strong>Niveau cible.</strong> Celui de 06_EMPLOYEE_SKILLS (F), propre a
 * chaque collaborateur, comme le classeur : gap = cible - actuel (06!G) et
 * statut avec le seuil du trimestre (06!H), par
 * {@link CompetenceCollaborateurService#evaluer}. Les exigences des postes
 * (07_POSTES) ne servent qu'au bloc des postes critiques, par
 * {@link SuccessionService#ecartsExigences} et {@link PlusGrandGap}, comme
 * 09_SUCCESSION. Aucune formule nouvelle : des comptes et des moyennes.
 *
 * <p>Population : les collaborateurs actifs, comme 00_DASHBOARD G10
 * ({@link CompetenceCollaborateurService#compterGapsPrioritaires}). Les
 * competences ne dependent pas du trimestre ; le trimestre donne le seuil de
 * Prioritaire, les viviers et les successeurs.
 *
 * <p>Lecture seule, un nombre fixe de requetes quelle que soit la population.
 */
@Service
public class CompetenceSyntheseService {

    private static final int PRECISION_MOYENNE = 2;
    private static final int PRECISION_POURCENTAGE = 1;
    private static final int TOP_MAX = 50;
    private static final BigDecimal CENT = new BigDecimal("100");

    private final CollaborateurRepository collaborateurRepository;
    private final CompetenceRepository competenceRepository;
    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final CompetenceCollaborateurService competenceCollaborateurService;
    private final CalculService calculService;
    private final SuccessionService successionService;
    private final PosteCritiqueService posteCritiqueService;
    private final VivierThematiqueService vivierThematiqueService;
    private final TalentService talentService;

    public CompetenceSyntheseService(CollaborateurRepository collaborateurRepository,
                                     CompetenceRepository competenceRepository,
                                     CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                     CompetenceCollaborateurService competenceCollaborateurService,
                                     CalculService calculService, SuccessionService successionService,
                                     PosteCritiqueService posteCritiqueService,
                                     VivierThematiqueService vivierThematiqueService, TalentService talentService) {
        this.collaborateurRepository = collaborateurRepository;
        this.competenceRepository = competenceRepository;
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
        this.competenceCollaborateurService = competenceCollaborateurService;
        this.calculService = calculService;
        this.successionService = successionService;
        this.posteCritiqueService = posteCritiqueService;
        this.vivierThematiqueService = vivierThematiqueService;
        this.talentService = talentService;
    }

    /**
     * @throws IllegalArgumentException si un critere est invalide (vivier ou poste critique inconnu, top hors bornes)
     * @throws com.talent360bank.talent360bank.exception.RessourceIntrouvableException
     *         si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public SyntheseCompetences synthese(Trimestre trimestre, Criteres criteres) {
        Objects.requireNonNull(trimestre, "trimestre");
        verifier(criteres);
        Parametre parametre = calculService.chargerParametre(trimestre);
        List<CouverturePoste> couvertures = posteCritiqueService.listerPostesCritiques(trimestre);
        if (criteres.posteCritique() != null && couvertures.stream()
                .noneMatch(c -> c.poste().getPosteId().equals(criteres.posteCritique()))) {
            throw new IllegalArgumentException("Poste critique inconnu : " + criteres.posteCritique());
        }

        List<Collaborateur> actifs = collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF);
        Map<String, List<CompetenceCollaborateur>> competences = new HashMap<>();
        if (!actifs.isEmpty()) {
            for (CompetenceCollaborateur skill : competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(
                    actifs.stream().map(Collaborateur::getIdCollaborateur).toList())) {
                competences.computeIfAbsent(skill.getCollaborateur().getIdCollaborateur(), cle -> new ArrayList<>())
                        .add(skill);
            }
        }

        Set<String> population = population(actifs, trimestre, criteres, couvertures);
        List<GapCompetence> gaps = new ArrayList<>();
        for (String id : population) {
            for (CompetenceCollaborateur skill : competences.getOrDefault(id, List.of())) {
                gaps.add(competenceCollaborateurService.evaluer(skill, parametre.getSeuilsGapCompetence()));
            }
        }

        List<LigneCompetence> lignes = lignes(competenceRepository.findAll(), gaps, criteres.gapCroissant());
        List<LigneCompetence> top = lignes.stream()
                .filter(ligne -> ligne.gapMoyen() != null)
                .sorted(Comparator.comparing(LigneCompetence::gapMoyen, Comparator.reverseOrder())
                        .thenComparing(LigneCompetence::nbAvecGap, Comparator.reverseOrder())
                        .thenComparing(LigneCompetence::competenceId))
                .limit(criteres.top())
                .toList();
        int prioritaires = lignes.stream().mapToInt(LigneCompetence::nbPrioritaires).sum();

        List<PosteCompetences> postes = couvertures.stream()
                .filter(c -> criteres.posteCritique() == null
                        || c.poste().getPosteId().equals(criteres.posteCritique()))
                .map(c -> poste(c, competences, parametre))
                .toList();
        return new SyntheseCompetences(criteres, population.size(), lignes, top, prioritaires, postes);
    }

    /** Matricules des actifs qui passent les filtres (ET entre eux), dans l'ordre des matricules. */
    private Set<String> population(List<Collaborateur> actifs, Trimestre trimestre, Criteres criteres,
                                   List<CouverturePoste> couvertures) {
        Set<String> ids = actifs.stream()
                .filter(c -> criteres.entite() == null || dansLeSousArbre(c.getEntite(), criteres.entite()))
                .map(Collaborateur::getIdCollaborateur)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (criteres.vivier() != null) {
            ids.retainAll(membresDuVivier(trimestre, criteres.vivier()));
        }
        if (criteres.posteCritique() != null) {
            ids.retainAll(couvertures.stream()
                    .filter(c -> c.poste().getPosteId().equals(criteres.posteCritique()))
                    .flatMap(c -> c.successeurs().stream())
                    .map(s -> s.candidat().getIdCollaborateur())
                    .collect(Collectors.toSet()));
        }
        return ids;
    }

    private Set<String> membresDuVivier(Trimestre trimestre, String code) {
        if (VivierReleveService.CODE_VIVIER_RELEVE.equals(code)) {
            return talentService.getVivierReleve(trimestre).stream()
                    .map(m -> m.score().getCollaborateur().getIdCollaborateur()).collect(Collectors.toSet());
        }
        VivierThematique vivier = VivierThematique.valueOf(code);
        return vivierThematiqueService.getViviersThematiques(trimestre).membresDe(vivier).stream()
                .map(m -> m.score().getCollaborateur().getIdCollaborateur()).collect(Collectors.toSet());
    }

    private static boolean dansLeSousArbre(Entite entite, String code) {
        for (Entite e = entite; e != null; e = e.getParent()) {
            if (code.equals(e.getCode())) {
                return true;
            }
        }
        return false;
    }

    /** Une ligne par competence du referentiel, meme sans evalue, triee par gap moyen (inconnus en dernier). */
    static List<LigneCompetence> lignes(List<Competence> referentiel, List<GapCompetence> gaps, boolean croissant) {
        Map<String, List<GapCompetence>> parCompetence = gaps.stream()
                .filter(g -> g.gap() != null)
                .collect(Collectors.groupingBy(g -> g.competence().getCompetence().getCompetenceId()));
        Map<String, Competence> competences = new LinkedHashMap<>();
        referentiel.stream().sorted(Comparator.comparing(Competence::getCompetenceId))
                .forEach(c -> competences.put(c.getCompetenceId(), c));
        // Une competence evaluee mais absente du referentiel reste comptee.
        gaps.forEach(g -> competences.putIfAbsent(g.competence().getCompetence().getCompetenceId(),
                g.competence().getCompetence()));

        List<LigneCompetence> lignes = new ArrayList<>();
        for (Competence competence : competences.values()) {
            List<GapCompetence> evalues = parCompetence.getOrDefault(competence.getCompetenceId(), List.of());
            List<CompteNiveau> repartition = new ArrayList<>();
            for (int niveau = 1; niveau <= 5; niveau++) {
                int n = niveau;
                repartition.add(new CompteNiveau(niveau, (int) evalues.stream()
                        .filter(g -> g.competence().getNiveauActuel() == n).count()));
            }
            int avecGap = (int) evalues.stream()
                    .filter(g -> g.statut() != null && g.statut() != StatutGapCompetence.MAITRISE).count();
            lignes.add(new LigneCompetence(competence.getCompetenceId(), competence.getNom(),
                    competence.getCategorie(), evalues.size(),
                    moyenne(evalues, g -> g.competence().getNiveauActuel()),
                    moyenne(evalues, g -> g.competence().getNiveauCible()),
                    moyenne(evalues, GapCompetence::gap),
                    avecGap,
                    evalues.isEmpty() ? null : BigDecimal.valueOf(avecGap).multiply(CENT)
                            .divide(BigDecimal.valueOf(evalues.size()), PRECISION_POURCENTAGE, RoundingMode.HALF_UP),
                    (int) evalues.stream().filter(g -> g.statut() == StatutGapCompetence.PRIORITAIRE).count(),
                    List.copyOf(repartition)));
        }
        Comparator<BigDecimal> sens = croissant ? Comparator.naturalOrder() : Comparator.reverseOrder();
        lignes.sort(Comparator.comparing(LigneCompetence::gapMoyen, Comparator.nullsLast(sens))
                .thenComparing(LigneCompetence::competenceId));
        return List.copyOf(lignes);
    }

    /** Exigences du poste face a ses successeurs evalues, et leurs plus grands gaps. */
    private PosteCompetences poste(CouverturePoste couverture, Map<String, List<CompetenceCollaborateur>> competences,
                                   Parametre parametre) {
        List<ResultatMatching> successeurs = couverture.successeurs();
        List<List<EcartExigence>> ecarts = successeurs.stream()
                .map(s -> successionService.ecartsExigences(couverture.poste(),
                        competences.getOrDefault(s.candidat().getIdCollaborateur(), List.of()),
                        parametre.getBaremeCompetences()))
                .toList();
        List<Exigence> exigences = new ArrayList<>();
        List<EcartExigence> duPoste = successionService.ecartsExigences(couverture.poste(), List.of(),
                parametre.getBaremeCompetences());
        for (int i = 0; i < duPoste.size(); i++) {
            int index = i;
            List<EcartExigence> colonne = ecarts.stream().map(e -> e.get(index)).toList();
            exigences.add(new Exigence(duPoste.get(i).competenceId(), duPoste.get(i).competence(),
                    duPoste.get(i).niveauRequis(),
                    colonne.isEmpty() ? null : BigDecimal.valueOf(colonne.stream()
                                    .mapToInt(EcartExigence::niveauActuel).sum())
                            .divide(BigDecimal.valueOf(colonne.size()), PRECISION_MOYENNE, RoundingMode.HALF_UP),
                    (int) colonne.stream().filter(e -> e.ecart() > 0).count()));
        }

        Map<String, PlusGrandGap> noms = new LinkedHashMap<>();
        Map<String, Integer> nombres = new LinkedHashMap<>();
        for (ResultatMatching successeur : successeurs) {
            PlusGrandGap gap = successeur.plusGrandGap();
            if (gap != null && gap.aUnEcart()) {
                noms.putIfAbsent(gap.competenceId(), gap);
                nombres.merge(gap.competenceId(), 1, Integer::sum);
            }
        }
        List<GapFrequent> frequents = nombres.entrySet().stream()
                .map(e -> new GapFrequent(e.getKey(), noms.get(e.getKey()).competence(), e.getValue()))
                .sorted(Comparator.comparing(GapFrequent::nombre, Comparator.reverseOrder())
                        .thenComparing(GapFrequent::competence, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new PosteCompetences(couverture.poste().getPosteId(), couverture.poste().getNomPoste(),
                successeurs.size(), List.copyOf(exigences), frequents);
    }

    private static BigDecimal moyenne(List<GapCompetence> gaps, Function<GapCompetence, Integer> valeur) {
        List<Integer> valeurs = gaps.stream().map(valeur).filter(Objects::nonNull).toList();
        if (valeurs.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(valeurs.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(valeurs.size()), PRECISION_MOYENNE, RoundingMode.HALF_UP);
    }

    /** Refuse un critere hors bornes ou un code inconnu (400) plutot que de rendre un resultat vide. */
    static void verifier(Criteres criteres) {
        Objects.requireNonNull(criteres, "criteres");
        if (criteres.top() < 1 || criteres.top() > TOP_MAX) {
            throw new IllegalArgumentException("Le nombre de compétences du top doit être entre 1 et " + TOP_MAX);
        }
        if (criteres.vivier() != null) {
            List<String> codes = new ArrayList<>(Arrays.stream(VivierThematique.values())
                    .map(VivierThematique::getCode).toList());
            codes.add(VivierReleveService.CODE_VIVIER_RELEVE);
            if (!codes.contains(criteres.vivier())) {
                throw new IllegalArgumentException("Vivier inconnu : " + criteres.vivier() + " (attendu : "
                        + String.join(", ", codes) + ")");
            }
        }
    }
}
