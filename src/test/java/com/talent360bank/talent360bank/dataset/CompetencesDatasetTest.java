package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.CompetenceSyntheseService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.CompteNiveau;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Criteres;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Exigence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.LigneCompetence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.PosteCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier.GapFrequent;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurViewService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module Competences sur le vrai classeur, apres le vrai import et le vrai
 * calcul de T3 2026. Attendus lus dans 06_EMPLOYEE_SKILLS (niveaux, gap,
 * statut), 07_POSTES (exigences), 09_SUCCESSION (N a R, M), 10_TALENTS,
 * 01_COLLABORATEURS et 00_DASHBOARD (G10).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:competences-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class CompetencesDatasetTest {

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
    private CompetenceSyntheseService service;
    @Autowired
    private FicheCollaborateurViewService ficheService;

    private LecteurXlsx classeur;
    private Trimestre t3;
    /** Lignes de 06_EMPLOYEE_SKILLS (Employee_ID en B, Competence en C, E actuel, F cible, G gap, H statut). */
    private List<Map<String, String>> skills;

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
        skills = classeur.feuille("06_EMPLOYEE_SKILLS").values().stream()
                .filter(l -> l.getOrDefault("B", "").matches("BP\\d+"))
                .toList();
    }

    private SyntheseCompetences synthese(String entite, String vivier, String poste) {
        return service.synthese(t3, new Criteres(entite, vivier, poste, false, 5));
    }

    @Test
    void chaque_competence_a_les_moyennes_les_gaps_et_la_repartition_de_06() {
        SyntheseCompetences synthese = synthese(null, null, null);
        assertThat(synthese.nbCollaborateurs()).isEqualTo(100);
        assertThat(synthese.competences()).hasSize(25);
        verifierLignes(synthese, ligne -> true);

        // Trois competences relevees dans le classeur, pour lire le test sans l'ouvrir.
        Map<String, LigneCompetence> parNom = parNom(synthese);
        assertThat(parNom.keySet()).contains("credit", "leadership", "data analytics");
        assertThat(parNom.get("credit").nbEvalues()).isEqualTo(100);
        // 00_DASHBOARD G10 : competences en gap Prioritaire.
        assertThat(synthese.nbGapsPrioritaires())
                .isEqualTo((int) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(10).get("G")))
                .isEqualTo(141);
    }

    @Test
    void les_competences_sont_triees_par_gap_moyen_et_le_top_suit() {
        SyntheseCompetences desc = synthese(null, null, null);
        assertThat(desc.competences()).extracting(LigneCompetence::gapMoyen)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(desc.topGaps()).hasSize(5);
        assertThat(desc.topGaps().get(0).gapMoyen()).isEqualByComparingTo(desc.competences().get(0).gapMoyen());

        SyntheseCompetences asc = service.synthese(t3, new Criteres(null, null, null, true, 3));
        assertThat(asc.competences()).extracting(LigneCompetence::gapMoyen).isSortedAccordingTo(Comparator.naturalOrder());
        // Le top reste les plus grands gaps, quel que soit l'ordre de la liste.
        assertThat(asc.topGaps()).isEqualTo(desc.topGaps().subList(0, 3));
    }

    @Test
    void les_filtres_entite_vivier_et_poste_critique_restreignent_la_population() {
        Map<String, Map<String, String>> collaborateurs = parEmploye("01_COLLABORATEURS");
        Set<String> retail = collaborateurs.entrySet().stream()
                .filter(e -> "Reseau Retail".equals(e.getValue().get("H")))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        SyntheseCompetences parEntite = synthese(Entite.code(null, TypeEntite.DIRECTION, "Reseau Retail"), null, null);
        assertThat(parEntite.nbCollaborateurs()).isEqualTo(retail.size());
        verifierLignes(parEntite, ligne -> retail.contains(ligne.get("B")));

        Set<String> expertise = parEmploye("10_TALENTS").entrySet().stream()
                .filter(e -> "Vivier Expertise".equals(e.getValue().get("I")))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        SyntheseCompetences parVivier = synthese(null, "EXPERTISE", null);
        assertThat(parVivier.nbCollaborateurs()).isEqualTo(expertise.size()).isEqualTo(17);
        verifierLignes(parVivier, ligne -> expertise.contains(ligne.get("B")));

        Set<String> successeursPst01 = successions("PST01").stream().map(l -> l.get("B")).collect(Collectors.toSet());
        SyntheseCompetences parPoste = synthese(null, null, "PST01");
        assertThat(parPoste.nbCollaborateurs()).isEqualTo(4);
        verifierLignes(parPoste, ligne -> successeursPst01.contains(ligne.get("B")));
        assertThat(parPoste.postesCritiques()).extracting(PosteCompetences::posteId).containsExactly("PST01");

        // Filtres combines (ET) : les successeurs de PST01 du vivier Expertise.
        assertThat(synthese(null, "EXPERTISE", "PST01").nbCollaborateurs())
                .isEqualTo((int) successeursPst01.stream().filter(expertise::contains).count());
    }

    @Test
    void les_exigences_de_PST01_sont_celles_de_07_et_les_manques_ceux_de_09() {
        PosteCompetences pst01 = synthese(null, null, null).postesCritiques().stream()
                .filter(p -> p.posteId().equals("PST01")).findFirst().orElseThrow();

        Map<String, String> poste07 = classeur.feuille("07_POSTES").values().stream()
                .filter(l -> "PST01".equals(l.get("A"))).findFirst().orElseThrow();
        List<String> noms = new ArrayList<>();
        List<Integer> niveaux = new ArrayList<>();
        for (String[] colonnes : new String[][]{{"F", "G"}, {"H", "I"}, {"J", "K"}, {"L", "M"}, {"N", "O"}}) {
            noms.add(normaliser(poste07.get(colonnes[0])));
            niveaux.add((int) Double.parseDouble(poste07.get(colonnes[1])));
        }
        assertThat(pst01.exigences()).extracting(e -> normaliser(e.competence())).containsExactlyElementsOf(noms);
        assertThat(pst01.exigences()).extracting(Exigence::niveauRequis).containsExactlyElementsOf(niveaux)
                .containsExactly(5, 4, 4, 3, 3);
        assertThat(pst01.nbSuccesseurs()).isEqualTo(4);

        // Successeurs sous le niveau : sous-score 09!N..R inferieur a 100, colonne par colonne.
        List<Map<String, String>> lignes = successions("PST01");
        String[] colonnes = {"N", "O", "P", "Q", "R"};
        for (int i = 0; i < colonnes.length; i++) {
            String colonne = colonnes[i];
            assertThat(pst01.exigences().get(i).nbSuccesseursSousLeNiveau())
                    .isEqualTo((int) lignes.stream().filter(l -> decimal(l.get(colonne)).compareTo(BigDecimal.valueOf(100)) < 0)
                            .count());
        }

        // Postes critiques : manques frequents = 09!M des successions avec un ecart reel.
        for (PosteCompetences poste : synthese(null, null, null).postesCritiques()) {
            Map<String, Integer> attendus = new TreeMap<>();
            for (Map<String, String> l : successions(poste.posteId())) {
                if (List.of(colonnes).stream().anyMatch(c -> decimal(l.get(c)).compareTo(BigDecimal.valueOf(100)) < 0)) {
                    attendus.merge(normaliser(l.get("M")), 1, Integer::sum);
                }
            }
            Map<String, Integer> obtenus = new TreeMap<>();
            poste.gapsFrequents().forEach(g -> obtenus.put(normaliser(g.competence()), g.nombre()));
            assertThat(obtenus).as(poste.posteId()).isEqualTo(attendus);
            assertThat(poste.gapsFrequents()).extracting(GapFrequent::nombre)
                    .isSortedAccordingTo(Comparator.reverseOrder());
        }
    }

    @Test
    void les_competences_de_BP001_et_BP019_dans_leur_fiche_sont_celles_de_06() {
        for (String matricule : List.of("BP001", "BP019")) {
            Map<String, String[]> attendues = skills.stream().filter(l -> matricule.equals(l.get("B")))
                    .collect(Collectors.toMap(l -> normaliser(l.get("C")),
                            l -> new String[]{l.get("E"), l.get("F"), l.get("G"), normaliser(l.get("H"))}));
            List<FicheCollaborateur.Competence> fiche = ficheService.construire(matricule, 2026, 3).competences();
            assertThat(fiche).hasSize(25);
            for (FicheCollaborateur.Competence competence : fiche) {
                String[] attendu = attendues.get(normaliser(competence.nom()));
                assertThat(competence.niveauActuel()).as(matricule + " " + competence.nom())
                        .isEqualTo(entier(attendu[0]));
                assertThat(competence.niveauRequis()).isEqualTo(entier(attendu[1]));
                assertThat(competence.gap()).isEqualTo(entier(attendu[2]));
                assertThat(normaliser(competence.statutLibelle())).isEqualTo(attendu[3]);
            }
        }
        // BP019 n'a aucun ecart ; BP001 en a sur toutes ses competences sauf celles a 0.
        assertThat(ficheService.construire("BP019", 2026, 3).competences())
                .allSatisfy(c -> assertThat(c.gap()).isZero());
        assertThat(ficheService.construire("BP001", 2026, 3).competences()).filteredOn(c -> c.gap() > 0)
                .hasSize((int) skills.stream().filter(l -> "BP001".equals(l.get("B")))
                        .filter(l -> entier(l.get("G")) > 0).count());
    }

    // --- outils ---------------------------------------------------------------------

    /** Chaque ligne de la synthese face aux lignes de 06 retenues par le filtre. */
    private void verifierLignes(SyntheseCompetences synthese, Predicate<Map<String, String>> retenue) {
        Map<String, List<Map<String, String>>> parCompetence = skills.stream().filter(retenue)
                .collect(Collectors.groupingBy(l -> normaliser(l.get("C"))));
        for (LigneCompetence ligne : synthese.competences()) {
            List<Map<String, String>> attendues = parCompetence.getOrDefault(normaliser(ligne.competence()), List.of());
            String nom = ligne.competence();
            assertThat(ligne.nbEvalues()).as(nom + " evalues").isEqualTo(attendues.size());
            if (attendues.isEmpty()) {
                assertThat(ligne.gapMoyen()).isNull();
                continue;
            }
            assertThat(ligne.niveauActuelMoyen()).as(nom + " actuel").isEqualByComparingTo(moyenne(attendues, "E"));
            assertThat(ligne.niveauCibleMoyen()).as(nom + " cible").isEqualByComparingTo(moyenne(attendues, "F"));
            assertThat(ligne.gapMoyen()).as(nom + " gap").isEqualByComparingTo(moyenne(attendues, "G"));
            int avecGap = (int) attendues.stream().filter(l -> entier(l.get("G")) > 0).count();
            assertThat(ligne.nbAvecGap()).as(nom + " avec gap").isEqualTo(avecGap);
            assertThat(ligne.pourcentageAvecGap()).isEqualByComparingTo(BigDecimal.valueOf(avecGap * 100L)
                    .divide(BigDecimal.valueOf(attendues.size()), 1, RoundingMode.HALF_UP));
            assertThat(ligne.nbPrioritaires()).as(nom + " prioritaires")
                    .isEqualTo((int) attendues.stream().filter(l -> "prioritaire".equals(normaliser(l.get("H")))).count());
            Map<Integer, Integer> repartition = new HashMap<>();
            attendues.forEach(l -> repartition.merge(entier(l.get("E")), 1, Integer::sum));
            for (CompteNiveau compte : ligne.repartition()) {
                assertThat(compte.nombre()).as(nom + " niveau " + compte.niveau())
                        .isEqualTo(repartition.getOrDefault(compte.niveau(), 0));
            }
        }
    }

    private List<Map<String, String>> successions(String posteId) {
        return classeur.feuille("09_SUCCESSION").values().stream()
                .filter(l -> posteId.equals(l.get("A")) && l.getOrDefault("B", "").matches("BP\\d+"))
                .toList();
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

    private static Map<String, LigneCompetence> parNom(SyntheseCompetences synthese) {
        return synthese.competences().stream().collect(Collectors.toMap(l -> normaliser(l.competence()), l -> l));
    }

    private static BigDecimal moyenne(List<Map<String, String>> lignes, String colonne) {
        return BigDecimal.valueOf(lignes.stream().mapToInt(l -> entier(l.get(colonne))).sum())
                .divide(BigDecimal.valueOf(lignes.size()), 2, RoundingMode.HALF_UP);
    }

    private static int entier(String valeur) {
        return (int) Double.parseDouble(valeur);
    }

    private static BigDecimal decimal(String valeur) {
        return new BigDecimal(valeur).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
