package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Criteres;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.LigneCollaborateur;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Tri;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.PosteDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.TalentDg;
import com.talent360bank.talent360bank.ui.service.DashboardService;
import com.talent360bank.talent360bank.ui.service.ListeCollaborateursViewService;
import com.talent360bank.talent360bank.ui.service.TableauDeBordDgViewService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Liste des collaborateurs et tableau de bord DG sur le vrai classeur, apres le
 * vrai import et le vrai calcul de T3 2026 (date de reference 15/09/2026).
 * Attendus lus dans 00_DASHBOARD, 01_COLLABORATEURS, 02, 03, 08, 09 et 10_TALENTS.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:collaborateurs-dg-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class CollaborateursDgDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreService trimestreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private ListeCollaborateursViewService listeService;
    @Autowired
    private TableauDeBordDgViewService dgService;
    @Autowired
    private DashboardService dashboardService;

    private LecteurXlsx classeur;
    private Trimestre t3;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        trimestreService.modifierDateReference(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow(),
                LocalDate.of(2026, 9, 15));
        t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        calculTrimestreService.calculer(t3);
        classeur = new LecteurXlsx(FICHIER);
    }

    // --- liste des collaborateurs ------------------------------------------------------

    @Test
    void la_liste_couvre_les_100_collaborateurs_et_chaque_ligne_est_complete() {
        ListeCollaborateurs liste = liste(criteres().build());
        assertThat(liste.nbTotal()).isEqualTo(100);
        assertThat(liste.nbFiltres()).isEqualTo(100);
        assertThat(liste.donneesManquantes()).isEmpty();

        List<LigneCollaborateur> toutes = toutes(criteres());
        LigneCollaborateur bp019 = toutes.stream().filter(l -> l.matricule().equals("BP019")).findFirst().orElseThrow();
        assertThat(bp019.scorePerformance()).isEqualByComparingTo("93.20");
        assertThat(bp019.scorePotentiel()).isEqualByComparingTo("91.85");
        assertThat(bp019.neufBox().numero()).isEqualTo(9);
        assertThat(bp019.estTalent()).isTrue();
        assertThat(bp019.estTalentValide()).isFalse();
        assertThat(bp019.decisionComite()).isEqualTo("EN_ATTENTE");
        assertThat(bp019.viviers()).extracting(ListeCollaborateurs.VivierRef::code)
                .containsExactly("COMMERCIAL", "RELEVE");
        assertThat(bp019.posteCible().posteId()).isEqualTo("PST01");
        assertThat(bp019.posteCible().readiness()).isEqualTo("READY_NOW");
        assertThat(bp019.niveauVigilance()).isEqualTo("FAIBLE");
        assertThat(bp019.direction()).isEqualTo("Reseau Retail");
        assertThat(bp019.poste()).isNotBlank();
        // Toutes les lignes ont un manager sauf ceux sans Manager_ID dans 01_COLLABORATEURS!N.
        long sansManager = parEmploye("01_COLLABORATEURS").values().stream()
                .filter(ligne -> ligne.getOrDefault("N", "").isBlank()).count();
        assertThat(toutes.stream().filter(l -> l.manager() == null).count()).isEqualTo(sansManager);
    }

    @Test
    void le_filtre_par_case_9_box_redonne_la_repartition_de_00_dashboard() {
        Map<String, Integer> attendu = new HashMap<>();
        for (int ligne = 21; ligne <= 29; ligne++) {
            Map<String, String> cellules = classeur.feuille("00_DASHBOARD").get(ligne);
            attendu.put(normaliser(cellules.get("A")), (int) Double.parseDouble(cellules.get("B")));
        }
        Map<String, Integer> obtenu = new HashMap<>();
        for (int i = 1; i <= 9; i++) {
            int numero = i;
            List<LigneCollaborateur> lignes = toutes(criteres().caseNeufBox(numero));
            assertThat(lignes).allSatisfy(l -> assertThat(l.neufBox().numero()).isEqualTo(numero));
            if (!lignes.isEmpty()) {
                obtenu.put(normaliser(lignes.get(0).neufBox().libelle()), lignes.size());
            }
        }
        obtenu.entrySet().removeIf(e -> e.getValue() == 0);
        attendu.entrySet().removeIf(e -> e.getValue() == 0);
        assertThat(obtenu).isEqualTo(attendu);
    }

    @Test
    void les_filtres_talent_vivier_vigilance_et_entite_suivent_le_classeur() {
        Map<String, Map<String, String>> talents = parEmploye("10_TALENTS");
        Set<String> talentsAttendus = talents.entrySet().stream().filter(e -> "Oui".equals(e.getValue().get("E")))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        assertThat(matricules(toutes(criteres().talent(true)))).isEqualTo(talentsAttendus);
        assertThat(toutes(criteres().talent(false))).hasSize(100 - talentsAttendus.size());
        // 00_DASHBOARD E6 : talents valides par le comite.
        assertThat(toutes(criteres().talent(true)).stream().filter(LigneCollaborateur::estTalentValide).count())
                .isEqualTo((long) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(6).get("E")));

        Set<String> expertise = talents.entrySet().stream()
                .filter(e -> "Vivier Expertise".equals(e.getValue().get("I")))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        assertThat(matricules(toutes(criteres().vivier("EXPERTISE")))).isEqualTo(expertise);
        Set<String> releve = talents.entrySet().stream()
                .filter(e -> "Oui".equals(e.getValue().get("J")))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        assertThat(matricules(toutes(criteres().vivier("RELEVE")))).isEqualTo(releve);

        // 00_DASHBOARD E15 / G15 : vigilance elevee / moderee.
        assertThat(toutes(criteres().vigilance("ELEVEE")))
                .hasSize((int) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(15).get("E")));
        assertThat(toutes(criteres().vigilance("MODEREE")))
                .hasSize((int) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(15).get("G")));

        // Entite avec son sous-arbre : la direction Reseau Retail = les lignes de 01_COLLABORATEURS!H.
        Set<String> retail = parEmploye("01_COLLABORATEURS").entrySet().stream()
                .filter(e -> "Reseau Retail".equals(e.getValue().get("H")))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        String code = Entite.code(null, TypeEntite.DIRECTION, "Reseau Retail");
        assertThat(matricules(toutes(criteres().entite(code)))).isEqualTo(retail);
    }

    @Test
    void le_filtre_readiness_et_la_recherche_texte() {
        List<LigneCollaborateur> readyNow = toutes(criteres().readiness("READY_NOW"));
        assertThat(readyNow).isNotEmpty().allSatisfy(l -> assertThat(l.posteCible().readiness()).isEqualTo("READY_NOW"));
        assertThat(readyNow).extracting(LigneCollaborateur::matricule).contains("BP019");

        assertThat(toutes(criteres().recherche("BOUZID"))).extracting(LigneCollaborateur::matricule).contains("BP001");
        assertThat(toutes(criteres().recherche("bp019"))).extracting(LigneCollaborateur::matricule)
                .containsExactly("BP019");
    }

    @Test
    void tri_par_performance_et_pagination() {
        Map<String, Map<String, String>> performances = parEmploye("02_PERFORMANCE");
        String meilleur = performances.entrySet().stream()
                .max(Comparator.comparing((Map.Entry<String, Map<String, String>> e) -> decimal(e.getValue().get("I")))
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow().getKey();

        ListeCollaborateurs premiere = liste(criteres().tri(Tri.PERFORMANCE, true).page(1, 30).build());
        assertThat(premiere.nbPages()).isEqualTo(4);
        assertThat(premiere.lignes()).hasSize(30);
        assertThat(premiere.lignes().get(0).matricule()).isEqualTo(meilleur);
        assertThat(premiere.lignes()).extracting(LigneCollaborateur::scorePerformance)
                .isSortedAccordingTo(Comparator.reverseOrder());

        List<String> parPages = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            ListeCollaborateurs liste = liste(criteres().tri(Tri.PERFORMANCE, true).page(page, 30).build());
            parPages.addAll(liste.lignes().stream().map(LigneCollaborateur::matricule).toList());
        }
        assertThat(parPages).hasSize(100).doesNotHaveDuplicates();
        assertThat(liste(criteres().tri(Tri.PERFORMANCE, true).page(4, 30).build()).lignes()).hasSize(10);
    }

    // --- tableau de bord DG -------------------------------------------------------------

    @Test
    void la_releve_de_chaque_poste_critique_est_celle_du_classeur() {
        TableauDeBordDg dg = dgService.construire(t3, TableauDeBordDg.TOP_TALENTS_PAR_DEFAUT);
        assertThat(dg.erreur()).isNull();

        Map<String, Map<String, String>> postes08 = new HashMap<>();
        for (Map<String, String> ligne : classeur.feuille("08_POSTES_CRITIQUES").values()) {
            if (ligne.getOrDefault("A", "").matches("PST\\d+")) {
                postes08.put(ligne.get("A"), ligne);
            }
        }
        List<Map<String, String>> successions = classeur.feuille("09_SUCCESSION").values().stream()
                .filter(l -> l.getOrDefault("A", "").matches("PST\\d+") && l.getOrDefault("B", "").matches("BP\\d+"))
                .toList();

        assertThat(dg.postesCritiques()).extracting(PosteDg::posteId)
                .containsExactlyInAnyOrderElementsOf(postes08.keySet());
        for (PosteDg poste : dg.postesCritiques()) {
            List<Map<String, String>> lignes = successions.stream()
                    .filter(l -> poste.posteId().equals(l.get("A"))).toList();
            assertThat(poste.nbSuccesseurs()).as(poste.posteId() + " successeurs")
                    .isEqualTo((int) Double.parseDouble(postes08.get(poste.posteId()).get("G")));
            assertThat(poste.nbReadyNow()).as(poste.posteId() + " Ready Now")
                    .isEqualTo((int) lignes.stream().filter(l -> "Ready Now".equals(l.get("L"))).count());
            if (lignes.isEmpty()) {
                assertThat(poste.meilleurSuccesseur()).isNull();
                assertThat(poste.alerte()).isTrue();
            } else {
                Map<String, String> meilleure = lignes.stream()
                        .max(Comparator.comparing((Map<String, String> l) -> decimal(l.get("K")))
                                .thenComparing(l -> l.get("B"), Comparator.reverseOrder()))
                        .orElseThrow();
                assertThat(poste.meilleurSuccesseur().matricule()).as(poste.posteId() + " meilleur")
                        .isEqualTo(meilleure.get("B"));
                assertThat(poste.meilleurSuccesseur().scoreMatching())
                        .isEqualByComparingTo(decimal(postes08.get(poste.posteId()).get("H")));
            }
        }
        assertThat(dg.postesCritiques().stream().filter(p -> p.posteId().equals("PST01")).findFirst().orElseThrow()
                .nbReadyNow()).isEqualTo(3);
    }

    @Test
    void les_meilleurs_talents_sont_classes_par_performance_plus_potentiel() {
        Map<String, Map<String, String>> talents = parEmploye("10_TALENTS");
        Map<String, Map<String, String>> performances = parEmploye("02_PERFORMANCE");
        Map<String, Map<String, String>> potentiels = parEmploye("03_POTENTIEL");
        List<String> attendus = talents.entrySet().stream()
                .filter(e -> "Oui".equals(e.getValue().get("E")))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing((String id) -> decimal(performances.get(id).get("I"))
                                .add(decimal(potentiels.get(id).get("K"))), Comparator.reverseOrder())
                        .thenComparing(id -> decimal(performances.get(id).get("I")), Comparator.reverseOrder())
                        .thenComparing(id -> id))
                .toList();

        TableauDeBordDg dg = dgService.construire(t3, 8);
        assertThat(dg.topTalents()).extracting(TalentDg::matricule).containsExactlyElementsOf(attendus.subList(0, 8));
        assertThat(dg.topTalents()).extracting(TalentDg::rang).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        for (TalentDg talent : dg.topTalents()) {
            assertThat(talent.scoreCumule()).isEqualByComparingTo(talent.scorePerformance().add(talent.scorePotentiel()));
            assertThat(talent.estTalentValide()).isEqualTo("Oui".equals(talents.get(talent.matricule()).get("H")));
            assertThat(talent.posteCible()).isNotNull();
        }
        // Tous les talents quand la limite les depasse.
        assertThat(dgService.construire(t3, 50).topTalents()).hasSize(attendus.size());
    }

    @Test
    void les_cartes_du_tableau_de_bord_dg_sont_celles_du_tableau_de_bord_rh() {
        TableauDeBordDg dg = dgService.construire(t3, 8);
        assertThat(dg.kpis()).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(dashboardService.construire(t3).kpis());
        assertThat(dg.neufBox()).isEqualTo(dashboardService.construire(t3).neufBox());
    }

    // --- outils ---------------------------------------------------------------------------

    private ListeCollaborateurs liste(Criteres criteres) {
        return listeService.construire(2026, 3, criteres);
    }

    /** Toutes les lignes qui passent les criteres (une seule grande page). */
    private List<LigneCollaborateur> toutes(Constructeur constructeur) {
        return liste(constructeur.page(1, Criteres.TAILLE_MAX).build()).lignes();
    }

    private static Set<String> matricules(List<LigneCollaborateur> lignes) {
        return lignes.stream().map(LigneCollaborateur::matricule).collect(Collectors.toCollection(HashSet::new));
    }

    private Map<String, Map<String, String>> parEmploye(String feuille) {
        Map<String, Map<String, String>> lignes = new HashMap<>();
        for (Map<String, String> ligne : classeur.feuille(feuille).values()) {
            String id = ligne.getOrDefault("A", "");
            if (id.matches("BP\\d+")) {
                lignes.put(id, ligne);
            }
        }
        return lignes;
    }

    private static BigDecimal decimal(String valeur) {
        return new BigDecimal(valeur).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static Constructeur criteres() {
        return new Constructeur();
    }

    /** Criteres pas a pas, pour des tests lisibles. */
    static final class Constructeur {
        private String entite;
        private Integer caseNeufBox;
        private Boolean talent;
        private String vivier;
        private String readiness;
        private String vigilance;
        private String recherche;
        private Tri tri = Tri.NOM;
        private boolean decroissant;
        private int page = 1;
        private int taille = Criteres.TAILLE_PAR_DEFAUT;

        Constructeur entite(String v) { entite = v; return this; }
        Constructeur caseNeufBox(int v) { caseNeufBox = v; return this; }
        Constructeur talent(boolean v) { talent = v; return this; }
        Constructeur vivier(String v) { vivier = v; return this; }
        Constructeur readiness(String v) { readiness = v; return this; }
        Constructeur vigilance(String v) { vigilance = v; return this; }
        Constructeur recherche(String v) { recherche = v; return this; }
        Constructeur tri(Tri t, boolean desc) { tri = t; decroissant = desc; return this; }
        Constructeur page(int p, int t) { page = p; taille = t; return this; }

        Criteres build() {
            return new Criteres(entite, caseNeufBox, talent, vivier, readiness, vigilance, recherche, tri,
                    decroissant, page, taille);
        }
    }
}
