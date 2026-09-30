package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.CategoriePotentiel;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Alerte;
import com.talent360bank.talent360bank.ui.model.VueManager.Compte;
import com.talent360bank.talent360bank.ui.model.VueManager.CompteCase;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerVue;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import com.talent360bank.talent360bank.ui.model.VueManager.Synthese;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Assemble la vue d'un manager : les resultats de son equipe directe sur un
 * trimestre, et la liste des managers pour la choisir.
 *
 * <p>REGLE : aucun calcul ici, comme FicheCollaborateurViewService. Scores et
 * categories sont ceux enregistres ; case 9-box (regle du placement), talent,
 * vigilance viennent des methodes du moteur. Seules les syntheses (compter,
 * moyenner) sont faites ici.
 *
 * <p><strong>Donnees manquantes.</strong> Un bloc ou une valeur sans donnees
 * reste null et une phrase s'ajoute a donneesManquantes (une fois par cause) ;
 * seuls un trimestre, un matricule inconnu ou un collaborateur qui n'est pas
 * manager levent RessourceIntrouvableException (404). Les blocs n'appellent
 * que des methodes sans transaction propre (voir FicheCollaborateurViewService).
 *
 * <p><strong>Requetes.</strong> Un nombre fixe, quelle que soit la taille de
 * l'equipe : chaque donnee de l'equipe est lue en une requete (IN sur les
 * matricules), entite et parents charges avec les collaborateurs.
 *
 * <p><strong>Equipe.</strong> Les collaborateurs dont il est le manager direct,
 * hors ARCHIVE (sortis de l'effectif) ; les INACTIF restent dans l'effectif.
 */
@Service
public class VueManagerViewService {

    private static final int PRECISION_MOYENNE = 2;

    private final TrimestreRepository trimestreRepository;
    private final ManagerRepository managerRepository;
    private final CollaborateurRepository collaborateurRepository;
    private final ParametreRepository parametreRepository;
    private final ScoreRepository scoreRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final CalculService calculService;
    private final NeufBoxService neufBoxService;
    private final TalentService talentService;
    private final VigilanceService vigilanceService;
    private final EntreesVigilance entreesVigilance;

    public VueManagerViewService(TrimestreRepository trimestreRepository,
                                 ManagerRepository managerRepository,
                                 CollaborateurRepository collaborateurRepository,
                                 ParametreRepository parametreRepository,
                                 ScoreRepository scoreRepository,
                                 PerformanceRepository performanceRepository,
                                 PotentielRepository potentielRepository,
                                 QuestionnaireEngagementRepository questionnaireRepository,
                                 Matrice9BoxRepository matrice9BoxRepository,
                                 CalculService calculService,
                                 NeufBoxService neufBoxService,
                                 TalentService talentService,
                                 VigilanceService vigilanceService,
                                 EntreesVigilance entreesVigilance) {
        this.trimestreRepository = trimestreRepository;
        this.managerRepository = managerRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.parametreRepository = parametreRepository;
        this.scoreRepository = scoreRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.calculService = calculService;
        this.neufBoxService = neufBoxService;
        this.talentService = talentService;
        this.vigilanceService = vigilanceService;
        this.entreesVigilance = entreesVigilance;
    }

    /**
     * Managers pour la page de choix, par nom, avec la taille de leur equipe
     * directe (hors archives). Deux requetes, quel que soit leur nombre.
     *
     * @throws RessourceIntrouvableException si le trimestre est inconnu
     */
    @Transactional(readOnly = true)
    public List<ManagerResume> listerManagers(int annee, int numero) {
        exigerTrimestre(annee, numero);
        Map<Integer, Integer> tailles = new HashMap<>();
        for (Object[] ligne : collaborateurRepository.compterEquipesDirectes(StatutCollaborateur.ARCHIVE)) {
            tailles.put((Integer) ligne[0], ((Number) ligne[1]).intValue());
        }
        List<ManagerResume> managers = new ArrayList<>();
        for (Manager manager : managerRepository.findAllAvecEntite()) {
            Collaborateur collaborateur = manager.getCollaborateur();
            managers.add(new ManagerResume(collaborateur.getIdCollaborateur(), collaborateur.getNom(),
                    collaborateur.getPrenom(), collaborateur.getFonction(), EntiteFiche.de(collaborateur.getEntite()),
                    tailles.getOrDefault(manager.getIdManager(), 0)));
        }
        return List.copyOf(managers);
    }

    /**
     * @throws RessourceIntrouvableException si le trimestre ou le matricule est
     *                                       inconnu, ou si ce collaborateur n'est pas manager
     */
    @Transactional(readOnly = true)
    public VueManager construire(String matriculeManager, int annee, int numero) {
        Trimestre trimestre = exigerTrimestre(annee, numero);
        Manager manager = managerRepository.findByMatriculeAvecEntite(matriculeManager)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        collaborateurRepository.existsById(matriculeManager)
                                ? "Le collaborateur " + matriculeManager + " n'est pas manager"
                                : "Aucun collaborateur " + matriculeManager));

        Set<String> manquantes = new LinkedHashSet<>();
        Parametre parametre = parametreRepository.findByTrimestre(trimestre).orElse(null);
        if (parametre == null) {
            manquantes.add("Aucun réglage pour " + libelle(trimestre)
                    + " : catégories, case 9-box, talent et vigilance ne sont pas calculables");
        }
        CasesNeufBox cases = new CasesNeufBox(neufBoxService, matrice9BoxRepository.findAll());

        // TODO N-2 hierarchy pending client answer: equipe directe seulement ; si le manager doit
        // aussi voir les equipes de ses managers directs, elargir ici la requete d'equipe.
        List<Collaborateur> equipe = collaborateurRepository.findEquipeDirecte(manager, StatutCollaborateur.ARCHIVE);
        List<String> idsEquipe = equipe.stream().map(Collaborateur::getIdCollaborateur).toList();

        // Donnees de l'equipe (et le score du manager), une requete chacune.
        List<String> idsAvecManager = new ArrayList<>(idsEquipe);
        idsAvecManager.add(manager.getIdCollaborateur());
        Map<String, Score> scores = scoreRepository.findByTrimestreEtCollaborateurs(trimestre, idsAvecManager).stream()
                .collect(Collectors.toMap(s -> s.getCollaborateur().getIdCollaborateur(), Function.identity()));
        Map<String, QuestionnaireEngagement> engagements = new HashMap<>();
        Set<String> avecPerformance = new HashSet<>();
        Set<String> avecPotentiel = new HashSet<>();
        if (!idsEquipe.isEmpty()) {
            questionnaireRepository.findByTrimestreEtCollaborateurs(trimestre, idsEquipe)
                    .forEach(q -> engagements.put(q.getCollaborateur().getIdCollaborateur(), q));
            avecPerformance.addAll(performanceRepository.findMatriculesEvalues(trimestre, idsEquipe));
            avecPotentiel.addAll(potentielRepository.findMatriculesEvalues(trimestre, idsEquipe));
        }
        Map<String, ResultatVigilance> vigilances = vigilances(equipe, trimestre, parametre, scores, engagements,
                avecPerformance, avecPotentiel, manquantes);

        List<Membre> membres = new ArrayList<>();
        List<Alerte> alertes = new ArrayList<>();
        for (Collaborateur collaborateur : equipe) {
            String id = collaborateur.getIdCollaborateur();
            Membre membre = membre(collaborateur, scores.get(id), parametre, cases, engagements.get(id),
                    vigilances.get(id), avecPerformance.contains(id), avecPotentiel.contains(id), manquantes);
            membres.add(membre);
            alertes.addAll(alertes(membre, avecPerformance.contains(id), avecPotentiel.contains(id)));
        }

        return new VueManager(
                new TrimestreFiche(annee, numero, libelle(trimestre), trimestre.getDateReference()),
                managerVue(manager, scores.get(manager.getIdCollaborateur()), parametre, cases, manquantes),
                synthese(membres, cases),
                List.copyOf(membres),
                List.copyOf(alertes),
                null,
                List.copyOf(manquantes));
    }

    // --- manager ---------------------------------------------------------------

    private static ManagerVue managerVue(Manager manager, Score score, Parametre parametre, CasesNeufBox cases,
                                         Set<String> manquantes) {
        Collaborateur collaborateur = manager.getCollaborateur();
        CaseNeufBox case9Box = null;
        if (score == null || score.getPositionBox() == null) {
            manquantes.add("Pas de case 9-box pour le manager sur ce trimestre");
        } else if (parametre != null) {
            case9Box = caseOuNull(score, parametre, cases, manquantes);
        }
        return new ManagerVue(collaborateur.getIdCollaborateur(), collaborateur.getNom(), collaborateur.getPrenom(),
                collaborateur.getFonction(), EntiteFiche.de(collaborateur.getEntite()), case9Box);
    }

    // --- membres ---------------------------------------------------------------

    /**
     * Vigilance des membres qui ont au moins une donnee de vigilance ; les
     * autres restent sans vigilance (EntreesVigilance) et sont nommes dans
     * donneesManquantes.
     */
    private Map<String, ResultatVigilance> vigilances(List<Collaborateur> equipe, Trimestre trimestre,
                                                      Parametre parametre, Map<String, Score> scores,
                                                      Map<String, QuestionnaireEngagement> engagements,
                                                      Set<String> avecPerformance, Set<String> avecPotentiel,
                                                      Set<String> manquantes) {
        if (parametre == null || equipe.isEmpty()) {
            return Map.of();
        }
        Set<String> sansDonnee = entreesVigilance.sansDonnee(
                equipe.stream().map(Collaborateur::getIdCollaborateur).toList(), trimestre,
                id -> engagements.containsKey(id) && engagements.get(id).getScoreEngagement() != null,
                id -> avecPerformance.contains(id) || avecPotentiel.contains(id));
        if (!sansDonnee.isEmpty()) {
            manquantes.add(EntreesVigilance.AUCUNE_DONNEE + " : " + equipe.stream()
                    .filter(c -> sansDonnee.contains(c.getIdCollaborateur()))
                    .map(c -> c.getPrenom() + " " + c.getNom() + " (" + c.getIdCollaborateur() + ")")
                    .collect(Collectors.joining(", ")));
        }
        List<Collaborateur> evaluables = equipe.stream()
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
            manquantes.add("Scores non calculés pour une partie de l'équipe : lancer le calcul du trimestre");
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

    private static CaseNeufBox caseOuNull(Score score, Parametre parametre, CasesNeufBox cases,
                                          Set<String> manquantes) {
        try {
            return cases.caseDe(score, parametre);
        } catch (DonneesIncompletesException e) {
            manquantes.add("Case 9-box : " + e.getMessage());
            return null;
        }
    }

    // --- alertes ---------------------------------------------------------------

    private static List<Alerte> alertes(Membre membre, boolean evaluePerformance, boolean evaluePotentiel) {
        List<Alerte> alertes = new ArrayList<>();
        String nom = membre.prenom() + " " + membre.nom();
        if (membre.niveauVigilance() != null
                && NiveauVigilance.valueOf(membre.niveauVigilance()).compareTo(NiveauVigilance.ELEVEE) >= 0) {
            alertes.add(new Alerte(membre.matricule(), nom, "VIGILANCE_ELEVEE",
                    "Vigilance élevée : indice " + membre.indiceVigilance().stripTrailingZeros().toPlainString()
                            + " / 100"));
        }
        if (!evaluePerformance || !evaluePotentiel) {
            String manque = !evaluePerformance && !evaluePotentiel ? "performance ni de potentiel"
                    : !evaluePerformance ? "performance" : "potentiel";
            alertes.add(new Alerte(membre.matricule(), nom, "EVALUATION_MANQUANTE",
                    "Pas d'évaluation de " + manque + " pour ce trimestre"));
        }
        return alertes;
    }

    // --- synthese --------------------------------------------------------------

    private static Synthese synthese(List<Membre> membres, CasesNeufBox cases) {
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

    private static int compter(List<Membre> membres, Predicate<Membre> condition) {
        return (int) membres.stream().filter(condition).count();
    }

    /** Moyenne des valeurs connues, arrondie a 2 decimales ; null si aucune. */
    private static BigDecimal moyenne(List<Membre> membres, Function<Membre, BigDecimal> valeur) {
        List<BigDecimal> valeurs = membres.stream().map(valeur).filter(Objects::nonNull).toList();
        if (valeurs.isEmpty()) {
            return null;
        }
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valeurs.size()), PRECISION_MOYENNE, RoundingMode.HALF_UP);
    }

    // --- outils ----------------------------------------------------------------

    private Trimestre exigerTrimestre(int annee, int numero) {
        return trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre T" + numero + " " + annee));
    }

    private static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
