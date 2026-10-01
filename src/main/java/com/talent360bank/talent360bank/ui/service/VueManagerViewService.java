package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Alerte;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerVue;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assemble la vue d'un manager : les resultats de son equipe directe sur un
 * trimestre, et la liste des managers pour la choisir.
 *
 * <p>REGLE : aucun calcul ici, comme FicheCollaborateurViewService. Les
 * resultats de l'equipe et leur synthese viennent de
 * {@link ResultatsCollaborateurs}, partage avec la vue entite : les deux vues
 * donnent les memes chiffres pour les memes personnes.
 *
 * <p><strong>Donnees manquantes.</strong> Un bloc ou une valeur sans donnees
 * reste null et une phrase s'ajoute a donneesManquantes (une fois par cause) ;
 * seuls un trimestre, un matricule inconnu ou un collaborateur qui n'est pas
 * manager levent RessourceIntrouvableException (404).
 *
 * <p><strong>Requetes.</strong> Un nombre fixe, quelle que soit la taille de
 * l'equipe (voir ResultatsCollaborateurs).
 *
 * <p><strong>Equipe.</strong> Les collaborateurs dont il est le manager direct,
 * hors ARCHIVE (sortis de l'effectif) ; les INACTIF restent dans l'effectif.
 */
@Service
public class VueManagerViewService {

    private final TrimestreRepository trimestreRepository;
    private final ManagerRepository managerRepository;
    private final CollaborateurRepository collaborateurRepository;
    private final ParametreRepository parametreRepository;
    private final ScoreRepository scoreRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final NeufBoxService neufBoxService;
    private final ResultatsCollaborateurs resultats;

    public VueManagerViewService(TrimestreRepository trimestreRepository,
                                 ManagerRepository managerRepository,
                                 CollaborateurRepository collaborateurRepository,
                                 ParametreRepository parametreRepository,
                                 ScoreRepository scoreRepository,
                                 Matrice9BoxRepository matrice9BoxRepository,
                                 NeufBoxService neufBoxService,
                                 ResultatsCollaborateurs resultats) {
        this.trimestreRepository = trimestreRepository;
        this.managerRepository = managerRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.parametreRepository = parametreRepository;
        this.scoreRepository = scoreRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.neufBoxService = neufBoxService;
        this.resultats = resultats;
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
        return managers();
    }

    /** Tous les managers avec la taille de leur equipe directe (hors archives), par nom ; deux requetes. */
    List<ManagerResume> managers() {
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
            manquantes.add(aucunReglage(trimestre));
        }
        CasesNeufBox cases = new CasesNeufBox(neufBoxService, matrice9BoxRepository.findAll());

        // TODO N-2 hierarchy pending client answer: equipe directe seulement ; si le manager doit
        // aussi voir les equipes de ses managers directs, elargir ici la requete d'equipe.
        List<Collaborateur> equipe = collaborateurRepository.findEquipeDirecte(manager, StatutCollaborateur.ARCHIVE);
        ResultatsCollaborateurs.Groupe groupe = resultats.charger(equipe, trimestre, parametre, cases, manquantes);

        List<Alerte> alertes = new ArrayList<>();
        for (Membre membre : groupe.membres()) {
            alertes.addAll(alertes(membre, groupe.evaluePerformance(membre.matricule()),
                    groupe.evaluePotentiel(membre.matricule())));
        }
        Score scoreManager = scoreRepository
                .findByCollaborateurIdCollaborateurAndTrimestre(manager.getIdCollaborateur(), trimestre)
                .orElse(null);

        return new VueManager(
                new TrimestreFiche(annee, numero, libelle(trimestre), trimestre.getDateReference()),
                managerVue(manager, scoreManager, parametre, cases, manquantes),
                ResultatsCollaborateurs.synthese(groupe.membres(), cases),
                groupe.membres(),
                List.copyOf(alertes),
                null,
                List.copyOf(manquantes));
    }

    /** Phrase de donneesManquantes quand le trimestre n'a pas de reglages (vues manager et entite). */
    static String aucunReglage(Trimestre trimestre) {
        return "Aucun réglage pour " + libelle(trimestre)
                + " : catégories, case 9-box, talent et vigilance ne sont pas calculables";
    }

    // --- manager ---------------------------------------------------------------

    private static ManagerVue managerVue(Manager manager, Score score, Parametre parametre, CasesNeufBox cases,
                                         Set<String> manquantes) {
        Collaborateur collaborateur = manager.getCollaborateur();
        CaseNeufBox case9Box = null;
        if (score == null || score.getPositionBox() == null) {
            manquantes.add("Pas de case 9-box pour le manager sur ce trimestre");
        } else if (parametre != null) {
            case9Box = ResultatsCollaborateurs.caseOuNull(score, parametre, cases, manquantes);
        }
        return new ManagerVue(collaborateur.getIdCollaborateur(), collaborateur.getNom(), collaborateur.getPrenom(),
                collaborateur.getFonction(), EntiteFiche.de(collaborateur.getEntite()), case9Box);
    }

    // --- alertes ---------------------------------------------------------------

    private static List<Alerte> alertes(Membre membre, boolean evaluePerformance, boolean evaluePotentiel) {
        List<Alerte> alertes = new ArrayList<>();
        String nom = membre.prenom() + " " + membre.nom();
        if (ResultatsCollaborateurs.vigilanceElevee(membre)) {
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

    // --- outils ----------------------------------------------------------------

    private Trimestre exigerTrimestre(int annee, int numero) {
        return trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre T" + numero + " " + annee));
    }

    static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
