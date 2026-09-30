package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.NiveauGrille;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.SuccessionService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Critere;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Evaluation;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.HistoriqueTrimestre;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Identite;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.ManagerFiche;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assemble la fiche d'un collaborateur sur un trimestre : tout ce que le RH
 * doit voir, en un appel.
 *
 * <p>REGLE : aucun calcul ici. Scores, categories et case 9-box sont ceux
 * enregistres par ScoreService et NeufBoxService ; talent, gap de competence,
 * vigilance et matching viennent des methodes du moteur (TalentService,
 * CompetenceCollaborateurService, VigilanceService, SuccessionService).
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

    private final CollaborateurRepository collaborateurRepository;
    private final TrimestreRepository trimestreRepository;
    private final ParametreRepository parametreRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final ValidationComiteRepository validationComiteRepository;
    private final AppartenanceVivierRepository appartenanceVivierRepository;
    private final SuccesseurIdentifieRepository successeurIdentifieRepository;
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

    public FicheCollaborateurViewService(CollaborateurRepository collaborateurRepository,
                                         TrimestreRepository trimestreRepository,
                                         ParametreRepository parametreRepository,
                                         PerformanceRepository performanceRepository,
                                         PotentielRepository potentielRepository,
                                         QuestionnaireEngagementRepository questionnaireRepository,
                                         ValidationComiteRepository validationComiteRepository,
                                         AppartenanceVivierRepository appartenanceVivierRepository,
                                         SuccesseurIdentifieRepository successeurIdentifieRepository,
                                         CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                         Matrice9BoxRepository matrice9BoxRepository,
                                         ScoreService scoreService,
                                         CalculService calculService,
                                         NeufBoxService neufBoxService,
                                         TalentService talentService,
                                         VigilanceService vigilanceService,
                                         SuccessionService successionService,
                                         CompetenceCollaborateurService competenceCollaborateurService,
                                         VivierThematiqueService vivierThematiqueService) {
        this.collaborateurRepository = collaborateurRepository;
        this.trimestreRepository = trimestreRepository;
        this.parametreRepository = parametreRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.validationComiteRepository = validationComiteRepository;
        this.appartenanceVivierRepository = appartenanceVivierRepository;
        this.successeurIdentifieRepository = successeurIdentifieRepository;
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
        d.performance = performanceRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre).orElse(null);
        d.potentiel = potentielRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre).orElse(null);
        d.competences = competenceCollaborateurRepository.findByCollaborateurAvecCompetence(collaborateur);
        d.cases = matrice9BoxRepository.findAll().stream()
                .collect(Collectors.toMap(FicheCollaborateurViewService::cle, Matrice9Box::getCategorie, (a, b) -> a));

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
                historique(d),
                null,
                List.copyOf(d.manquantes));
    }

    // --- identite ------------------------------------------------------------

    private Identite identite(Collaborateur collaborateur, Trimestre trimestre) {
        Manager manager = collaborateur.getManager();
        ManagerFiche managerFiche = manager == null || manager.getCollaborateur() == null ? null
                : new ManagerFiche(manager.getIdCollaborateur(), manager.getCollaborateur().getNomComplet());
        return new Identite(collaborateur.getIdCollaborateur(), collaborateur.getNom(), collaborateur.getPrenom(),
                collaborateur.getFonction(), collaborateur.getGrade(), entite(collaborateur.getEntite()),
                managerFiche, nom(collaborateur.getStatut()), collaborateur.getDateEntree(),
                successionService.ancienneteEnAnnees(collaborateur, trimestre.getDateReference()));
    }

    private static EntiteFiche entite(Entite entite) {
        if (entite == null) {
            return null;
        }
        LinkedList<String> chemin = new LinkedList<>();
        for (Entite niveau = entite; niveau != null; niveau = niveau.getParent()) {
            chemin.addFirst(niveau.getLibelle());
        }
        return new EntiteFiche(entite.getCode(), entite.getLibelle(), nom(entite.getType()), List.copyOf(chemin));
    }

    // --- performance et potentiel ----------------------------------------------

    private Evaluation performance(Donnees d) {
        if (d.performance == null) {
            d.manque("Pas d'évaluation de performance pour ce trimestre");
            return null;
        }
        Performance p = d.performance;
        PoidsPerformance poids = d.parametre == null ? null : d.parametre.getPoidsPerformance();
        List<Critere> criteres = List.of(
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
            d.manque("Pas d'évaluation de potentiel pour ce trimestre");
            return null;
        }
        Potentiel p = d.potentiel;
        PoidsPotentiel poids = d.parametre == null ? null : d.parametre.getPoidsPotentiel();
        List<Critere> criteres = List.of(
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
            return caseDe(d.score, d.parametre, d.cases);
        } catch (DonneesIncompletesException e) {
            d.manque("Case 9-box : " + e.getMessage());
            return null;
        }
    }

    /**
     * Case d'un score place : numero deduit des niveaux par la regle du placement
     * (NeufBoxService.niveauxDe, reglages du trimestre du score), jamais du libelle
     * enregistre ; libelle actuel de cette case dans la matrice (un renommage s'y
     * voit), a defaut celui enregistre.
     *
     * @throws DonneesIncompletesException si un score ou les seuils manquent
     */
    private CaseNeufBox caseDe(Score score, Parametre parametre, Map<String, String> cases) {
        NiveauGrille[] niveaux = neufBoxService.niveauxDe(score, parametre);
        String libelle = cases.getOrDefault(cle(niveaux[0].getRang(), niveaux[1].getRang()), score.getPositionBox());
        return new CaseNeufBox(NeufBoxService.numeroCase(niveaux[0], niveaux[1]), libelle);
    }

    private static String cle(Matrice9Box case9Box) {
        return cle(case9Box.getNiveauPerformance(), case9Box.getNiveauPotentiel());
    }

    private static String cle(int niveauPerformance, int niveauPotentiel) {
        return niveauPerformance + "/" + niveauPotentiel;
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
            d.manque("Pas de questionnaire d'engagement pour ce trimestre");
        }
        return score;
    }

    private Vigilance vigilance(Donnees d) {
        if (d.parametre == null) {
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
                successions.add(new Succession(poste.getPosteId(), poste.getNomPoste(), poste.getDirection(),
                        poste.getCriticite(), matching.scoreMatching(), matching.readiness().name(),
                        matching.readiness().getLibelle()));
            }
        } catch (DonneesIncompletesException e) {
            d.manque("Matching succession : " + e.getMessage());
            return List.of();
        }
        return List.copyOf(successions);
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
    private CaseNeufBox caseHistorique(Score ancien, Parametre parametre, Map<String, String> cases) {
        if (ancien.getPositionBox() == null || parametre == null) {
            return null;
        }
        try {
            return caseDe(ancien, parametre, cases);
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
        Performance performance;
        Potentiel potentiel;
        List<CompetenceCollaborateur> competences;
        /** Libelle actuel de chaque case, par "niveau performance/niveau potentiel". */
        Map<String, String> cases;

        Donnees(Collaborateur collaborateur, Trimestre trimestre) {
            this.collaborateur = collaborateur;
            this.trimestre = trimestre;
        }

        void manque(String message) {
            manquantes.add(message);
        }
    }
}
