package com.talent360bank.talent360bank.ui.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.LigneCollaborateur;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.VivierRef;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.AlerteLigne;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.CaseCompte;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.Filtres;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.Kpi;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.Personne;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.Puce;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.VivierListe;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import com.talent360bank.talent360bank.ui.model.TypeAlerte;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Tableau de bord RH interactif : la population calculee du trimestre, filtree par
 * les parametres de l'URL (case, entite, vigilance, perf, pot, vivier, alerte).
 *
 * <p>REGLE : aucun calcul du moteur ici. Les personnes viennent de
 * {@link ListeCollaborateursViewService#population} (les memes resultats que la liste
 * Collaborateurs), les alertes d'{@link AlertesViewService} (comme l'ecran Alertes) ;
 * les cartes des postes critiques sont celles de {@link DashboardService}, non filtrables.
 * Ici, seulement filtrer, compter et moyenner l'engagement (2 decimales, comme
 * DashboardService). static/js/tableau-de-bord.js applique les memes regles.
 *
 * <p>Cartes : les 10 du prototype (renderDashRH), dans son ordre. Population, Talents
 * valides, Hauts potentiels, Viviers actifs et Engagement suivent les filtres ; Postes
 * critiques, Couverture succession, Ready Now, Postes sans releve et Gaps critiques
 * (postes en alerte) sont les cartes du moteur, toute la banque.
 *
 * <p>Regles de filtrage (toutes cumulees) :
 * <ul>
 *   <li>une personne passe si elle repond a chaque filtre ; pour "alerte", si elle a
 *   au moins une alerte de ce type ;</li>
 *   <li>une alerte sur une personne passe si la personne passe (hors filtre "alerte")
 *   et si son type est celui du filtre ; une alerte sur un poste ne passe que sans
 *   filtre propre aux personnes (case, vigilance, perf, pot, vivier), si sa direction
 *   est celle du filtre "entite" et si son type est celui du filtre ;</li>
 *   <li>chaque graphique compte avec tous les filtres sauf le sien (l'element choisi
 *   est mis en avant, les autres estompes) ; cartes et listes, avec tous.</li>
 * </ul>
 *
 * <p>Tout ce que la page affiche est calcule ici, avant le rendu : libelles, etiquettes des cases,
 * ordre de la grille, textes des listes ; une valeur absente devient "—" (jamais null dans un texte,
 * jamais d'exception pendant le rendu). Le template ne fait qu'afficher.
 *
 * <p>Un nombre fixe de requetes quelle que soit la population.
 */
@Service
public class TableauDeBordInteractifService {


    /** Libelles d'affichage, accentues, des codes du moteur. */
    static final Map<String, String> VIGILANCE = libelles("FAIBLE", "Faible", "MODEREE", "Modérée", "ELEVEE", "Élevée");
    static final Map<String, String> PERFORMANCE = libelles("EXCEPTIONNELLE", "Exceptionnelle", "ELEVEE", "Élevée",
            "SOLIDE", "Solide", "A_RENFORCER", "À renforcer", "INSUFFISANTE", "Insuffisante");
    static final Map<String, String> POTENTIEL = libelles("ELEVE", "Élevé", "MOYEN", "Moyen", "FAIBLE", "Faible");
    private static final String SANS_DIRECTION = "SANS";
    /** Valeur affichee quand une donnee manque. */
    static final String VIDE = "—";
    /** Emoji et fond de chaque case du prototype (BOX9_DEF), par numero 1 a 9. */
    private static final Map<Integer, String> EMOJI = Map.of(9, "⭐", 6, "🚀", 3, "🔵", 8, "👔", 5, "🟢", 2, "🟠",
            7, "🧠", 4, "🟠", 1, "🔴");
    private static final Map<Integer, String> FOND = Map.of(9, "talent", 6, "talent", 3, "high", 8, "high", 5, "",
            2, "", 7, "high", 4, "", 1, "watch");

    private final ListeCollaborateursViewService listeCollaborateurs;
    private final AlertesViewService alertesViewService;
    private final ObjectMapper objectMapper;

    public TableauDeBordInteractifService(ListeCollaborateursViewService listeCollaborateurs,
                                          AlertesViewService alertesViewService, ObjectMapper objectMapper) {
        this.listeCollaborateurs = listeCollaborateurs;
        this.alertesViewService = alertesViewService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param tableau    le tableau de bord du moteur (DashboardService), sans erreur
     * @param parametres parametres de la requete ; seuls ceux de {@link TableauDeBordInteractif#PARAMETRES} comptent
     */
    public TableauDeBordInteractif construire(Trimestre trimestre, TableauDeBordView tableau,
                                              Map<String, String> parametres) {
        String valeurTrimestre = TrimestreCourantService.valeur(trimestre);
        ListeCollaborateursViewService.Population population = listeCollaborateurs.population(trimestre);

        Map<String, String> libellesViviers = new LinkedHashMap<>();
        OptionsFiltresService.viviers().forEach(c -> libellesViviers.put(c.valeur(), c.libelle()));
        List<Personne> personnes = new ArrayList<>();
        for (LigneCollaborateur ligne : population.lignes()) {
            if (ligne == null || ligne.matricule() == null) {
                continue;
            }
            personnes.add(personne(ligne, population.membres().get(ligne.matricule()), valeurTrimestre,
                    libellesViviers));
        }
        personnes.sort(ORDRE);
        Map<String, Personne> parMatricule = personnes.stream()
                .collect(Collectors.toMap(Personne::matricule, p -> p, (a, b) -> a, LinkedHashMap::new));
        List<AlerteLigne> alertes = alertesViewService.alertes(trimestre).stream()
                .map(a -> alerte(a, parMatricule.containsKey(a.matricule()))).toList();

        Map<String, Object> dimensions = dimensions(tableau, personnes);
        Filtres filtres = filtres(parametres, dimensions);
        Regles regles = new Regles(personnes, alertes, filtres);

        List<Personne> filtrees = personnes.stream().filter(p -> regles.passe(p, null)).toList();
        List<AlerteLigne> alertesFiltrees = alertes.stream().filter(a -> regles.passe(a, null)).toList();

        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("trimestre", valeurTrimestre);
        donnees.put("dimensions", dimensions);
        donnees.put("personnes", personnes);
        donnees.put("alertes", alertes);
        String json;
        try {
            json = objectMapper.writeValueAsString(donnees);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Donnees du tableau de bord non serialisables", e);
        }

        return new TableauDeBordInteractif(filtres, puces(filtres, dimensions, valeurTrimestre),
                kpis(filtrees, tableau),
                neufBox(tableau, personnes, regles, filtres, valeurTrimestre), filtrees, alertesFiltrees,
                viviers(filtrees), json);
    }

    /** Performance + potentiel decroissants, puis performance, puis matricule (regle des top talents). */
    static final Comparator<Personne> ORDRE = Comparator
            .comparing((Personne p) -> p.performance() == null || p.potentiel() == null ? null
                    : p.performance().add(p.potentiel()), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Personne::performance, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Personne::matricule);

    // --- construction des lignes -------------------------------------------------------------

    static Personne personne(LigneCollaborateur l, Membre m, String trimestre, Map<String, String> libellesViviers) {
        String code = l.entite() == null || l.entite().code() == null || l.entite().code().isBlank() ? SANS_DIRECTION
                : l.entite().code().split("/", 2)[0];
        Integer numero = l.neufBox() == null ? null : l.neufBox().numero();
        String libelleCase = l.neufBox() == null ? null : texte(l.neufBox().libelle());
        List<String> viviers = l.viviers() == null ? List.of() : l.viviers().stream()
                .filter(Objects::nonNull).map(VivierRef::code).filter(Objects::nonNull).toList();
        String viviersTexte = viviers.isEmpty() ? VIDE : viviers.stream()
                .map(v -> libellesViviers.getOrDefault(v, v)).collect(Collectors.joining(", "));
        return new Personne(l.matricule(), texte(l.nomComplet()), LiensPages.fiche(l.matricule(), trimestre), code,
                l.direction() == null || l.direction().isBlank() ? "Sans direction" : l.direction(),
                numero, libelleCase,
                l.scorePerformance(), m == null ? null : m.categoriePerformance(), l.scorePotentiel(),
                m == null ? null : m.categoriePotentiel(), l.niveauVigilance(),
                l.niveauVigilance() == null ? null : VIGILANCE.getOrDefault(l.niveauVigilance(), l.niveauVigilance()),
                m == null ? null : m.engagement(), Boolean.TRUE.equals(l.estTalent()),
                Boolean.TRUE.equals(l.estHautPotentiel()), l.estTalentValide(),
                m != null && Boolean.TRUE.equals(m.estVivierSuccession()), viviers,
                numero == null ? VIDE : etiquette(numero, libelleCase), viviersTexte);
    }

    /** "emoji libelle" d'une case 9-Box. */
    static String etiquette(int numero, String libelle) {
        return EMOJI.getOrDefault(numero, "") + " " + texte(libelle);
    }

    /** Le texte, ou "—" s'il est absent. */
    static String texte(String valeur) {
        return valeur == null || valeur.isBlank() ? VIDE : valeur;
    }

    private static AlerteLigne alerte(AlerteVue a, boolean personne) {
        String classe = a.severite() == SeveriteAlerte.CRITIQUE ? "crit" : a.severite() == SeveriteAlerte.ELEVEE
                ? "warn" : "info";
        String sev = a.severite() == SeveriteAlerte.CRITIQUE ? "CRITIQUE" : a.severite() == SeveriteAlerte.ELEVEE
                ? "ATTENTION" : "INFO";
        return new AlerteLigne(a.type().name(), a.type().getLibelle(), a.severite().name(), classe, sev,
                texte(a.sujet()), a.matricule(), personne, a.direction(), texte(a.message()), a.lien(),
                texte(a.lienLibelle()));
    }

    /** Valeurs possibles de chaque filtre, dans l'ordre d'affichage, avec leur libelle. */
    static Map<String, Object> dimensions(TableauDeBordView tableau, List<Personne> personnes) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("case", tableau.neufBox().stream()
                .map(c -> Map.of("code", String.valueOf(numero(c)), "libelle", texte(c.libelle()),
                        "perf", c.niveauPerformance(), "pot", c.niveauPotentiel()))
                .toList());
        Map<String, String> directions = new java.util.TreeMap<>();
        personnes.forEach(p -> directions.putIfAbsent(p.directionCode(), p.direction()));
        d.put("entite", directions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> Map.of("code", e.getKey(), "libelle", e.getValue())).toList());
        d.put("vigilance", choix(VIGILANCE));
        d.put("perf", choix(PERFORMANCE));
        d.put("pot", choix(POTENTIEL));
        d.put("vivier", OptionsFiltresService.viviers().stream()
                .map(c -> Map.of("code", c.valeur(), "libelle", c.libelle())).toList());
        d.put("alerte", java.util.Arrays.stream(TypeAlerte.values())
                .map(t -> Map.of("code", t.name(), "libelle", t.getLibelle())).toList());
        return d;
    }

    private static int numero(CaseTableau c) {
        return (c.niveauPerformance() - 1) * 3 + c.niveauPotentiel();
    }

    // --- filtres -------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static String valide(Map<String, String> parametres, String nom, Map<String, Object> dimensions) {
        String valeur = parametres.get(nom);
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String v = valeur.trim();
        return ((List<Map<String, Object>>) dimensions.get(nom)).stream()
                .anyMatch(e -> v.equals(e.get("code"))) ? v : null;
    }

    static Filtres filtres(Map<String, String> parametres, Map<String, Object> dimensions) {
        String numero = valide(parametres, "case", dimensions);
        return new Filtres(numero == null ? null : Integer.valueOf(numero), valide(parametres, "entite", dimensions),
                valide(parametres, "vigilance", dimensions), valide(parametres, "perf", dimensions),
                valide(parametres, "pot", dimensions), valide(parametres, "vivier", dimensions),
                valide(parametres, "alerte", dimensions));
    }

    /** Les regles de filtrage croise (meme logique que tableau-de-bord.js). */
    static final class Regles {

        private final Filtres f;
        private final Map<String, Set<String>> typesParPersonne = new LinkedHashMap<>();
        private final Map<String, Personne> personnes = new LinkedHashMap<>();
        private final Map<String, String> directions = new LinkedHashMap<>();

        Regles(List<Personne> personnes, List<AlerteLigne> alertes, Filtres f) {
            this.f = f;
            personnes.forEach(p -> {
                this.personnes.put(p.matricule(), p);
                directions.putIfAbsent(p.directionCode(), p.direction());
            });
            alertes.stream().filter(AlerteLigne::personne).forEach(a ->
                    typesParPersonne.computeIfAbsent(a.matricule(), k -> new HashSet<>()).add(a.type()));
        }

        /** @param ignore dimension a ne pas appliquer (graphique de cette dimension), null pour toutes */
        boolean passe(Personne p, String ignore) {
            return (f.caseNeufBox() == null || "case".equals(ignore) || f.caseNeufBox().equals(p.caseNumero()))
                    && (f.entite() == null || "entite".equals(ignore) || f.entite().equals(p.directionCode()))
                    && (f.vigilance() == null || "vigilance".equals(ignore) || f.vigilance().equals(p.vigilance()))
                    && (f.perf() == null || "perf".equals(ignore) || f.perf().equals(p.categoriePerformance()))
                    && (f.pot() == null || "pot".equals(ignore) || f.pot().equals(p.categoriePotentiel()))
                    && (f.vivier() == null || "vivier".equals(ignore) || p.viviers().contains(f.vivier()))
                    && (f.alerte() == null || "alerte".equals(ignore)
                    || typesParPersonne.getOrDefault(p.matricule(), Set.of()).contains(f.alerte()));
        }

        boolean passe(AlerteLigne a, String ignore) {
            if (f.alerte() != null && !"alerte".equals(ignore) && !f.alerte().equals(a.type())) {
                return false;
            }
            if (a.personne()) {
                return passe(personnes.get(a.matricule()), ignore == null ? "alerte" : ignore);
            }
            boolean filtrePersonne = (f.caseNeufBox() != null && !"case".equals(ignore))
                    || (f.vigilance() != null && !"vigilance".equals(ignore))
                    || (f.perf() != null && !"perf".equals(ignore))
                    || (f.pot() != null && !"pot".equals(ignore))
                    || (f.vivier() != null && !"vivier".equals(ignore));
            return !filtrePersonne && (f.entite() == null || "entite".equals(ignore)
                    || Objects.equals(directions.get(f.entite()), a.direction()));
        }
    }

    // --- cartes, matrice, viviers, puces ----------------------------------------------------------

    /**
     * Les 10 cartes du prototype, dans son ordre (2 rangees de 5). Celles sur les personnes
     * suivent les filtres, avec les definitions de DashboardService ; les autres sont les cartes
     * du moteur telles quelles. Couleur de la carte Engagement : regle du prototype (>= 75 teal,
     * >= 60 gold, sinon rust).
     */
    static List<Kpi> kpis(List<Personne> p, TableauDeBordView tableau) {
        // Une carte sans libelle ou sans valeur ne fait pas echouer la page : "—".
        Map<String, String> moteur = new LinkedHashMap<>();
        tableau.kpis().stream().filter(Objects::nonNull).filter(k -> k.getLabel() != null)
                .forEach(k -> moteur.putIfAbsent(k.getLabel(), texte(k.getValue())));
        List<BigDecimal> engagements = p.stream().map(Personne::engagement).filter(Objects::nonNull).toList();
        BigDecimal moyenne = engagements.isEmpty() ? null : engagements.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(engagements.size()), 2, RoundingMode.HALF_UP);
        long viviersActifs = p.stream().flatMap(x -> x.viviers().stream())
                .filter(code -> !"RELEVE".equals(code)).distinct().count();
        return List.of(
                new Kpi("population", "👥 Population", String.valueOf(p.size()), "", true),
                new Kpi("talentsValides", "⭐ Talents validés", compter(p, Personne::estTalentValide), "gold", true),
                new Kpi("hautsPotentiels", "🚀 Hauts potentiels", compter(p, Personne::estHautPotentiel), "teal", true),
                new Kpi("viviersActifs", "🔄 Viviers actifs", String.valueOf(viviersActifs), "", true),
                moteur(moteur, "Postes critiques", "postesCritiques", "👔 Postes critiques", ""),
                moteur(moteur, "Couverture succession", "couverture", "🔗 Couverture succession", "teal"),
                moteur(moteur, "Successeurs Ready Now", "readyNow", "🟢 Ready Now", "teal"),
                new Kpi("engagement", "❤️ Engagement (" + engagements.size() + " réponse"
                        + (engagements.size() > 1 ? "s" : "") + ")", moyenne == null ? "—" : moyenne.toPlainString(),
                        classeEngagement(moyenne), true),
                moteur(moteur, "Postes critiques sans successeur", "postesSansReleve", "🔴 Postes sans relève", "rust"),
                moteur(moteur, "Postes critiques en alerte", "gapsCritiques", "🧩 Gaps critiques", "rust"));
    }

    private static Kpi moteur(Map<String, String> moteur, String carte, String code, String libelle, String classe) {
        return new Kpi(code, libelle, moteur.getOrDefault(carte, VIDE), classe, false);
    }

    /** Prototype (renderDashRH) : engagement >= 75 teal, >= 60 gold, sinon rust ; sans reponse : gold. */
    static String classeEngagement(BigDecimal moyenne) {
        if (moyenne == null) {
            return "gold";
        }
        return moyenne.compareTo(BigDecimal.valueOf(75)) >= 0 ? "teal"
                : moyenne.compareTo(BigDecimal.valueOf(60)) >= 0 ? "gold" : "rust";
    }

    private static String compter(List<Personne> p, Predicate<Personne> critere) {
        return String.valueOf(p.stream().filter(critere).count());
    }

    /** Les 9 cases dans l'ordre de la grille : potentiel 3 a 1 (lignes), performance 1 a 3 (colonnes). */
    private static List<CaseCompte> neufBox(TableauDeBordView tableau, List<Personne> personnes, Regles regles,
                                            Filtres filtres, String trimestre) {
        return tableau.neufBox().stream()
                .sorted(Comparator.comparing(CaseTableau::niveauPotentiel, Comparator.reverseOrder())
                        .thenComparing(CaseTableau::niveauPerformance))
                .map(c -> {
                    int numero = numero(c);
                    int nombre = (int) personnes.stream()
                            .filter(p -> Integer.valueOf(numero).equals(p.caseNumero()) && regles.passe(p, "case"))
                            .count();
                    boolean choisie = Integer.valueOf(numero).equals(filtres.caseNeufBox());
                    return new CaseCompte(numero, texte(c.libelle()), c.niveauPerformance(), c.niveauPotentiel(),
                            nombre, choisie, lien(trimestre, filtres, "case", choisie ? null : String.valueOf(numero)),
                            FOND.getOrDefault(numero, ""), etiquette(numero, c.libelle()));
                }).toList();
    }

    private static List<VivierListe> viviers(List<Personne> filtrees) {
        return OptionsFiltresService.viviers().stream()
                .map(c -> new VivierListe(c.valeur(), c.libelle(), filtrees.stream()
                        .filter(p -> p.viviers().contains(c.valeur())).toList()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Puce> puces(Filtres f, Map<String, Object> dimensions, String trimestre) {
        Map<String, String> actifs = valeurs(f);
        List<Puce> puces = new ArrayList<>();
        Map<String, String> noms = Map.of("case", "9-Box", "entite", "Entité", "vigilance", "Vigilance",
                "perf", "Performance", "pot", "Potentiel", "vivier", "Vivier", "alerte", "Alerte");
        actifs.forEach((parametre, valeur) -> {
            String libelle = ((List<Map<String, Object>>) dimensions.get(parametre)).stream()
                    .filter(e -> valeur.equals(e.get("code"))).map(e -> String.valueOf(e.get("libelle")))
                    .findFirst().orElse(valeur);
            puces.add(new Puce(parametre, noms.get(parametre), libelle, lien(trimestre, f, parametre, null)));
        });
        return puces;
    }

    /** Valeur de chaque filtre actif, dans l'ordre des parametres. */
    static Map<String, String> valeurs(Filtres f) {
        Map<String, String> v = new LinkedHashMap<>();
        if (f.caseNeufBox() != null) {
            v.put("case", String.valueOf(f.caseNeufBox()));
        }
        put(v, "entite", f.entite());
        put(v, "vigilance", f.vigilance());
        put(v, "perf", f.perf());
        put(v, "pot", f.pot());
        put(v, "vivier", f.vivier());
        put(v, "alerte", f.alerte());
        return v;
    }

    private static void put(Map<String, String> v, String nom, String valeur) {
        if (valeur != null) {
            v.put(nom, valeur);
        }
    }

    /** "/" avec le trimestre et les filtres, le parametre donne remplace (null : retire). */
    static String lien(String trimestre, Filtres f, String parametre, String valeur) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath("/").queryParam("trimestre", trimestre);
        Map<String, String> v = valeurs(f);
        if (valeur == null) {
            v.remove(parametre);
        } else {
            v.put(parametre, valeur);
        }
        for (String nom : TableauDeBordInteractif.PARAMETRES) {
            if (v.containsKey(nom)) {
                lien.queryParam(nom, v.get(nom));
            }
        }
        return lien.encode().build().toUriString();
    }

    private static List<Map<String, String>> choix(Map<String, String> libelles) {
        return libelles.entrySet().stream().map(e -> Map.of("code", e.getKey(), "libelle", e.getValue())).toList();
    }

    private static Map<String, String> libelles(String... codesEtLibelles) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < codesEtLibelles.length; i += 2) {
            m.put(codesEtLibelles[i], codesEtLibelles[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
