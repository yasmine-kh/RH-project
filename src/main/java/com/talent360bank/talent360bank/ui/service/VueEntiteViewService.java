package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.AlerteVigilance;
import com.talent360bank.talent360bank.ui.model.VueEntite.ComparaisonEnfant;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteEnfant;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteVue;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.PosteCritiqueVue;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import com.talent360bank.talent360bank.ui.model.VueManager.Synthese;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assemble la vue d'une entite de l'organigramme sur un trimestre (resultats
 * agreges de son sous-arbre), et l'arbre des entites pour la choisir.
 *
 * <p>REGLE : aucun calcul ici. Resultats et synthese viennent de
 * {@link ResultatsCollaborateurs}, partage avec la vue manager : pour les
 * memes personnes, les deux vues donnent les memes chiffres. La couverture
 * des postes critiques vient de PosteCritiqueService.evaluerPostes.
 *
 * <p><strong>Sous-arbre.</strong> Resolu en memoire depuis l'organigramme,
 * lu en une requete (quelques centaines d'entites au plus). Collaborateurs du
 * sous-arbre hors ARCHIVE, comme la vue manager. Un collaborateur rattache a
 * l'entite elle-meme (et non a un enfant) compte dans la synthese, pas dans
 * la comparaison des enfants.
 *
 * <p><strong>Requetes.</strong> Un nombre fixe quelle que soit la taille du
 * sous-arbre : organigramme, collaborateurs (IN sur les entites), puis leurs
 * donnees (IN sur les matricules), postes et managers en une requete chacun.
 *
 * <p><strong>Donnees manquantes.</strong> Comme les autres vues : bloc ou
 * valeur null et une phrase dans donneesManquantes ; seuls un trimestre ou
 * une entite inconnus levent RessourceIntrouvableException (404).
 */
@Service
public class VueEntiteViewService {

    private final TrimestreRepository trimestreRepository;
    private final EntiteRepository entiteRepository;
    private final CollaborateurRepository collaborateurRepository;
    private final ParametreRepository parametreRepository;
    private final PosteRepository posteRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final NeufBoxService neufBoxService;
    private final PosteCritiqueService posteCritiqueService;
    private final ResultatsCollaborateurs resultats;
    private final VueManagerViewService vueManagerViewService;

    public VueEntiteViewService(TrimestreRepository trimestreRepository,
                                EntiteRepository entiteRepository,
                                CollaborateurRepository collaborateurRepository,
                                ParametreRepository parametreRepository,
                                PosteRepository posteRepository,
                                Matrice9BoxRepository matrice9BoxRepository,
                                NeufBoxService neufBoxService,
                                PosteCritiqueService posteCritiqueService,
                                ResultatsCollaborateurs resultats,
                                VueManagerViewService vueManagerViewService) {
        this.trimestreRepository = trimestreRepository;
        this.entiteRepository = entiteRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.parametreRepository = parametreRepository;
        this.posteRepository = posteRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.neufBoxService = neufBoxService;
        this.posteCritiqueService = posteCritiqueService;
        this.resultats = resultats;
        this.vueManagerViewService = vueManagerViewService;
    }

    /**
     * L'organigramme pour la page de choix : les directions par libelle, chacune
     * avec ses descendants, et l'effectif de chaque sous-arbre (hors archives).
     * Deux requetes.
     */
    @Transactional(readOnly = true)
    public List<NoeudEntite> listerEntites() {
        Organigramme organigramme = new Organigramme(entiteRepository.findAllAvecParent());
        Map<Integer, Integer> effectifsDirects = new HashMap<>();
        for (Object[] ligne : collaborateurRepository.compterParEntite(StatutCollaborateur.ARCHIVE)) {
            effectifsDirects.put((Integer) ligne[0], ((Number) ligne[1]).intValue());
        }
        return organigramme.racines().stream().map(racine -> noeud(racine, organigramme, effectifsDirects)).toList();
    }

    private static NoeudEntite noeud(Entite entite, Organigramme organigramme, Map<Integer, Integer> effectifs) {
        List<NoeudEntite> enfants = organigramme.enfants(entite).stream()
                .map(enfant -> noeud(enfant, organigramme, effectifs))
                .toList();
        int effectif = effectifs.getOrDefault(entite.getIdEntite(), 0)
                + enfants.stream().mapToInt(NoeudEntite::effectif).sum();
        return new NoeudEntite(entite.getCode(), entite.getLibelle(), type(entite), effectif, enfants);
    }

    /**
     * @throws RessourceIntrouvableException si le trimestre ou l'entite est inconnu
     */
    @Transactional(readOnly = true)
    public VueEntite construire(String codeEntite, int annee, int numero) {
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre T" + numero + " " + annee));
        Organigramme organigramme = new Organigramme(entiteRepository.findAllAvecParent());
        Entite entite = organigramme.parCode(codeEntite)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucune entité " + codeEntite));

        Set<String> manquantes = new LinkedHashSet<>();
        Parametre parametre = parametreRepository.findByTrimestre(trimestre).orElse(null);
        if (parametre == null) {
            manquantes.add(VueManagerViewService.aucunReglage(trimestre));
        }
        CasesNeufBox cases = new CasesNeufBox(neufBoxService, matrice9BoxRepository.findAll());

        // Sous-arbre et, pour chaque entite descendante, l'enfant direct dont elle releve.
        List<Entite> enfantsDirects = organigramme.enfants(entite);
        Map<Integer, Entite> enfantDirectDe = new HashMap<>();
        for (Entite enfant : enfantsDirects) {
            for (Entite descendante : organigramme.sousArbre(enfant)) {
                enfantDirectDe.put(descendante.getIdEntite(), enfant);
            }
        }
        List<Integer> idsSousArbre = organigramme.sousArbre(entite).stream().map(Entite::getIdEntite).toList();

        List<Collaborateur> collaborateurs = collaborateurRepository.findByEntites(idsSousArbre,
                StatutCollaborateur.ARCHIVE);
        ResultatsCollaborateurs.Groupe groupe = resultats.charger(collaborateurs, trimestre, parametre, cases,
                manquantes);
        Map<String, Collaborateur> parMatricule = collaborateurs.stream()
                .collect(Collectors.toMap(Collaborateur::getIdCollaborateur, c -> c));

        // Membres de chaque enfant direct.
        Map<Integer, List<Membre>> membresParEnfant = new HashMap<>();
        for (Membre membre : groupe.membres()) {
            Entite rattachement = parMatricule.get(membre.matricule()).getEntite();
            Entite enfant = rattachement == null ? null : enfantDirectDe.get(rattachement.getIdEntite());
            if (enfant != null) {
                membresParEnfant.computeIfAbsent(enfant.getIdEntite(), id -> new ArrayList<>()).add(membre);
            }
        }

        List<EntiteEnfant> enfants = new ArrayList<>();
        List<ComparaisonEnfant> comparaison = new ArrayList<>();
        for (Entite enfant : enfantsDirects) {
            List<Membre> membres = membresParEnfant.getOrDefault(enfant.getIdEntite(), List.of());
            enfants.add(new EntiteEnfant(enfant.getCode(), enfant.getLibelle(), type(enfant), membres.size()));
            comparaison.add(comparer(enfant, membres, cases));
        }

        Set<String> matricules = parMatricule.keySet();
        return new VueEntite(
                new TrimestreFiche(annee, numero, VueManagerViewService.libelle(trimestre), trimestre.getDateReference()),
                new EntiteVue(entite.getCode(), entite.getLibelle(), type(entite), EntiteFiche.de(entite).chemin(),
                        ref(entite.getParent()), List.copyOf(enfants)),
                ResultatsCollaborateurs.synthese(groupe.membres(), cases),
                List.copyOf(comparaison),
                postesCritiques(organigramme.sousArbre(entite), matricules, trimestre, parametre, manquantes),
                alertes(groupe.membres(), parMatricule),
                vueManagerViewService.managers().stream()
                        .filter(manager -> matricules.contains(manager.matricule()))
                        .toList(),
                List.copyOf(manquantes));
    }

    // --- comparaison des enfants -----------------------------------------------

    private static ComparaisonEnfant comparer(Entite enfant, List<Membre> membres, CasesNeufBox cases) {
        Synthese synthese = ResultatsCollaborateurs.synthese(membres, cases);
        BigDecimal pourcentageTalents = synthese.nbAvecScore() == 0 ? null
                : BigDecimal.valueOf(synthese.nbTalents() * 100L)
                .divide(BigDecimal.valueOf(synthese.nbAvecScore()), 1, RoundingMode.HALF_UP);
        return new ComparaisonEnfant(enfant.getCode(), enfant.getLibelle(), type(enfant), synthese.effectif(),
                synthese.nbAvecScore(), synthese.moyennePerformance(), synthese.moyennePotentiel(),
                pourcentageTalents, synthese.moyenneEngagement(),
                ResultatsCollaborateurs.compter(membres, ResultatsCollaborateurs::vigilanceElevee));
    }

    // --- postes critiques ------------------------------------------------------

    /**
     * Postes critiques rattaches au sous-arbre (les postes le sont a leur
     * direction) ou dont le titulaire y travaille, avec leur couverture.
     */
    private List<PosteCritiqueVue> postesCritiques(List<Entite> sousArbre, Set<String> matricules,
                                                   Trimestre trimestre, Parametre parametre,
                                                   Set<String> manquantes) {
        Set<Integer> idsSousArbre = sousArbre.stream().map(Entite::getIdEntite).collect(Collectors.toSet());
        List<Poste> postes = new ArrayList<>();
        Map<String, String> rattachements = new HashMap<>();
        for (Poste poste : posteRepository.findAllAvecCompetences()) {
            if (!PosteCritiqueService.estCritique(poste)) {
                continue;
            }
            if (poste.getEntite() != null && idsSousArbre.contains(poste.getEntite().getIdEntite())) {
                rattachements.put(poste.getPosteId(), "ENTITE");
            } else if (poste.getTitulaireId() != null && matricules.contains(poste.getTitulaireId())) {
                rattachements.put(poste.getPosteId(), "TITULAIRE");
            } else {
                continue;
            }
            postes.add(poste);
        }

        Map<String, CouverturePoste> couvertures = new HashMap<>();
        if (parametre != null && !postes.isEmpty()) {
            try {
                for (CouverturePoste couverture : posteCritiqueService.evaluerPostes(postes, trimestre, parametre)) {
                    couvertures.put(couverture.poste().getPosteId(), couverture);
                }
            } catch (DonneesIncompletesException e) {
                manquantes.add("Couverture des postes critiques : " + e.getMessage());
            }
        }

        List<PosteCritiqueVue> vues = new ArrayList<>();
        for (Poste poste : postes) {
            CouverturePoste couverture = couvertures.get(poste.getPosteId());
            vues.add(new PosteCritiqueVue(poste.getPosteId(), poste.getNomPoste(), poste.getDirection(),
                    poste.getCriticite(), poste.getTitulaireId(), poste.getTitulaireNom(),
                    rattachements.get(poste.getPosteId()),
                    couverture == null ? null : couverture.niveau().name(),
                    couverture == null ? null : couverture.niveau().getLibelle(),
                    couverture == null ? null : couverture.estEnAlerte(),
                    couverture == null ? null : couverture.nbSuccesseurs(),
                    couverture == null ? null : couverture.meilleurMatching()));
        }
        return List.copyOf(vues);
    }

    // --- alertes ---------------------------------------------------------------

    private static List<AlerteVigilance> alertes(List<Membre> membres, Map<String, Collaborateur> parMatricule) {
        return membres.stream()
                .filter(ResultatsCollaborateurs::vigilanceElevee)
                .sorted(Comparator.comparing(Membre::indiceVigilance, Comparator.reverseOrder())
                        .thenComparing(Membre::matricule))
                .map(membre -> new AlerteVigilance(membre.matricule(), membre.nom(), membre.prenom(),
                        ref(parMatricule.get(membre.matricule()).getEntite()), membre.indiceVigilance(),
                        membre.niveauVigilance(), membre.niveauVigilanceLibelle()))
                .toList();
    }

    // --- outils ----------------------------------------------------------------

    private static EntiteRef ref(Entite entite) {
        return entite == null ? null : new EntiteRef(entite.getCode(), entite.getLibelle(), type(entite));
    }

    private static String type(Entite entite) {
        return entite.getType() == null ? null : entite.getType().name();
    }

    /** L'organigramme en memoire : enfants de chaque entite, par libelle. */
    private static final class Organigramme {

        private final Map<String, Entite> parCode = new HashMap<>();
        private final Map<Integer, List<Entite>> enfants = new HashMap<>();
        private final List<Entite> racines = new ArrayList<>();

        Organigramme(List<Entite> entites) {
            Comparator<Entite> parLibelle = Comparator.comparing(Entite::getLibelle)
                    .thenComparing(Entite::getCode);
            for (Entite entite : entites) {
                parCode.put(entite.getCode(), entite);
                if (entite.getParent() == null) {
                    racines.add(entite);
                } else {
                    enfants.computeIfAbsent(entite.getParent().getIdEntite(), id -> new ArrayList<>()).add(entite);
                }
            }
            racines.sort(parLibelle);
            enfants.values().forEach(liste -> liste.sort(parLibelle));
        }

        Optional<Entite> parCode(String code) {
            return Optional.ofNullable(parCode.get(code));
        }

        List<Entite> racines() {
            return racines;
        }

        List<Entite> enfants(Entite entite) {
            return enfants.getOrDefault(entite.getIdEntite(), List.of());
        }

        /** L'entite et tous ses descendants, en largeur. Un cycle eventuel ne boucle pas. */
        List<Entite> sousArbre(Entite racine) {
            List<Entite> resultat = new ArrayList<>();
            Set<Integer> vus = new HashSet<>();
            List<Entite> aTraiter = new ArrayList<>(List.of(racine));
            while (!aTraiter.isEmpty()) {
                Entite entite = aTraiter.remove(0);
                if (vus.add(entite.getIdEntite())) {
                    resultat.add(entite);
                    aTraiter.addAll(enfants(entite));
                }
            }
            return resultat;
        }
    }
}
