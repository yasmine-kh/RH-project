package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCibleService;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.VivierReleveService;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.ManagerFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Criteres;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.LigneCollaborateur;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.PosteCibleLigne;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Tri;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.VivierRef;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Liste des collaborateurs d'un trimestre (ecran Collaborateurs, Ima) : une
 * ligne par collaborateur actif, filtrable, triable et paginee.
 *
 * <p>REGLE : aucun calcul ici. Scores, case 9-box, talent, haut potentiel et
 * vigilance viennent de {@link ResultatsCollaborateurs} (comme les vues
 * manager et entite) ; la decision du comite de {@link ValidationComiteService} ;
 * le vivier thematique de {@link VivierThematiqueService} ; le poste cible de
 * {@link PosteCibleService}. Ici, seulement l'assemblage, puis les filtres,
 * le tri et la pagination.
 *
 * <p>Perimetre : les collaborateurs actifs (statut ACTIF), ceux que le moteur
 * calcule, comme le chiffre "Collaborateurs actifs" du tableau de bord.
 *
 * <p><strong>Requetes.</strong> Un nombre fixe quelle que soit la population :
 * tout le trimestre est lu en lots, puis filtre en memoire. Sans transaction
 * propre : chaque service appele a la sienne, si bien qu'une donnee manquante
 * dans un bloc (reglages incomplets...) laisse ce bloc vide sans faire echouer
 * la liste ; les relations utiles sont chargees par les requetes elles-memes.
 */
@Service
public class ListeCollaborateursViewService {

    private final TrimestreRepository trimestreRepository;
    private final CollaborateurRepository collaborateurRepository;
    private final ParametreRepository parametreRepository;
    private final ScoreRepository scoreRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final NeufBoxService neufBoxService;
    private final ResultatsCollaborateurs resultats;
    private final ValidationComiteService validationComiteService;
    private final VivierThematiqueService vivierThematiqueService;
    private final PosteCibleService posteCibleService;

    public ListeCollaborateursViewService(TrimestreRepository trimestreRepository,
                                          CollaborateurRepository collaborateurRepository,
                                          ParametreRepository parametreRepository,
                                          ScoreRepository scoreRepository,
                                          Matrice9BoxRepository matrice9BoxRepository,
                                          NeufBoxService neufBoxService,
                                          ResultatsCollaborateurs resultats,
                                          ValidationComiteService validationComiteService,
                                          VivierThematiqueService vivierThematiqueService,
                                          PosteCibleService posteCibleService) {
        this.trimestreRepository = trimestreRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.parametreRepository = parametreRepository;
        this.scoreRepository = scoreRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.neufBoxService = neufBoxService;
        this.resultats = resultats;
        this.validationComiteService = validationComiteService;
        this.vivierThematiqueService = vivierThematiqueService;
        this.posteCibleService = posteCibleService;
    }

    /**
     * @throws RessourceIntrouvableException si le trimestre est inconnu
     * @throws IllegalArgumentException      si un critere est invalide (page, taille, case, code inconnu)
     */
    public ListeCollaborateurs construire(int annee, int numero, Criteres criteres) {
        verifier(criteres);
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre T" + numero + " " + annee));
        Population population = population(trimestre);
        return paginer(new TrimestreFiche(annee, numero, VueManagerViewService.libelle(trimestre),
                        trimestre.getDateReference()), population.lignes(), population.collaborateurs(), criteres,
                population.manquantes());
    }

    /**
     * Toute la population calculee du trimestre, sans filtre ni pagination : les lignes de la liste
     * et, pour chacune, le resultat du moteur dont elle est tiree (categories, engagement, releve).
     * Le tableau de bord interactif filtre ces lignes lui-meme. Meme nombre fixe de requetes.
     *
     * @param membres par matricule, le resultat de ResultatsCollaborateurs
     */
    public record Population(List<LigneCollaborateur> lignes, Map<String, Membre> membres,
                             Map<String, Collaborateur> collaborateurs, List<String> manquantes) {
    }

    public Population population(Trimestre trimestre) {
        Set<String> manquantes = new LinkedHashSet<>();
        Parametre parametre = parametreRepository.findByTrimestre(trimestre).orElse(null);
        if (parametre == null) {
            manquantes.add(VueManagerViewService.aucunReglage(trimestre));
        }
        CasesNeufBox cases = new CasesNeufBox(neufBoxService, matrice9BoxRepository.findAll());

        List<Collaborateur> collaborateurs = collaborateurRepository.findAllAvecManager().stream()
                .filter(Collaborateur::estCalculable)
                .toList();
        List<Membre> membres = resultats.charger(collaborateurs, trimestre, parametre, cases, manquantes).membres();

        Map<String, String> decisions = new HashMap<>();
        Map<String, VivierThematique> viviers = new HashMap<>();
        Map<String, ResultatPosteCible> cibles = new HashMap<>();
        if (parametre != null) {
            decisions = decisions(membres, trimestre, manquantes);
            viviers = viviersThematiques(trimestre, manquantes);
            cibles = postesCibles(trimestre, manquantes);
        }

        Map<String, Collaborateur> parMatricule = collaborateurs.stream()
                .collect(Collectors.toMap(Collaborateur::getIdCollaborateur, Function.identity()));
        List<LigneCollaborateur> lignes = new ArrayList<>();
        for (Membre membre : membres) {
            String id = membre.matricule();
            lignes.add(ligne(parMatricule.get(id), membre, decisions.get(id), viviers.get(id), cibles.get(id)));
        }

        return new Population(List.copyOf(lignes), membres.stream()
                .collect(Collectors.toMap(Membre::matricule, Function.identity(), (x, y) -> x, java.util.LinkedHashMap::new)),
                parMatricule, List.copyOf(manquantes));
    }

    // --- lecture ------------------------------------------------------------------

    /** Decision du comite de chaque talent propose (10_TALENTS!G), en une lecture. */
    private Map<String, String> decisions(List<Membre> membres, Trimestre trimestre, Set<String> manquantes) {
        Set<String> talents = membres.stream().filter(m -> Boolean.TRUE.equals(m.estTalent()))
                .map(Membre::matricule).collect(Collectors.toSet());
        if (talents.isEmpty()) {
            return Map.of();
        }
        List<Score> scoresTalents = scoreRepository.findByTrimestreAvecCollaborateur(trimestre).stream()
                .filter(score -> talents.contains(score.getCollaborateur().getIdCollaborateur()))
                .toList();
        try {
            return validationComiteService.deciderPour(scoresTalents, trimestre).stream()
                    .collect(Collectors.toMap(d -> d.score().getCollaborateur().getIdCollaborateur(),
                            d -> d.statut().name()));
        } catch (RuntimeException e) {
            manquantes.add("Décisions du Comité Talent : " + e.getMessage());
            return Map.of();
        }
    }

    private Map<String, VivierThematique> viviersThematiques(Trimestre trimestre, Set<String> manquantes) {
        Map<String, VivierThematique> viviers = new HashMap<>();
        try {
            vivierThematiqueService.getViviersThematiques(trimestre).membres().forEach((vivier, membres) -> {
                for (MembreVivierThematique membre : membres) {
                    viviers.put(membre.score().getCollaborateur().getIdCollaborateur(), vivier);
                }
            });
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            manquantes.add("Viviers thématiques : " + e.getMessage());
        }
        return viviers;
    }

    private Map<String, ResultatPosteCible> postesCibles(Trimestre trimestre, Set<String> manquantes) {
        try {
            return posteCibleService.postesCibles(trimestre).stream()
                    .collect(Collectors.toMap(c -> c.collaborateur().getIdCollaborateur(), Function.identity()));
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            manquantes.add("Poste cible : " + e.getMessage());
            return Map.of();
        }
    }

    private static LigneCollaborateur ligne(Collaborateur collaborateur, Membre membre, String decision,
                                            VivierThematique vivier, ResultatPosteCible cible) {
        List<VivierRef> viviers = new ArrayList<>();
        if (vivier != null) {
            viviers.add(new VivierRef(vivier.getCode(), vivier.getLibelle()));
        }
        if (Boolean.TRUE.equals(membre.estVivierSuccession())) {
            viviers.add(new VivierRef(VivierReleveService.CODE_VIVIER_RELEVE, VivierReleveService.NOM_VIVIER_RELEVE));
        }
        Manager manager = collaborateur.getManager();
        ManagerFiche managerFiche = manager == null || manager.getCollaborateur() == null ? null
                : new ManagerFiche(manager.getIdCollaborateur(), manager.getCollaborateur().getNomComplet());
        Entite entite = collaborateur.getEntite();
        // Regle 10_TALENTS!H : seul un talent propose peut etre valide, par la decision Oui.
        boolean valide = Boolean.TRUE.equals(membre.estTalent()) && "OUI".equals(decision);

        return new LigneCollaborateur(membre.matricule(), membre.nom(), membre.prenom(),
                collaborateur.getNomComplet(),
                entite == null ? null : new EntiteRef(entite.getCode(), entite.getLibelle(),
                        entite.getType() == null ? null : entite.getType().name()),
                collaborateur.getDirection(), managerFiche, collaborateur.getFonction(), collaborateur.getGrade(),
                membre.scorePerformance(), membre.scorePotentiel(), membre.neufBox(), membre.estTalent(),
                membre.estHautPotentiel(), valide, decision, List.copyOf(viviers), posteCible(cible),
                membre.indiceVigilance(), membre.niveauVigilance(), membre.niveauVigilanceLibelle());
    }

    /** Poste cible tel que la liste et le tableau de bord DG l'affichent ; null sans poste cible. */
    static PosteCibleLigne posteCible(ResultatPosteCible cible) {
        if (cible == null) {
            return null;
        }
        ResultatMatching matching = cible.matching();
        PlusGrandGap gap = matching.plusGrandGap() != null && matching.plusGrandGap().aUnEcart()
                ? matching.plusGrandGap() : null;
        return new PosteCibleLigne(cible.poste().getPosteId(), cible.poste().getNomPoste(),
                matching.scoreMatching(), matching.readiness().name(), matching.readiness().getLibelle(),
                cible.successeurIdentifie(), gap == null ? null : gap.competence(), gap == null ? 0 : gap.ecart());
    }

    // --- filtres, tri, pagination (sans acces base) -------------------------------------

    /**
     * Applique les criteres a des lignes deja construites.
     *
     * @param collaborateurs les collaborateurs des lignes, par matricule : leur entite et ses
     *                       parents servent au filtre par entite (sous-arbre)
     */
    static ListeCollaborateurs paginer(TrimestreFiche trimestre, List<LigneCollaborateur> lignes,
                                       Map<String, Collaborateur> collaborateurs, Criteres criteres,
                                       List<String> manquantes) {
        List<LigneCollaborateur> filtrees = lignes.stream()
                .filter(filtre(criteres, collaborateurs))
                .sorted(ordre(criteres))
                .toList();
        int nbPages = Math.max(1, (filtrees.size() + criteres.taille() - 1) / criteres.taille());
        int debut = Math.min(filtrees.size(), (criteres.page() - 1) * criteres.taille());
        int fin = Math.min(filtrees.size(), debut + criteres.taille());
        return new ListeCollaborateurs(trimestre, criteres, lignes.size(), filtrees.size(), nbPages,
                List.copyOf(filtrees.subList(debut, fin)), manquantes);
    }

    private static Predicate<LigneCollaborateur> filtre(Criteres c, Map<String, Collaborateur> collaborateurs) {
        Predicate<LigneCollaborateur> filtre = ligne -> true;
        if (c.entite() != null) {
            filtre = filtre.and(ligne -> dansLeSousArbre(collaborateurs.get(ligne.matricule()), c.entite()));
        }
        if (c.caseNeufBox() != null) {
            filtre = filtre.and(ligne -> ligne.neufBox() != null && ligne.neufBox().numero() == c.caseNeufBox());
        }
        if (c.talent() != null) {
            filtre = filtre.and(ligne -> c.talent().equals(Boolean.TRUE.equals(ligne.estTalent())));
        }
        if (c.vivier() != null) {
            filtre = filtre.and(ligne -> ligne.viviers().stream().anyMatch(v -> v.code().equals(c.vivier())));
        }
        if (c.readiness() != null) {
            filtre = filtre.and(ligne -> ligne.posteCible() != null
                    && c.readiness().equals(ligne.posteCible().readiness()));
        }
        if (c.vigilance() != null) {
            filtre = filtre.and(ligne -> c.vigilance().equals(ligne.niveauVigilance()));
        }
        if (c.recherche() != null && !c.recherche().isBlank()) {
            String texte = sansAccents(c.recherche());
            filtre = filtre.and(ligne -> sansAccents(ligne.nom()).contains(texte)
                    || sansAccents(ligne.prenom()).contains(texte)
                    || sansAccents(ligne.nomComplet()).contains(texte)
                    || sansAccents(ligne.matricule()).contains(texte));
        }
        return filtre;
    }

    /** Vrai si l'entite du collaborateur est celle du code ou l'une de ses descendantes. */
    private static boolean dansLeSousArbre(Collaborateur collaborateur, String code) {
        for (Entite e = collaborateur == null ? null : collaborateur.getEntite(); e != null; e = e.getParent()) {
            if (code.equals(e.getCode())) {
                return true;
            }
        }
        return false;
    }

    /** Tri demande, valeurs inconnues en dernier quel que soit le sens, puis matricule. */
    private static Comparator<LigneCollaborateur> ordre(Criteres c) {
        Comparator<LigneCollaborateur> parMatricule = Comparator.comparing(LigneCollaborateur::matricule);
        Tri tri = c.tri() == null ? Tri.NOM : c.tri();
        Comparator<LigneCollaborateur> principal = switch (tri) {
            case NOM -> Comparator.comparing((LigneCollaborateur l) -> sansAccents(l.nom()))
                    .thenComparing(l -> sansAccents(l.prenom()));
            case MATRICULE -> parMatricule;
            case PERFORMANCE -> valeur(LigneCollaborateur::scorePerformance, c.decroissant());
            case POTENTIEL -> valeur(LigneCollaborateur::scorePotentiel, c.decroissant());
            case MATCHING -> valeur(l -> l.posteCible() == null ? null : l.posteCible().scoreMatching(),
                    c.decroissant());
            case VIGILANCE -> valeur(LigneCollaborateur::indiceVigilance, c.decroissant());
        };
        boolean textuel = tri == Tri.NOM || tri == Tri.MATRICULE;
        if (textuel && c.decroissant()) {
            principal = principal.reversed();
        }
        return principal.thenComparing(parMatricule);
    }

    private static Comparator<LigneCollaborateur> valeur(Function<LigneCollaborateur, BigDecimal> valeur,
                                                         boolean decroissant) {
        Comparator<BigDecimal> sens = decroissant ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(valeur, Comparator.nullsLast(sens));
    }

    /** Refuse les criteres hors bornes ou les codes inconnus (400) plutot que de rendre une liste vide. */
    static void verifier(Criteres c) {
        if (c.page() < 1) {
            throw new IllegalArgumentException("La page commence a 1");
        }
        if (c.taille() < 1 || c.taille() > Criteres.TAILLE_MAX) {
            throw new IllegalArgumentException("La taille de page doit etre entre 1 et " + Criteres.TAILLE_MAX);
        }
        if (c.caseNeufBox() != null && (c.caseNeufBox() < 1 || c.caseNeufBox() > 9)) {
            throw new IllegalArgumentException("La case 9-box doit etre entre 1 et 9");
        }
        connu("readiness", c.readiness(), Arrays.stream(NiveauReadiness.values()).map(Enum::name).toList());
        connu("vigilance", c.vigilance(), Arrays.stream(NiveauVigilance.values()).map(Enum::name).toList());
        List<String> viviers = new ArrayList<>(Arrays.stream(VivierThematique.values())
                .map(VivierThematique::getCode).toList());
        viviers.add(VivierReleveService.CODE_VIVIER_RELEVE);
        connu("vivier", c.vivier(), viviers);
    }

    private static void connu(String nom, String valeur, List<String> valeurs) {
        if (valeur != null && !valeurs.contains(valeur)) {
            throw new IllegalArgumentException("Valeur inconnue pour " + nom + " : " + valeur + " (attendu : "
                    + String.join(", ", valeurs) + ")");
        }
    }

    private static String sansAccents(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
