package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne.Ligne;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne.ManagerEnRetard;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Avancement de la campagne d'evaluation d'un trimestre (ecran Campagne).
 *
 * <p>REGLE : aucun calcul. Un collaborateur actif est evalue quand son manager
 * a note sa performance ET son potentiel ce trimestre (source MANAGER) : la
 * regle de l'alerte "Evaluation du manager manquante", si bien que
 * {@code nbManquants} est le nombre de ces alertes. Ici, seulement compter et
 * regrouper par direction (ou par entite fille d'une entite demandee).
 *
 * <p>Seules les donnees importees sont suivies : le classeur n'a ni dates ni
 * statut de campagne, et ne contient aucune auto-evaluation (l'import ecrit
 * des evaluations MANAGER) ; c'est dit dans {@code remarques} plutot qu'invente.
 *
 * <p>Six lectures quelle que soit la population (sept avec une entite).
 */
@Service
public class SuiviCampagneViewService {

    private static final int PRECISION_POURCENTAGE = 1;

    private final TrimestreRepository trimestreRepository;
    private final CollaborateurRepository collaborateurRepository;
    private final EntiteRepository entiteRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;

    public SuiviCampagneViewService(TrimestreRepository trimestreRepository,
                                    CollaborateurRepository collaborateurRepository,
                                    EntiteRepository entiteRepository, PerformanceRepository performanceRepository,
                                    PotentielRepository potentielRepository) {
        this.trimestreRepository = trimestreRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.entiteRepository = entiteRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
    }

    /**
     * @param codeEntite null : une ligne par direction ; sinon une ligne par entite fille de celle-ci
     * @throws RessourceIntrouvableException si le trimestre ou l'entite est inconnu
     */
    public SuiviCampagne construire(int annee, int numero, String codeEntite) {
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre T" + numero + " " + annee));
        Entite demandee = codeEntite == null ? null : entiteRepository.findByCode(codeEntite)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucune entité " + codeEntite));

        List<Collaborateur> actifs = collaborateurRepository.findAllAvecManager().stream()
                .filter(Collaborateur::estCalculable)
                .filter(c -> demandee == null || chemin(c).stream().anyMatch(e -> e.getCode().equals(codeEntite)))
                .toList();
        Set<String> performance = new HashSet<>(
                performanceRepository.findMatriculesEvaluesDuTrimestre(trimestre, SourceEvaluation.MANAGER));
        Set<String> potentiel = new HashSet<>(
                potentielRepository.findMatriculesEvaluesDuTrimestre(trimestre, SourceEvaluation.MANAGER));
        Set<String> auto = new HashSet<>(
                performanceRepository.findMatriculesEvaluesDuTrimestre(trimestre, SourceEvaluation.AUTO));
        auto.addAll(potentielRepository.findMatriculesEvaluesDuTrimestre(trimestre, SourceEvaluation.AUTO));
        Comptage comptage = new Comptage(performance, potentiel, auto);

        Map<String, Groupe> groupes = new LinkedHashMap<>();
        for (Collaborateur collaborateur : actifs) {
            Groupe groupe = groupeDe(collaborateur, demandee);
            groupes.computeIfAbsent(groupe.cle(), cle -> groupe).membres().add(collaborateur);
        }
        List<Ligne> lignes = groupes.values().stream()
                .sorted(Comparator.comparing(Groupe::rang).thenComparing(Groupe::libelle))
                .map(groupe -> comptage.ligne(groupe.code(), groupe.libelle(), groupe.type(), groupe.membres()))
                .toList();

        Ligne total = comptage.ligne(demandee == null ? null : demandee.getCode(),
                demandee == null ? "Toute la banque" : demandee.getLibelle(),
                demandee == null ? null : nom(demandee.getType()), actifs);
        List<String> remarques = new ArrayList<>();
        remarques.add("Le classeur ne contient ni dates ni statut de campagne : seul l'avancement des évaluations "
                + "du manager est suivi.");
        if (total.nbAutoEvaluations() == 0) {
            remarques.add("Aucune auto-évaluation pour ce trimestre : l'import du classeur n'en contient pas, "
                    + "leur nombre reste 0 tant qu'aucune source ne les fournit.");
        }
        return new SuiviCampagne(new TrimestreFiche(annee, numero, TrimestreCourantService.libelle(trimestre),
                trimestre.getDateReference()),
                demandee == null ? null : new EntiteRef(demandee.getCode(), demandee.getLibelle(),
                        nom(demandee.getType())),
                total, lignes, List.copyOf(remarques));
    }

    /** Direction du collaborateur, ou l'entite fille de l'entite demandee dont il releve. */
    private static Groupe groupeDe(Collaborateur collaborateur, Entite demandee) {
        if (demandee == null) {
            Entite direction = collaborateur.getEntite() == null ? null
                    : collaborateur.getEntite().ancetre(TypeEntite.DIRECTION);
            return direction == null ? new Groupe(null, "Sans direction", null, 1, new ArrayList<>())
                    : new Groupe(direction.getCode(), direction.getLibelle(), nom(direction.getType()), 0,
                    new ArrayList<>());
        }
        List<Entite> chemin = chemin(collaborateur);
        for (int i = 0; i < chemin.size(); i++) {
            if (chemin.get(i).getCode().equals(demandee.getCode())) {
                if (i == 0) {
                    return new Groupe(demandee.getCode(), "Rattachés directement à " + demandee.getLibelle(),
                            nom(demandee.getType()), 1, new ArrayList<>());
                }
                Entite fille = chemin.get(i - 1);
                return new Groupe(fille.getCode(), fille.getLibelle(), nom(fille.getType()), 0, new ArrayList<>());
            }
        }
        throw new IllegalStateException("Collaborateur hors de l'entite demandee");
    }

    /** L'entite du collaborateur puis ses parents, de la plus fine a la direction. */
    private static List<Entite> chemin(Collaborateur collaborateur) {
        List<Entite> chemin = new ArrayList<>();
        for (Entite e = collaborateur.getEntite(); e != null; e = e.getParent()) {
            chemin.add(e);
        }
        return chemin;
    }

    private static String nom(Enum<?> valeur) {
        return valeur == null ? null : valeur.name();
    }

    /** @param rang 0 pour les entites, 1 pour "sans direction" / "rattaches directement" (en dernier) */
    private record Groupe(String code, String libelle, String type, int rang, List<Collaborateur> membres) {

        String cle() {
            return code == null ? "" : code + "|" + rang;
        }
    }

    /** Matricules evalues du trimestre, et les comptes d'un groupe. */
    private record Comptage(Set<String> performance, Set<String> potentiel, Set<String> auto) {

        boolean evalue(Collaborateur collaborateur) {
            String id = collaborateur.getIdCollaborateur();
            return performance.contains(id) && potentiel.contains(id);
        }

        Ligne ligne(String code, String libelle, String type, List<Collaborateur> membres) {
            int evalues = (int) membres.stream().filter(this::evalue).count();
            Map<String, List<String>> manquantsParManager = new LinkedHashMap<>();
            Map<String, String> nomsManagers = new LinkedHashMap<>();
            for (Collaborateur collaborateur : membres.stream()
                    .sorted(Comparator.comparing(Collaborateur::getIdCollaborateur)).toList()) {
                if (evalue(collaborateur)) {
                    continue;
                }
                Manager manager = collaborateur.getManager();
                String cle = manager == null ? "" : manager.getIdCollaborateur();
                nomsManagers.putIfAbsent(cle, manager == null || manager.getCollaborateur() == null
                        ? "Sans manager" : manager.getCollaborateur().getNomComplet());
                manquantsParManager.computeIfAbsent(cle, k -> new ArrayList<>())
                        .add(collaborateur.getIdCollaborateur());
            }
            List<ManagerEnRetard> managers = manquantsParManager.entrySet().stream()
                    .map(e -> new ManagerEnRetard(e.getKey().isEmpty() ? null : e.getKey(),
                            nomsManagers.get(e.getKey()), e.getValue().size(), List.copyOf(e.getValue())))
                    .sorted(Comparator.comparing(ManagerEnRetard::nbManquants, Comparator.reverseOrder())
                            .thenComparing(ManagerEnRetard::nom))
                    .toList();
            return new Ligne(code, libelle, type, membres.size(), evalues,
                    membres.isEmpty() ? null : BigDecimal.valueOf(evalues * 100L)
                            .divide(BigDecimal.valueOf(membres.size()), PRECISION_POURCENTAGE, RoundingMode.HALF_UP),
                    membres.size() - evalues,
                    (int) membres.stream().filter(c -> auto.contains(c.getIdCollaborateur())).count(),
                    managers);
        }
    }
}
