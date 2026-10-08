package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.EntreesVigilance;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCibleService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.SuccessionService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.AutoEvaluation;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Critere;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CritereAuto;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Evaluation;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EvaluationAuto;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.HistoriqueTrimestre;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Identite;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.ManagerFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.PosteCible;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.RaisonVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Succession;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Talent;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Vigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.VivierFiche;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assemble la fiche d'un collaborateur sur un trimestre : tout ce que le RH
 * doit voir, en un appel.
 *
 * <p>REGLE : aucun calcul ici. Scores, categories et case 9-box sont ceux
 * enregistres par ScoreService et NeufBoxService ; talent, gap de competence,
 * vigilance, matching et poste cible viennent des methodes du moteur (TalentService,
 * CompetenceCollaborateurService, VigilanceService, SuccessionService, PosteCibleService).
 *
 * <p><strong>Donnees manquantes.</strong> Un bloc sans donnees reste vide
 * (null ou liste vide) et une phrase s'ajoute a donneesManquantes ; seuls un
 * trimestre ou un matricule inconnus levent RessourceIntrouvableException (404).
 * Pour cela, les blocs n'appellent que des methodes sans transaction propre,
 * avec des donnees deja chargees : une exception levee par une methode
 * {@code @Transactional} marquerait la transaction de la fiche pour
 * annulation, meme rattrapee, et la fiche echouerait a la fin.
 *
 * <p><strong>Requetes.</strong> Un nombre fixe, quelle que soit la taille de
 * l'historique ou le nombre de competences : chaque donnee est lue une fois,
 * relations utiles chargees dans la meme requete.
 */
@Service
public class FicheCollaborateurViewService {

    /**
     * Donnee manquante "pas de score d'engagement" : le Talent Passport (page) ne montre pas
     * l'engagement et ecarte cette ligne ; l'API de la fiche la garde.
     */
    public static final String MANQUE_ENGAGEMENT = "Pas de questionnaire d'engagement pour ce trimestre";

    private final CollaborateurRepository collaborateurRepository;
    private final TrimestreRepository trimestreRepository;
    private final ParametreRepository parametreRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final ValidationComiteRepository validationComiteRepository;
    private final AppartenanceVivierRepository appartenanceVivierRepository;
    private final SuccesseurIdentifieRepository successeurIdentifieRepository;
    private final PosteRepository posteRepository;
    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final ScoreService scoreService;
    private final CalculService calculService;
    private final NeufBoxService neufBoxService;
    private final TalentService talentService;
    private final VigilanceService vigilanceService;
    private final SuccessionService successionService;
    private final CompetenceCollaborateurService competenceCollaborateurService;
    private final VivierThematiqueService vivierThematiqueService;
    private final PosteCibleService posteCibleService;
    private final EntreesVigilance entreesVigilance;

    public FicheCollaborateurViewService(CollaborateurRepository collaborateurRepository,
                                         TrimestreRepository trimestreRepository,
                                         ParametreRepository parametreRepository,
                                         PerformanceRepository performanceRepository,
                                         PotentielRepository potentielRepository,
                                         QuestionnaireEngagementRepository questionnaireRepository,
                                         ValidationComiteRepository validationComiteRepository,
                                         AppartenanceVivierRepository appartenanceVivierRepository,
                                         SuccesseurIdentifieRepository successeurIdentifieRepository,
                                         PosteRepository posteRepository,
                                         CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                         Matrice9BoxRepository matrice9BoxRepository,
                                         ScoreService scoreService,
                                         CalculService calculService,
                                         NeufBoxService neufBoxService,
                                         TalentService talentService,
                                         VigilanceService vigilanceService,
                                         SuccessionService successionService,
                                         CompetenceCollaborateurService competenceCollaborateurService,
                                         VivierThematiqueService vivierThematiqueService,
                                         PosteCibleService posteCibleService,
                                         EntreesVigilance entreesVigilance) {
        this.collaborateurRepository = collaborateurRepository;
        this.trimestreRepository = trimestreRepository;
        this.parametreRepository = parametreRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.validationComiteRepository = validationComiteRepository;
        this.appartenanceVivierRepository = appartenanceVivierRepository;
        this.successeurIdentifieRepository = successeurIdentifieRepository;
        this.posteRepository = posteRepository;
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.scoreService = scoreService;
        this.calculService = calculService;
        this.neufBoxService = neufBoxService;
        this.talentService = talentService;
        this.vigilanceService = vigilanceService;
        this.successionService = successionService;
        this.competenceCollaborateurService = competenceCollaborateurService;
        this.vivierThematiqueService = vivierThematiqueService;
        this.posteCibleService = posteCibleService;
        this.entreesVigilance = entreesVigilance;
    }

    /**
     * @throws RessourceIntrouvableException si le trimestre ou le matricule est inconnu
     */
    @Transactional(readOnly = true)
    public FicheCollaborateur construire(String matricule, int annee, int numero) {
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre T" + numero + " " + annee));
        Collaborateur collaborateur = collaborateurRepository.findByIdAvecManager(matricule)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun collaborateur " + matricule));

        Donnees d = new Donnees(collaborateur, trimestre);
        d.parametre = parametreRepository.findByTrimestre(trimestre).orElse(null);
        d.score = scoreService.rechercher(collaborateur, trimestre).orElse(null);
        // Les deux sources (manager et auto-evaluation) en une requete par axe.
        for (Performance performance
                : performanceRepository.findByCollaborateurAndTrimestreOrderBySource(collaborateur, trimestre)) {
            if (performance.getSource() == SourceEvaluation.AUTO) {
                d.performanceAuto = performance;
            } else {
                d.performance = performance;
            }
        }
        for (Potentiel potentiel
                : potentielRepository.findByCollaborateurAndTrimestreOrderBySource(collaborateur, trimestre)) {
            if (potentiel.getSource() == SourceEvaluation.AUTO) {
                d.potentielAuto = potentiel;
            } else {
                d.potentiel = potentiel;
            }
        }
        d.competences = competenceCollaborateurRepository.findByCollaborateurAvecCompetence(collaborateur);
        d.cases = new CasesNeufBox(neufBoxService, matrice9BoxRepository.findAll());

        if (d.parametre == null) {
            d.manque("Aucun réglage pour " + libelle(trimestre)
                    + " : catégories, talent, compétences, vigilance et succession ne sont pas calculables");
        }
        // Sans l'une des deux evaluations, le moteur ne calcule pas de score : le manque
        // est deja dit par le bloc concerne.
        if (d.score == null && d.performance != null && d.potentiel != null) {
            d.manque("Scores non calculés pour " + libelle(trimestre) + " : lancer le calcul du trimestre");
        }

        return new FicheCollaborateur(
                new TrimestreFiche(annee, numero, libelle(trimestre), trimestre.getDateReference()),
                identite(collaborateur, trimestre),
                performance(d),
                potentiel(d),
                neufBox(d),
                talent(d),
                competences(d),
                engagement(d),
                vigilance(d),
                successions(d),
                posteCible(d),
                historique(d),
                autoEvaluation(d),
                List.copyOf(d.manquantes));
    }

    // --- identite ------------------------------------------------------------

    private Identite identite(Collaborateur collaborateur, Trimestre trimestre) {
        Manager manager = collaborateur.getManager();
        ManagerFiche managerFiche = manager == null || manager.getCollaborateur() == null ? null
                : new ManagerFiche(manager.getIdCollaborateur(), manager.getCollaborateur().getNomComplet());
        return new Identite(collaborateur.getIdCollaborateur(), collaborateur.getNom(), collaborateur.getPrenom(),
                collaborateur.getFonction(), collaborateur.getGrade(), EntiteFiche.de(collaborateur.getEntite()),
                managerFiche, nom(collaborateur.getStatut()), collaborateur.getDateEntree(),
                successionService.ancienneteEnAnnees(collaborateur, trimestre.getDateReference()));
    }

    // --- performance et potentiel ----------------------------------------------

    private Evaluation performance(Donnees d) {
        if (d.performance == null) {
            d.manque(d.performanceAuto == null ? "Pas d'évaluation de performance pour ce trimestre"
                    : "Pas d'évaluation de performance du manager pour ce trimestre (auto-évaluation seule)");
            return null;
        }
        List<Critere> criteres = criteresPerformance(d.performance, d.parametre);

        BigDecimal score = d.score == null ? null : d.score.getScorePerformance();
        String categorie = null;
        String libelle = null;
        if (d.score != null && d.score.getCategoriePerformance() != null) {
            categorie = d.score.getCategoriePerformance().name();
            libelle = d.score.getCategoriePerformance().getLibelle();
        } else if (score != null && d.parametre != null) {
            try {
                var calculee = calculService.categoriePerformance(score, d.parametre.getSeuilsCategoriePerformance());
                categorie = calculee.name();
                libelle = calculee.getLibelle();
            } catch (DonneesIncompletesException e) {
                d.manque("Catégorie de performance : " + e.getMessage());
            }
        }
        return new Evaluation(score, categorie, libelle, criteres);
    }

    private Evaluation potentiel(Donnees d) {
        if (d.potentiel == null) {
            d.manque(d.potentielAuto == null ? "Pas d'évaluation de potentiel pour ce trimestre"
                    : "Pas d'évaluation de potentiel du manager pour ce trimestre (auto-évaluation seule)");
            return null;
        }
        List<Critere> criteres = criteresPotentiel(d.potentiel, d.parametre);

        BigDecimal score = d.score == null ? null : d.score.getScorePotentiel();
        String categorie = null;
        String libelle = null;
        if (d.score != null && d.score.getCategoriePotentiel() != null) {
            categorie = d.score.getCategoriePotentiel().name();
            libelle = d.score.getCategoriePotentiel().getLibelle();
        } else if (score != null && d.parametre != null) {
            try {
                var calculee = neufBoxService.categoriePotentiel(score, d.parametre);
                categorie = calculee.name();
                libelle = calculee.getLibelle();
            } catch (DonneesIncompletesException e) {
                d.manque("Catégorie de potentiel : " + e.getMessage());
            }
        }
        return new Evaluation(score, categorie, libelle, criteres);
    }

    /** Les 5 criteres de performance d'une evaluation (une source), avec leur poids. */
    private static List<Critere> criteresPerformance(Performance p, Parametre parametre) {
        PoidsPerformance poids = parametre == null ? null : parametre.getPoidsPerformance();
        return List.of(
                new Critere("OBJECTIFS", "Objectifs", p.getNoteObjectifs(),
                        poids == null ? null : poids.getPoidsObjectifs()),
                new Critere("COMPETENCES", "Compétences", p.getNoteCompetences(),
                        poids == null ? null : poids.getPoidsCompetences()),
                new Critere("COMPORTEMENT", "Comportement", p.getNoteComportement(),
                        poids == null ? null : poids.getPoidsComportement()),
                new Critere("CONTRIBUTION", "Contribution", p.getNoteContribution(),
                        poids == null ? null : poids.getPoidsContribution()),
                new Critere("DEVELOPPEMENT", "Développement", p.getNoteDeveloppement(),
                        poids == null ? null : poids.getPoidsDeveloppement()));
    }

    /** Les 7 criteres de potentiel d'une evaluation (une source), avec leur poids. */
    private static List<Critere> criteresPotentiel(Potentiel p, Parametre parametre) {
        PoidsPotentiel poids = parametre == null ? null : parametre.getPoidsPotentiel();
        return List.of(
                new Critere("AGILITE_APPRENTISSAGE", "Agilité d'apprentissage", p.getNoteLearning(),
                        poids == null ? null : poids.getPoidsLearning()),
                new Critere("LEADERSHIP", "Leadership", p.getNoteLeadership(),
                        poids == null ? null : poids.getPoidsLeadership()),
                new Critere("ADAPTABILITE", "Adaptabilité", p.getNoteAdaptabilite(),
                        poids == null ? null : poids.getPoidsAdaptabilite()),
                new Critere("GESTION_COMPLEXITE", "Gestion de la complexité", p.getNoteComplexite(),
                        poids == null ? null : poids.getPoidsComplexite()),
                new Critere("MOBILITE", "Mobilité", p.getNoteMobilite(),
                        poids == null ? null : poids.getPoidsMobilite()),
                new Critere("VISION_STRATEGIQUE", "Vision stratégique", p.getNoteStrategie(),
                        poids == null ? null : poids.getPoidsStrategie()),
                new Critere("AUTONOMIE", "Autonomie", p.getNoteAutonomie(),
                        poids == null ? null : poids.getPoidsAutonomie()));
    }

    // --- auto-evaluation ---------------------------------------------------------

    /**
     * Auto-evaluation face a l'evaluation du manager, null sans auto-evaluation.
     * Chaque score est calcule par le moteur (CalculService) sur les notes de sa
     * source ; ici, seulement les ecarts.
     */
    private AutoEvaluation autoEvaluation(Donnees d) {
        if (d.performanceAuto == null && d.potentielAuto == null) {
            return null;
        }
        EvaluationAuto performance = null;
        if (d.performanceAuto != null) {
            BigDecimal score = scorePerformance(d.performanceAuto, d);
            String categorie = null;
            String libelle = null;
            if (score != null) {
                try {
                    var calculee = calculService.categoriePerformance(score,
                            d.parametre.getSeuilsCategoriePerformance());
                    categorie = calculee.name();
                    libelle = calculee.getLibelle();
                } catch (DonneesIncompletesException e) {
                    d.manque("Catégorie de l'auto-évaluation de performance : " + e.getMessage());
                }
            }
            BigDecimal scoreManager = d.performance == null ? null : scorePerformance(d.performance, d);
            performance = new EvaluationAuto(score, categorie, libelle, scoreManager,
                    ResultatsCollaborateurs.ecart(score, scoreManager),
                    criteresAuto(criteresPerformance(d.performanceAuto, d.parametre),
                            d.performance == null ? null : criteresPerformance(d.performance, d.parametre)));
        }
        EvaluationAuto potentiel = null;
        if (d.potentielAuto != null) {
            BigDecimal score = scorePotentiel(d.potentielAuto, d);
            String categorie = null;
            String libelle = null;
            if (score != null) {
                try {
                    var calculee = neufBoxService.categoriePotentiel(score, d.parametre);
                    categorie = calculee.name();
                    libelle = calculee.getLibelle();
                } catch (DonneesIncompletesException e) {
                    d.manque("Catégorie de l'auto-évaluation de potentiel : " + e.getMessage());
                }
            }
            BigDecimal scoreManager = d.potentiel == null ? null : scorePotentiel(d.potentiel, d);
            potentiel = new EvaluationAuto(score, categorie, libelle, scoreManager,
                    ResultatsCollaborateurs.ecart(score, scoreManager),
                    criteresAuto(criteresPotentiel(d.potentielAuto, d.parametre),
                            d.potentiel == null ? null : criteresPotentiel(d.potentiel, d.parametre)));
        }
        return new AutoEvaluation(performance, potentiel);
    }

    /** Score d'une evaluation par la formule du moteur ; null (et un manque) sans reglages complets. */
    private BigDecimal scorePerformance(Performance evaluation, Donnees d) {
        if (d.parametre == null) {
            return null;
        }
        try {
            return calculService.calculerScorePerformance(evaluation, d.parametre);
        } catch (DonneesIncompletesException e) {
            d.manque("Score de performance par source : " + e.getMessage());
            return null;
        }
    }

    private BigDecimal scorePotentiel(Potentiel evaluation, Donnees d) {
        if (d.parametre == null) {
            return null;
        }
        try {
            return calculService.calculerScorePotentiel(evaluation, d.parametre);
        } catch (DonneesIncompletesException e) {
            d.manque("Score de potentiel par source : " + e.getMessage());
            return null;
        }
    }

    /** Les criteres de l'auto-evaluation, chacun avec la note du manager et l'ecart (meme ordre des deux cotes). */
    private static List<CritereAuto> criteresAuto(List<Critere> auto, List<Critere> manager) {
        List<CritereAuto> criteres = new ArrayList<>();
        for (int i = 0; i < auto.size(); i++) {
            Critere critere = auto.get(i);
            BigDecimal noteManager = manager == null ? null : manager.get(i).note();
            criteres.add(new CritereAuto(critere.code(), critere.libelle(), critere.note(), noteManager,
                    ResultatsCollaborateurs.ecart(critere.note(), noteManager), critere.poids()));
        }
        return List.copyOf(criteres);
    }

    // --- 9-box -----------------------------------------------------------------

    private CaseNeufBox neufBox(Donnees d) {
        if (d.score == null) {
            return null;
        }
        if (d.score.getPositionBox() == null) {
            d.manque("Pas de case 9-box : placement non lancé pour ce trimestre");
            return null;
        }
        if (d.parametre == null) {
            return null;
        }
        try {
            return d.cases.caseDe(d.score, d.parametre);
        } catch (DonneesIncompletesException e) {
            d.manque("Case 9-box : " + e.getMessage());
            return null;
        }
    }


    // --- talent et viviers -----------------------------------------------------

    private Talent talent(Donnees d) {
        Boolean talent = null;
        Boolean hautPotentiel = null;
        if (d.score != null && d.parametre != null) {
            try {
                talent = talentService.estTalent(d.score, d.parametre);
                hautPotentiel = talentService.estHautPotentiel(d.score, d.parametre);
            } catch (DonneesIncompletesException e) {
                d.manque("Statut talent / haut potentiel : " + e.getMessage());
            }
        }
        // Vivier de releve (10_TALENTS!J) : talent OU haut potentiel, comme TalentService.getVivierReleve.
        Boolean vivierSuccession = talent == null || hautPotentiel == null ? null : talent || hautPotentiel;

        Optional<StatutValidationComite> decision =
                validationComiteRepository.findStatut(d.collaborateur.getIdCollaborateur(), d.trimestre);

        List<VivierFiche> viviers = new ArrayList<>();
        for (AppartenanceVivier appartenance
                : appartenanceVivierRepository.findByCollaborateurEtTrimestre(d.collaborateur, d.trimestre)) {
            viviers.add(new VivierFiche(appartenance.getVivier().getCode(), appartenance.getVivier().getNomCategorie(),
                    appartenance.getOrigine()));
        }
        // Vivier thematique : meme perimetre que VivierThematiqueService.getViviersThematiques
        // (score sur le trimestre, collaborateur actif), direction figee sur le score.
        if (d.score != null && d.collaborateur.estCalculable()) {
            vivierThematiqueService.vivierDe(d.score).ifPresent(vivier ->
                    viviers.add(new VivierFiche(vivier.getCode(), vivier.getLibelle(), "THEMATIQUE")));
        }

        return new Talent(talent, hautPotentiel, vivierSuccession,
                decision.map(Enum::name).orElse(null),
                decision.map(StatutValidationComite::getLibelle).orElse(null),
                List.copyOf(viviers));
    }

    // --- competences -----------------------------------------------------------

    private List<FicheCollaborateur.Competence> competences(Donnees d) {
        if (d.competences.isEmpty()) {
            d.manque("Aucune compétence renseignée");
            return List.of();
        }
        List<CompetenceCollaborateur> triees = d.competences.stream()
                .sorted(Comparator.comparing(skill -> skill.getCompetence().getCompetenceId()))
                .toList();

        List<GapCompetence> gaps = null;
        if (d.parametre != null) {
            try {
                gaps = triees.stream()
                        .map(skill -> competenceCollaborateurService.evaluer(skill, d.parametre.getSeuilsGapCompetence()))
                        .toList();
            } catch (DonneesIncompletesException e) {
                d.manque("Statut des compétences : " + e.getMessage());
            }
        }

        List<FicheCollaborateur.Competence> lignes = new ArrayList<>();
        for (int i = 0; i < triees.size(); i++) {
            CompetenceCollaborateur skill = triees.get(i);
            GapCompetence gap = gaps == null ? null : gaps.get(i);
            lignes.add(new FicheCollaborateur.Competence(skill.getCompetence().getCompetenceId(),
                    skill.getCompetence().getNom(), skill.getCompetence().getCategorie(),
                    skill.getNiveauCible(), skill.getNiveauActuel(), gap == null ? null : gap.gap(),
                    gap == null || gap.statut() == null ? null : gap.statut().name(),
                    gap == null || gap.statut() == null ? null : gap.statut().getLibelle()));
        }
        return List.copyOf(lignes);
    }

    // --- engagement et vigilance ------------------------------------------------

    private BigDecimal engagement(Donnees d) {
        BigDecimal score = questionnaireRepository.findByCollaborateurAndTrimestre(d.collaborateur, d.trimestre)
                .map(QuestionnaireEngagement::getScoreEngagement)
                .orElse(null);
        if (score == null) {
            d.manque(MANQUE_ENGAGEMENT);
        }
        d.engagement = score;
        return score;
    }

    private Vigilance vigilance(Donnees d) {
        if (d.parametre == null) {
            return null;
        }
        // Sans aucune donnee de vigilance, l'indice vaudrait 0 sans rien dire du risque (EntreesVigilance).
        String matricule = d.collaborateur.getIdCollaborateur();
        if (!entreesVigilance.sansDonnee(List.of(matricule), d.trimestre,
                id -> d.engagement != null, id -> d.performance != null || d.potentiel != null).isEmpty()) {
            d.manque(EntreesVigilance.AUCUNE_DONNEE);
            return null;
        }
        try {
            ResultatVigilance resultat = vigilanceService.evaluer(d.collaborateur, d.trimestre, d.parametre);
            List<RaisonVigilance> raisons = resultat.signaux().stream()
                    .sorted(Comparator.comparing(SignalVigilance::ordinal))
                    .map(signal -> new RaisonVigilance(signal.name(), signal.getLibelle(),
                            signal.pointsDans(d.parametre.getPointsVigilance())))
                    .toList();
            return new Vigilance(resultat.indice(), resultat.niveau().name(), resultat.niveau().getLibelle(),
                    raisons);
        } catch (DonneesIncompletesException e) {
            d.manque("Vigilance : " + e.getMessage());
            return null;
        }
    }

    // --- succession ------------------------------------------------------------

    private List<Succession> successions(Donnees d) {
        List<Poste> postes = successeurIdentifieRepository.findPostesDuSuccesseur(d.collaborateur.getIdCollaborateur())
                .stream()
                .filter(PosteCritiqueService::estCritique)
                .toList();
        d.postesSuccesseur = postes;
        if (postes.isEmpty() || d.parametre == null) {
            return List.of();
        }
        if (d.score == null || d.score.getScorePerformance() == null || d.score.getScorePotentiel() == null) {
            d.manque("Matching succession non calculable : scores absents pour ce trimestre");
            return List.of();
        }

        List<Succession> successions = new ArrayList<>();
        try {
            for (Poste poste : postes) {
                ResultatMatching matching = successionService.evaluer(d.collaborateur, poste, d.score, d.potentiel,
                        d.competences, d.parametre, d.trimestre.getDateReference());
                PlusGrandGap gap = ecart(matching.plusGrandGap());
                successions.add(new Succession(poste.getPosteId(), poste.getNomPoste(), poste.getDirection(),
                        poste.getCriticite(), matching.scoreMatching(), matching.readiness().name(),
                        matching.readiness().getLibelle(), gap == null ? null : gap.competence(),
                        gap == null ? 0 : gap.ecart()));
            }
        } catch (DonneesIncompletesException e) {
            d.manque("Matching succession : " + e.getMessage());
            return List.of();
        }
        return List.copyOf(successions);
    }

    /**
     * Poste critique ou le matching du collaborateur est le meilleur
     * (PosteCibleService), sur les donnees deja lues plus les postes (une requete).
     * Null sans reglages ni score complet : le manque est deja dit par ces blocs.
     */
    private PosteCible posteCible(Donnees d) {
        if (d.parametre == null || d.score == null || d.score.getScorePerformance() == null
                || d.score.getScorePotentiel() == null) {
            return null;
        }
        try {
            Set<String> identifies = d.postesSuccesseur.stream().map(Poste::getPosteId).collect(Collectors.toSet());
            return posteCibleService.meilleurPoste(d.collaborateur, d.score, d.potentiel, d.competences,
                            posteRepository.findAllAvecCompetences(), identifies, d.parametre,
                            d.trimestre.getDateReference())
                    .map(cible -> {
                        Poste poste = cible.poste();
                        ResultatMatching matching = cible.matching();
                        PlusGrandGap gap = ecart(matching.plusGrandGap());
                        return new PosteCible(poste.getPosteId(), poste.getNomPoste(), poste.getDirection(),
                                poste.getCriticite(), matching.scoreMatching(), matching.readiness().name(),
                                matching.readiness().getLibelle(), cible.successeurIdentifie(),
                                gap == null ? null : gap.competence(), gap == null ? 0 : gap.ecart());
                    })
                    .orElse(null);
        } catch (DonneesIncompletesException e) {
            d.manque("Poste cible : " + e.getMessage());
            return null;
        }
    }

    /** Le plus grand gap s'il y a un vrai ecart, null sinon (aucun ecart, ou poste sans exigence). */
    private static PlusGrandGap ecart(PlusGrandGap gap) {
        return gap != null && gap.aUnEcart() ? gap : null;
    }

    // --- historique ------------------------------------------------------------

    private List<HistoriqueTrimestre> historique(Donnees d) {
        List<Score> anciens = scoreService.historique(d.collaborateur).stream()
                .filter(ancien -> avant(ancien.getTrimestre(), d.trimestre))
                .toList();
        if (anciens.isEmpty()) {
            return List.of();
        }
        // Reglages de chaque trimestre passe, en une requete : la case se deduit avec ses seuils.
        Map<Integer, Parametre> reglages = parametreRepository
                .findByTrimestreIn(anciens.stream().map(Score::getTrimestre).toList()).stream()
                .collect(Collectors.toMap(p -> p.getTrimestre().getIdTrimestre(), Function.identity()));

        List<HistoriqueTrimestre> historique = new ArrayList<>();
        for (Score ancien : anciens) {
            Trimestre t = ancien.getTrimestre();
            historique.add(new HistoriqueTrimestre(t.getAnnee(), t.getNumero(), libelle(t),
                    ancien.getScorePerformance(), ancien.getScorePotentiel(),
                    caseHistorique(ancien, reglages.get(t.getIdTrimestre()), d.cases)));
        }
        return List.copyOf(historique);
    }

    /** Case d'un trimestre passe ; null s'il n'a pas ete place ou si ses reglages manquent. */
    private static CaseNeufBox caseHistorique(Score ancien, Parametre parametre, CasesNeufBox cases) {
        if (ancien.getPositionBox() == null || parametre == null) {
            return null;
        }
        try {
            return cases.caseDe(ancien, parametre);
        } catch (DonneesIncompletesException e) {
            return null;
        }
    }

    // --- outils ----------------------------------------------------------------

    private static boolean avant(Trimestre t, Trimestre reference) {
        return t.getAnnee() < reference.getAnnee()
                || (t.getAnnee().equals(reference.getAnnee()) && t.getNumero() < reference.getNumero());
    }

    private static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }

    private static String nom(Enum<?> valeur) {
        return valeur == null ? null : valeur.name();
    }

    /** Donnees lues une fois et partagees par les blocs, et les manques constates. */
    private static final class Donnees {
        final Collaborateur collaborateur;
        final Trimestre trimestre;
        final List<String> manquantes = new ArrayList<>();
        Parametre parametre;
        Score score;
        /** Evaluation du manager (null si elle manque), et auto-evaluation (null sans elle). */
        Performance performance;
        Performance performanceAuto;
        Potentiel potentiel;
        Potentiel potentielAuto;
        List<CompetenceCollaborateur> competences;
        /** Postes critiques ou il est successeur identifie, poses par le bloc succession. */
        List<Poste> postesSuccesseur = List.of();
        /** Score du questionnaire d'engagement, pose par le bloc engagement (lu avant la vigilance). */
        BigDecimal engagement;
        CasesNeufBox cases;

        Donnees(Collaborateur collaborateur, Trimestre trimestre) {
            this.collaborateur = collaborateur;
            this.trimestre = trimestre;
        }

        void manque(String message) {
            manquantes.add(message);
        }
    }
}
