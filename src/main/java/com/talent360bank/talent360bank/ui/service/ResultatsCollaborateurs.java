package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.CategoriePotentiel;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.EntreesVigilance;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.VueManager.AutoVsManager;
import com.talent360bank.talent360bank.ui.model.VueManager.EcartAutoManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Compte;
import com.talent360bank.talent360bank.ui.model.VueManager.CompteCase;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import com.talent360bank.talent360bank.ui.model.VueManager.Synthese;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Resultats d'un groupe de collaborateurs sur un trimestre, et leur synthese :
 * le coeur commun de la vue manager (une equipe) et de la vue entite (un
 * sous-arbre de l'organigramme). Les deux vues comptent donc avec le meme code.
 *
 * <p>REGLE : aucun calcul ici. Scores et categories sont ceux enregistres ;
 * case 9-box (regle du placement), talent et vigilance viennent des methodes
 * du moteur, sans transaction propre (une donnee manquante ne marque pas la
 * transaction de la vue pour annulation). Seules les syntheses (compter,
 * moyenner) sont faites ici.
 *
 * <p><strong>Requetes.</strong> Un nombre fixe quel que soit le groupe : chaque
 * donnee est lue en une requete (IN sur les matricules). Les notes de
 * performance et de potentiel sont lues avec leurs deux sources : l'evaluation
 * du manager dit qui est evalue, l'auto-evaluation sert a la comparaison
 * auto / manager.
 */
@Component
public class ResultatsCollaborateurs {

    private static final int PRECISION_MOYENNE = 2;

    private final ScoreRepository scoreRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final CalculService calculService;
    private final NeufBoxService neufBoxService;
    private final TalentService talentService;
    private final VigilanceService vigilanceService;
    private final EntreesVigilance entreesVigilance;

    public ResultatsCollaborateurs(ScoreRepository scoreRepository,
                                   PerformanceRepository performanceRepository,
                                   PotentielRepository potentielRepository,
                                   QuestionnaireEngagementRepository questionnaireRepository,
                                   CalculService calculService,
                                   NeufBoxService neufBoxService,
                                   TalentService talentService,
                                   VigilanceService vigilanceService,
                                   EntreesVigilance entreesVigilance) {
        this.scoreRepository = scoreRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.calculService = calculService;
        this.neufBoxService = neufBoxService;
        this.talentService = talentService;
        this.vigilanceService = vigilanceService;
        this.entreesVigilance = entreesVigilance;
    }

    /**
     * Resultats du groupe, une ligne par collaborateur dans l'ordre donne.
     *
     * @param avecPerformance matricules du groupe evalues en performance par leur manager sur le trimestre
     * @param avecPotentiel   matricules du groupe evalues en potentiel par leur manager sur le trimestre
     * @param ecartsAutoManager membres qui ont une auto-evaluation, avec le score de chaque source ;
     *                          vide sans auto-evaluation ou sans reglages
     * @param avecAutoEvaluation matricules du groupe qui ont une auto-evaluation (performance ou
     *                           potentiel) sur le trimestre, reglages ou non
     */
    public record Groupe(List<Membre> membres, Set<String> avecPerformance, Set<String> avecPotentiel,
                         List<EcartAutoManager> ecartsAutoManager, Set<String> avecAutoEvaluation) {

        public boolean evaluePerformance(String matricule) {
            return avecPerformance.contains(matricule);
        }

        public boolean evaluePotentiel(String matricule) {
            return avecPotentiel.contains(matricule);
        }
    }

    /**
     * Lit et assemble les resultats du groupe. Un manque est ajoute a
     * {@code manquantes} (une fois par cause), jamais leve.
     *
     * @param parametre reglages du trimestre, null s'ils manquent (rien n'est alors calcule)
     */
    public Groupe charger(List<Collaborateur> collaborateurs, Trimestre trimestre, Parametre parametre,
                          CasesNeufBox cases, Set<String> manquantes) {
        if (collaborateurs.isEmpty()) {
            return new Groupe(List.of(), Set.of(), Set.of(), List.of(), Set.of());
        }
        List<String> ids = collaborateurs.stream().map(Collaborateur::getIdCollaborateur).toList();

        Map<String, Score> scores = scoreRepository.findByTrimestreEtCollaborateurs(trimestre, ids).stream()
                .collect(Collectors.toMap(s -> s.getCollaborateur().getIdCollaborateur(), Function.identity()));
        Map<String, QuestionnaireEngagement> engagements = new HashMap<>();
        questionnaireRepository.findByTrimestreEtCollaborateurs(trimestre, ids)
                .forEach(q -> engagements.put(q.getCollaborateur().getIdCollaborateur(), q));
        // Notes des deux sources, une requete par axe.
        Map<String, Performance> performancesManager = new HashMap<>();
        Map<String, Performance> performancesAuto = new HashMap<>();
        for (Performance performance : performanceRepository.findByTrimestreEtCollaborateurs(trimestre, ids)) {
            (performance.getSource() == SourceEvaluation.AUTO ? performancesAuto : performancesManager)
                    .put(performance.getCollaborateur().getIdCollaborateur(), performance);
        }
        Map<String, Potentiel> potentielsManager = new HashMap<>();
        Map<String, Potentiel> potentielsAuto = new HashMap<>();
        for (Potentiel potentiel : potentielRepository.findByTrimestreEtCollaborateurs(trimestre, ids)) {
            (potentiel.getSource() == SourceEvaluation.AUTO ? potentielsAuto : potentielsManager)
                    .put(potentiel.getCollaborateur().getIdCollaborateur(), potentiel);
        }
        Set<String> avecPerformance = new HashSet<>(performancesManager.keySet());
        Set<String> avecPotentiel = new HashSet<>(potentielsManager.keySet());
        Map<String, ResultatVigilance> vigilances = vigilances(collaborateurs, trimestre, parametre, scores,
                engagements, avecPerformance, avecPotentiel, manquantes);

        List<Membre> membres = new ArrayList<>();
        for (Collaborateur collaborateur : collaborateurs) {
            String id = collaborateur.getIdCollaborateur();
            membres.add(membre(collaborateur, scores.get(id), parametre, cases, engagements.get(id),
                    vigilances.get(id), avecPerformance.contains(id), avecPotentiel.contains(id), manquantes));
        }
        List<EcartAutoManager> ecarts = ecartsAutoManager(collaborateurs, parametre, performancesManager,
                performancesAuto, potentielsManager, potentielsAuto, manquantes);
        Set<String> avecAutoEvaluation = new HashSet<>(performancesAuto.keySet());
        avecAutoEvaluation.addAll(potentielsAuto.keySet());
        return new Groupe(List.copyOf(membres), Set.copyOf(avecPerformance), Set.copyOf(avecPotentiel), ecarts,
                Set.copyOf(avecAutoEvaluation));
    }

    /**
     * Auto-evaluation et evaluation du manager des membres qui ont une
     * auto-evaluation, dans l'ordre du groupe. Le score de chaque source vient
     * de la formule du moteur (CalculService) ; ici, seulement les ecarts.
     */
    private List<EcartAutoManager> ecartsAutoManager(List<Collaborateur> collaborateurs, Parametre parametre,
                                                     Map<String, Performance> performancesManager,
                                                     Map<String, Performance> performancesAuto,
                                                     Map<String, Potentiel> potentielsManager,
                                                     Map<String, Potentiel> potentielsAuto,
                                                     Set<String> manquantes) {
        if (parametre == null || (performancesAuto.isEmpty() && potentielsAuto.isEmpty())) {
            return List.of();
        }
        List<EcartAutoManager> ecarts = new ArrayList<>();
        try {
            for (Collaborateur collaborateur : collaborateurs) {
                String id = collaborateur.getIdCollaborateur();
                if (!performancesAuto.containsKey(id) && !potentielsAuto.containsKey(id)) {
                    continue;
                }
                BigDecimal performanceAuto = scorePerformance(performancesAuto.get(id), parametre);
                BigDecimal performanceManager = scorePerformance(performancesManager.get(id), parametre);
                BigDecimal potentielAuto = scorePotentiel(potentielsAuto.get(id), parametre);
                BigDecimal potentielManager = scorePotentiel(potentielsManager.get(id), parametre);
                ecarts.add(new EcartAutoManager(id, collaborateur.getNom(), collaborateur.getPrenom(),
                        performanceAuto, performanceManager, ecart(performanceAuto, performanceManager),
                        potentielAuto, potentielManager, ecart(potentielAuto, potentielManager)));
            }
        } catch (DonneesIncompletesException e) {
            manquantes.add("Auto-évaluation / manager : " + e.getMessage());
            return List.of();
        }
        return List.copyOf(ecarts);
    }

    private BigDecimal scorePerformance(Performance evaluation, Parametre parametre) {
        return evaluation == null ? null : calculService.calculerScorePerformance(evaluation, parametre);
    }

    private BigDecimal scorePotentiel(Potentiel evaluation, Parametre parametre) {
        return evaluation == null ? null : calculService.calculerScorePotentiel(evaluation, parametre);
    }

    /** auto - manager (positif : l'auto-evaluation est plus haute) ; null si l'un manque. */
    static BigDecimal ecart(BigDecimal auto, BigDecimal manager) {
        return auto == null || manager == null ? null : auto.subtract(manager);
    }

    /**
     * Synthese auto / manager d'un groupe : ecarts moyens (sur les membres qui
     * ont les deux evaluations, 2 decimales) et membres dont un ecart atteint,
     * en valeur absolue, le seuil des reglages du trimestre
     * ({@link com.talent360bank.talent360bank.entity.SeuilsAutoEvaluation}).
     * null si aucun membre n'a d'auto-evaluation ou sans reglages.
     */
    public static AutoVsManager autoVsManager(List<EcartAutoManager> ecarts, Parametre parametre) {
        if (ecarts.isEmpty() || parametre == null || parametre.getSeuilsAutoEvaluation() == null) {
            return null;
        }
        BigDecimal seuil = parametre.getSeuilsAutoEvaluation().getSeuilEcartImportant();
        List<EcartAutoManager> importants = ecarts.stream()
                .filter(e -> important(e.ecartPerformance(), seuil) || important(e.ecartPotentiel(), seuil))
                .toList();
        return new AutoVsManager(ecarts,
                moyenneDe(ecarts.stream().map(EcartAutoManager::ecartPerformance).toList()),
                moyenneDe(ecarts.stream().map(EcartAutoManager::ecartPotentiel).toList()),
                seuil, importants);
    }

    private static boolean important(BigDecimal ecart, BigDecimal seuil) {
        return ecart != null && seuil != null && ecart.abs().compareTo(seuil) >= 0;
    }

    /**
     * Vigilance de ceux qui ont au moins une donnee de vigilance ; les autres
     * restent sans vigilance (EntreesVigilance) et sont nommes dans donneesManquantes.
     */
    private Map<String, ResultatVigilance> vigilances(List<Collaborateur> collaborateurs, Trimestre trimestre,
                                                      Parametre parametre, Map<String, Score> scores,
                                                      Map<String, QuestionnaireEngagement> engagements,
                                                      Set<String> avecPerformance, Set<String> avecPotentiel,
                                                      Set<String> manquantes) {
        if (parametre == null) {
            return Map.of();
        }
        Set<String> sansDonnee = entreesVigilance.sansDonnee(
                collaborateurs.stream().map(Collaborateur::getIdCollaborateur).toList(), trimestre,
                EntreesVigilance.questionnaireRempli(engagements),
                id -> avecPerformance.contains(id) || avecPotentiel.contains(id));
        if (!sansDonnee.isEmpty()) {
            manquantes.add(EntreesVigilance.AUCUNE_DONNEE + " : " + collaborateurs.stream()
                    .filter(c -> sansDonnee.contains(c.getIdCollaborateur()))
                    .map(c -> c.getPrenom() + " " + c.getNom() + " (" + c.getIdCollaborateur() + ")")
                    .collect(Collectors.joining(", ")));
        }
        List<Collaborateur> evaluables = collaborateurs.stream()
                .filter(c -> !sansDonnee.contains(c.getIdCollaborateur()))
                .toList();
        try {
            return vigilanceService.evaluerLot(evaluables, trimestre, parametre, scores, engagements);
        } catch (DonneesIncompletesException e) {
            manquantes.add("Vigilance : " + e.getMessage());
            return Map.of();
        }
    }

    private Membre membre(Collaborateur collaborateur, Score score, Parametre parametre, CasesNeufBox cases,
                          QuestionnaireEngagement engagement, ResultatVigilance vigilance,
                          boolean evaluePerformance, boolean evaluePotentiel, Set<String> manquantes) {
        boolean scoreComplet = score != null && score.getScorePerformance() != null
                && score.getScorePotentiel() != null;
        if (!scoreComplet && evaluePerformance && evaluePotentiel) {
            manquantes.add("Scores non calculés pour une partie des collaborateurs : lancer le calcul du trimestre");
        }

        CategoriePerformance categoriePerformance = score == null ? null : score.getCategoriePerformance();
        CategoriePotentiel categoriePotentiel = score == null ? null : score.getCategoriePotentiel();
        CaseNeufBox case9Box = null;
        Boolean talent = null;
        Boolean hautPotentiel = null;
        if (scoreComplet && parametre != null) {
            try {
                if (categoriePerformance == null) {
                    categoriePerformance = calculService.categoriePerformance(score.getScorePerformance(),
                            parametre.getSeuilsCategoriePerformance());
                }
                if (categoriePotentiel == null) {
                    categoriePotentiel = neufBoxService.categoriePotentiel(score.getScorePotentiel(), parametre);
                }
            } catch (DonneesIncompletesException e) {
                manquantes.add("Catégories : " + e.getMessage());
            }
            if (score.getPositionBox() == null) {
                manquantes.add("Placement 9-box non lancé pour ce trimestre");
            } else {
                case9Box = caseOuNull(score, parametre, cases, manquantes);
            }
            try {
                talent = talentService.estTalent(score, parametre);
                hautPotentiel = talentService.estHautPotentiel(score, parametre);
            } catch (DonneesIncompletesException e) {
                manquantes.add("Statut talent / haut potentiel : " + e.getMessage());
            }
        }
        // Vivier de releve (10_TALENTS!J) : talent OU haut potentiel, comme TalentService.getVivierReleve.
        Boolean vivierSuccession = talent == null || hautPotentiel == null ? null : talent || hautPotentiel;

        return new Membre(collaborateur.getIdCollaborateur(), collaborateur.getNom(), collaborateur.getPrenom(),
                collaborateur.getFonction(),
                score == null ? null : score.getScorePerformance(),
                categoriePerformance == null ? null : categoriePerformance.name(),
                categoriePerformance == null ? null : categoriePerformance.getLibelle(),
                score == null ? null : score.getScorePotentiel(),
                categoriePotentiel == null ? null : categoriePotentiel.name(),
                categoriePotentiel == null ? null : categoriePotentiel.getLibelle(),
                case9Box, talent, hautPotentiel, vivierSuccession,
                engagement == null ? null : engagement.getScoreEngagement(),
                vigilance == null ? null : vigilance.indice(),
                vigilance == null ? null : vigilance.niveau().name(),
                vigilance == null ? null : vigilance.niveau().getLibelle(),
                evaluePerformance || evaluePotentiel || score != null);
    }

    /** Case 9-box d'un score place, null (et un manque) si les seuils manquent. */
    static CaseNeufBox caseOuNull(Score score, Parametre parametre, CasesNeufBox cases, Set<String> manquantes) {
        try {
            return cases.caseDe(score, parametre);
        } catch (DonneesIncompletesException e) {
            manquantes.add("Case 9-box : " + e.getMessage());
            return null;
        }
    }

    // --- synthese --------------------------------------------------------------

    /**
     * Synthese d'un groupe. Les moyennes portent sur ceux qui ont la valeur,
     * arrondies a 2 decimales ; chaque repartition liste toutes les valeurs
     * possibles, a 0 si besoin.
     */
    public static Synthese synthese(List<Membre> membres, CasesNeufBox cases) {
        List<Compte> categoriesPerformance = new ArrayList<>();
        for (CategoriePerformance categorie : CategoriePerformance.values()) {
            categoriesPerformance.add(new Compte(categorie.name(), categorie.getLibelle(),
                    compter(membres, m -> categorie.name().equals(m.categoriePerformance()))));
        }
        List<Compte> categoriesPotentiel = new ArrayList<>();
        for (CategoriePotentiel categorie : CategoriePotentiel.values()) {
            categoriesPotentiel.add(new Compte(categorie.name(), categorie.getLibelle(),
                    compter(membres, m -> categorie.name().equals(m.categoriePotentiel()))));
        }
        List<CompteCase> neufBox = new ArrayList<>();
        for (int numero = 1; numero <= 9; numero++) {
            int n = numero;
            neufBox.add(new CompteCase(numero, cases.libelle(numero),
                    compter(membres, m -> m.neufBox() != null && m.neufBox().numero() == n)));
        }
        List<Compte> niveauxVigilance = new ArrayList<>();
        for (NiveauVigilance niveau : NiveauVigilance.values()) {
            niveauxVigilance.add(new Compte(niveau.name(), niveau.getLibelle(),
                    compter(membres, m -> niveau.name().equals(m.niveauVigilance()))));
        }

        return new Synthese(membres.size(),
                compter(membres, m -> m.scorePerformance() != null && m.scorePotentiel() != null),
                moyenne(membres, Membre::scorePerformance),
                moyenne(membres, Membre::scorePotentiel),
                List.copyOf(categoriesPerformance), List.copyOf(categoriesPotentiel), List.copyOf(neufBox),
                compter(membres, m -> Boolean.TRUE.equals(m.estTalent())),
                compter(membres, m -> Boolean.TRUE.equals(m.estHautPotentiel())),
                compter(membres, m -> Boolean.TRUE.equals(m.estVivierSuccession())),
                moyenne(membres, Membre::engagement),
                List.copyOf(niveauxVigilance),
                compter(membres, m -> m.niveauVigilance() == null));
    }

    /** Vigilance du membre au niveau ELEVEE ou au-dela. */
    static boolean vigilanceElevee(Membre membre) {
        return membre.niveauVigilance() != null
                && NiveauVigilance.valueOf(membre.niveauVigilance()).compareTo(NiveauVigilance.ELEVEE) >= 0;
    }

    static int compter(List<Membre> membres, Predicate<Membre> condition) {
        return (int) membres.stream().filter(condition).count();
    }

    /** Moyenne des valeurs connues, arrondie a 2 decimales ; null si aucune. */
    static BigDecimal moyenne(List<Membre> membres, Function<Membre, BigDecimal> valeur) {
        return moyenneDe(membres.stream().map(valeur).toList());
    }

    /** Moyenne des valeurs non nulles, arrondie a 2 decimales ; null si aucune. */
    private static BigDecimal moyenneDe(List<BigDecimal> toutes) {
        List<BigDecimal> valeurs = toutes.stream().filter(Objects::nonNull).toList();
        if (valeurs.isEmpty()) {
            return null;
        }
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valeurs.size()), PRECISION_MOYENNE, RoundingMode.HALF_UP);
    }
}
