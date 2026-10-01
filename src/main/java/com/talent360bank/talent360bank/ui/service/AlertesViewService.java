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
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.AlertesView;
import com.talent360bank.talent360bank.ui.model.AlertesView.Compteur;
import com.talent360bank.talent360bank.ui.model.AlertesView.Filtres;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import com.talent360bank.talent360bank.ui.model.TypeAlerte;
import com.talent360bank.talent360bank.ui.model.VueManager.AutoVsManager;
import com.talent360bank.talent360bank.ui.model.VueManager.EcartAutoManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Alertes du trimestre affiche, calculees a chaque lecture a partir du moteur
 * (aucune table d'alertes, aucun chiffre ecrit en dur). Source unique de
 * l'ecran Alertes et du panneau "Alertes prioritaires" du tableau de bord :
 * les deux ne peuvent pas diverger.
 *
 * <p>REGLE : aucun calcul ici. Chaque alerte reprend un resultat du moteur :
 * <ul>
 *   <li>postes critiques en alerte : {@link PosteCritiqueService} (sans successeur =
 *   Critique ; sous le minimum de successeurs = Elevee) ;</li>
 *   <li>vigilance ELEVEE : {@link VigilanceService#evaluerTrimestre} (regle
 *   EntreesVigilance), avec ses signaux ;</li>
 *   <li>evaluation du manager manquante : meme regle que la vue manager (une
 *   evaluation de performance ou de potentiel du manager absente), via
 *   {@link ResultatsCollaborateurs} ;</li>
 *   <li>ecart auto-evaluation / manager au-dela du seuil des reglages
 *   (SeuilsAutoEvaluation), via {@link ResultatsCollaborateurs#autoVsManager} ;</li>
 *   <li>talent propose sans decision du Comite : {@link ValidationComiteService} ;</li>
 *   <li>competences en gap Prioritaire : {@link CompetenceCollaborateurService},
 *   les {@link #TOP_GAPS_COMPETENCES} collaborateurs qui en ont le plus.</li>
 * </ul>
 *
 * <p><strong>Requetes.</strong> Un nombre fixe quelle que soit la population :
 * chaque source est lue en une fois (voir SansSessionOuverteIntegrationTest).
 */
@Service
public class AlertesViewService {

    /** Collaborateurs listes pour les gaps de competences : ceux qui en ont le plus. */
    public static final int TOP_GAPS_COMPETENCES = 10;

    private final CalculService calculService;
    private final CollaborateurRepository collaborateurRepository;
    private final ResultatsCollaborateurs resultats;
    private final NeufBoxService neufBoxService;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final VigilanceService vigilanceService;
    private final PosteCritiqueService posteCritiqueService;
    private final ValidationComiteService validationComiteService;
    private final CompetenceCollaborateurService competenceCollaborateurService;

    public AlertesViewService(CalculService calculService, CollaborateurRepository collaborateurRepository,
                              ResultatsCollaborateurs resultats, NeufBoxService neufBoxService,
                              Matrice9BoxRepository matrice9BoxRepository, VigilanceService vigilanceService,
                              PosteCritiqueService posteCritiqueService,
                              ValidationComiteService validationComiteService,
                              CompetenceCollaborateurService competenceCollaborateurService) {
        this.calculService = calculService;
        this.collaborateurRepository = collaborateurRepository;
        this.resultats = resultats;
        this.neufBoxService = neufBoxService;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.vigilanceService = vigilanceService;
        this.posteCritiqueService = posteCritiqueService;
        this.validationComiteService = validationComiteService;
        this.competenceCollaborateurService = competenceCollaborateurService;
    }

    /**
     * Toutes les alertes du trimestre, de la plus grave a la moins grave (puis
     * par type, puis dans l'ordre de chaque source).
     *
     * @throws RessourceIntrouvableException si les reglages du trimestre sont absents
     * @throws DonneesIncompletesException   si un reglage necessaire manque
     */
    public List<AlerteVue> alertes(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        Parametre parametre = calculService.chargerParametre(trimestre);
        Liens liens = new Liens(trimestre);

        List<AlerteVue> alertes = new ArrayList<>();
        alertes.addAll(postesCritiques(trimestre, parametre, liens));
        alertes.addAll(vigilance(trimestre, liens));

        // Actifs avec manager, entite et parents en une requete : les liens "Vue manager"
        // et les entites se lisent sans requete par collaborateur.
        List<Collaborateur> actifs = collaborateurRepository.findAllAvecManager().stream()
                .filter(Collaborateur::estCalculable)
                .toList();
        Map<String, Collaborateur> parMatricule = actifs.stream()
                .collect(Collectors.toMap(Collaborateur::getIdCollaborateur, Function.identity()));
        ResultatsCollaborateurs.Groupe groupe = resultats.charger(actifs, trimestre, parametre,
                new CasesNeufBox(neufBoxService, matrice9BoxRepository.findAll()), new LinkedHashSet<>());
        alertes.addAll(evaluationsManquantes(actifs, groupe, liens));
        alertes.addAll(ecartsAutoManager(groupe, parametre, parMatricule, liens));

        alertes.addAll(talentsSansDecision(trimestre, liens));
        alertes.addAll(gapsCompetences(trimestre, parMatricule, liens));

        alertes.sort(Comparator.comparing(AlerteVue::severite).thenComparing(AlerteVue::type));
        return List.copyOf(alertes);
    }

    /**
     * Ecran Alertes : compteurs sur toutes les alertes du trimestre, lignes
     * filtrees. Un filtre inconnu est ignore (toutes les valeurs).
     *
     * @param trimestre trimestre affiche (TrimestreCourantService), null s'il n'en existe aucun
     */
    public AlertesView construire(Trimestre trimestre, Filtres demandes) {
        Filtres filtres = nettoyer(demandes);
        if (trimestre == null) {
            return new AlertesView(null, 0, compteursParType(List.of()), compteursParSeverite(List.of()), List.of(),
                    List.of(), filtres, null);
        }
        String libelle = TrimestreCourantService.libelle(trimestre);
        List<AlerteVue> toutes;
        try {
            toutes = alertes(trimestre);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            return new AlertesView(libelle, 0, compteursParType(List.of()), compteursParSeverite(List.of()),
                    List.of(), List.of(), filtres, e.getMessage());
        }

        List<String> directions = toutes.stream().map(AlerteVue::direction).filter(Objects::nonNull)
                .distinct().sorted().toList();
        String recherche = filtres.recherche() == null ? null : normaliser(filtres.recherche());
        List<AlerteVue> filtrees = toutes.stream()
                .filter(alerte -> filtres.type() == null || alerte.type().name().equals(filtres.type()))
                .filter(alerte -> filtres.severite() == null || alerte.severite().name().equals(filtres.severite()))
                .filter(alerte -> filtres.direction() == null || filtres.direction().equals(alerte.direction()))
                .filter(alerte -> recherche == null || normaliser(alerte.sujet()).contains(recherche)
                        || (alerte.matricule() != null && normaliser(alerte.matricule()).contains(recherche)))
                .toList();
        return new AlertesView(libelle, toutes.size(), compteursParType(toutes), compteursParSeverite(toutes),
                filtrees, directions, filtres, null);
    }

    // --- sources --------------------------------------------------------------------

    private List<AlerteVue> postesCritiques(Trimestre trimestre, Parametre parametre, Liens liens) {
        Integer minimum = parametre.getSeuilsCouverture() == null ? null
                : parametre.getSeuilsCouverture().getNbMinSuccesseurs();
        List<AlerteVue> alertes = new ArrayList<>();
        for (CouverturePoste couverture : posteCritiqueService.listerPostesCritiques(trimestre)) {
            if (!couverture.estEnAlerte()) {
                continue;
            }
            Poste poste = couverture.poste();
            boolean aucun = couverture.nbSuccesseurs() == 0;
            alertes.add(new AlerteVue(
                    aucun ? TypeAlerte.POSTE_SANS_SUCCESSEUR : TypeAlerte.POSTE_SOUS_MINIMUM,
                    aucun ? SeveriteAlerte.CRITIQUE : SeveriteAlerte.ELEVEE,
                    poste.getNomPoste(), poste.getPosteId(), libelle(poste.getEntite()), poste.getDirection(),
                    aucun ? "Aucun successeur identifié"
                            : couverture.nbSuccesseurs() + " successeur(s) identifié(s), minimum "
                            + (minimum == null ? "non configuré" : minimum),
                    liens.postesCritiques(), "Postes critiques"));
        }
        return alertes;
    }

    private List<AlerteVue> vigilance(Trimestre trimestre, Liens liens) {
        List<AlerteVue> alertes = new ArrayList<>();
        for (ResultatVigilance resultat : vigilanceService.evaluerTrimestre(trimestre, NiveauVigilance.ELEVEE)) {
            Collaborateur collaborateur = resultat.collaborateur();
            String raisons = resultat.signaux().stream()
                    .sorted(Comparator.comparing(SignalVigilance::ordinal))
                    .map(SignalVigilance::getLibelle)
                    .collect(Collectors.joining(", "));
            alertes.add(alerte(TypeAlerte.VIGILANCE_ELEVEE, SeveriteAlerte.ELEVEE, collaborateur,
                    "Indice " + resultat.indice().stripTrailingZeros().toPlainString() + " / 100 ("
                            + resultat.niveau().getLibelle() + ")" + (raisons.isEmpty() ? "" : " : " + raisons),
                    liens.fiche(collaborateur), "Fiche collaborateur"));
        }
        return alertes;
    }

    /**
     * Meme regle que les alertes EVALUATION_MANQUANTE de la vue manager :
     * evaluation de performance ou de potentiel du manager absente.
     */
    private List<AlerteVue> evaluationsManquantes(List<Collaborateur> actifs, ResultatsCollaborateurs.Groupe groupe,
                                                  Liens liens) {
        List<AlerteVue> alertes = new ArrayList<>();
        for (Collaborateur collaborateur : actifs.stream()
                .sorted(Comparator.comparing(Collaborateur::getNom).thenComparing(Collaborateur::getPrenom)
                        .thenComparing(Collaborateur::getIdCollaborateur))
                .toList()) {
            String id = collaborateur.getIdCollaborateur();
            boolean performance = groupe.evaluePerformance(id);
            boolean potentiel = groupe.evaluePotentiel(id);
            if (performance && potentiel) {
                continue;
            }
            boolean auto = groupe.avecAutoEvaluation().contains(id);
            String message;
            if (!performance && !potentiel) {
                message = auto ? "Auto-évaluation seule : pas d'évaluation du manager, pas de score officiel"
                        : "Aucune évaluation ce trimestre (ni manager, ni auto-évaluation)";
            } else {
                message = "Évaluation de " + (performance ? "potentiel" : "performance") + " du manager manquante"
                        + (auto ? " (auto-évaluation reçue)" : "");
            }
            alertes.add(versManager(TypeAlerte.EVALUATION_MANAGER_MANQUANTE, SeveriteAlerte.ELEVEE,
                    collaborateur, message, liens));
        }
        return alertes;
    }

    private List<AlerteVue> ecartsAutoManager(ResultatsCollaborateurs.Groupe groupe, Parametre parametre,
                                              Map<String, Collaborateur> parMatricule, Liens liens) {
        AutoVsManager synthese = ResultatsCollaborateurs.autoVsManager(groupe.ecartsAutoManager(), parametre);
        if (synthese == null) {
            return List.of();
        }
        List<AlerteVue> alertes = new ArrayList<>();
        for (EcartAutoManager ecart : synthese.ecartsImportants()) {
            Collaborateur collaborateur = parMatricule.get(ecart.matricule());
            alertes.add(versManager(TypeAlerte.ECART_AUTO_MANAGER, SeveriteAlerte.MOYENNE, collaborateur,
                    "Auto-évaluation - manager : performance " + signe(ecart.ecartPerformance()) + ", potentiel "
                            + signe(ecart.ecartPotentiel()) + " (seuil "
                            + synthese.seuilEcartImportant().stripTrailingZeros().toPlainString() + " points)",
                    liens));
        }
        return alertes;
    }

    private List<AlerteVue> talentsSansDecision(Trimestre trimestre, Liens liens) {
        List<AlerteVue> alertes = new ArrayList<>();
        for (DecisionComite decision : validationComiteService.getDecisionsComite(trimestre)) {
            if (decision.statut() != StatutValidationComite.EN_ATTENTE) {
                continue;
            }
            alertes.add(alerte(TypeAlerte.TALENT_SANS_DECISION, SeveriteAlerte.MOYENNE,
                    decision.score().getCollaborateur(),
                    "Talent proposé (performance " + decision.score().getScorePerformance().toPlainString()
                            + ", potentiel " + decision.score().getScorePotentiel().toPlainString()
                            + ") : décision du Comité en attente",
                    liens.comiteEnAttente(), "Comité Talent"));
        }
        return alertes;
    }

    private List<AlerteVue> gapsCompetences(Trimestre trimestre, Map<String, Collaborateur> parMatricule,
                                            Liens liens) {
        return competenceCollaborateurService.gapsPrioritairesParCollaborateur(trimestre).entrySet().stream()
                .filter(entree -> parMatricule.containsKey(entree.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_GAPS_COMPETENCES)
                .map(entree -> {
                    Collaborateur collaborateur = parMatricule.get(entree.getKey());
                    return alerte(TypeAlerte.GAPS_COMPETENCES_PRIORITAIRES, SeveriteAlerte.MOYENNE, collaborateur,
                            entree.getValue() + " compétence(s) en gap prioritaire",
                            liens.fiche(collaborateur), "Fiche collaborateur");
                })
                .toList();
    }

    // --- outils ---------------------------------------------------------------------

    private static AlerteVue alerte(TypeAlerte type, SeveriteAlerte severite, Collaborateur collaborateur,
                                    String message, String lien, String lienLibelle) {
        return new AlerteVue(type, severite, collaborateur.getNomComplet(), collaborateur.getIdCollaborateur(),
                libelle(collaborateur.getEntite()), collaborateur.getDirection(), message, lien, lienLibelle);
    }

    /** Vers la vue manager du manager du collaborateur ; sans manager, vers sa fiche. */
    private static AlerteVue versManager(TypeAlerte type, SeveriteAlerte severite, Collaborateur collaborateur,
                                         String message, Liens liens) {
        return collaborateur.getManager() == null
                ? alerte(type, severite, collaborateur, message, liens.fiche(collaborateur), "Fiche collaborateur")
                : alerte(type, severite, collaborateur, message,
                liens.vueManager(collaborateur.getManager().getIdCollaborateur()), "Vue manager");
    }

    private static String libelle(Entite entite) {
        return entite == null ? null : entite.getLibelle();
    }

    private static String signe(BigDecimal ecart) {
        if (ecart == null) {
            return "-";
        }
        String valeur = ecart.stripTrailingZeros().toPlainString();
        return ecart.signum() > 0 ? "+" + valeur : valeur;
    }

    private static List<Compteur> compteursParType(List<AlerteVue> alertes) {
        List<Compteur> compteurs = new ArrayList<>();
        for (TypeAlerte type : TypeAlerte.values()) {
            compteurs.add(new Compteur(type.name(), type.getLibelle(),
                    (int) alertes.stream().filter(alerte -> alerte.type() == type).count(), null));
        }
        return List.copyOf(compteurs);
    }

    private static List<Compteur> compteursParSeverite(List<AlerteVue> alertes) {
        List<Compteur> compteurs = new ArrayList<>();
        for (SeveriteAlerte severite : SeveriteAlerte.values()) {
            compteurs.add(new Compteur(severite.name(), severite.getLibelle(),
                    (int) alertes.stream().filter(alerte -> alerte.severite() == severite).count(),
                    severite.getBadgeClass()));
        }
        return List.copyOf(compteurs);
    }

    /** Filtres valides ou null : un type ou une gravite inconnus, une valeur vide, valent "tous". */
    private static Filtres nettoyer(Filtres demandes) {
        if (demandes == null) {
            return Filtres.AUCUN;
        }
        String type = connu(demandes.type(), TypeAlerte.values());
        String severite = connu(demandes.severite(), SeveriteAlerte.values());
        String direction = vide(demandes.direction()) ? null : demandes.direction().trim();
        String recherche = vide(demandes.recherche()) ? null : demandes.recherche().trim();
        return new Filtres(type, severite, direction, recherche);
    }

    private static String connu(String valeur, Enum<?>[] valeurs) {
        if (vide(valeur)) {
            return null;
        }
        for (Enum<?> candidat : valeurs) {
            if (candidat.name().equalsIgnoreCase(valeur.trim())) {
                return candidat.name();
            }
        }
        return null;
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    /** Sans accents ni casse, pour la recherche par nom. */
    private static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Liens des alertes, pour le trimestre affiche. La fiche collaborateur et la
     * vue manager n'ont pas encore d'ecran : les liens menent a leur API (JSON).
     * A remplacer ici, en un seul endroit, quand les pages existeront.
     */
    private record Liens(String annee, String numero, String valeur) {

        Liens(Trimestre trimestre) {
            this(String.valueOf(trimestre.getAnnee()), String.valueOf(trimestre.getNumero()),
                    TrimestreCourantService.valeur(trimestre));
        }

        String fiche(Collaborateur collaborateur) {
            return "/api/trimestres/" + annee + "/" + numero + "/collaborateurs/"
                    + collaborateur.getIdCollaborateur() + "/fiche";
        }

        String vueManager(String matriculeManager) {
            return "/api/trimestres/" + annee + "/" + numero + "/managers/" + matriculeManager + "/vue";
        }

        String postesCritiques() {
            return "/postes-critiques?trimestre=" + valeur;
        }

        String comiteEnAttente() {
            return "/comite-talent?trimestre=" + valeur + "&statut=EN_ATTENTE";
        }
    }
}
