package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import com.talent360bank.talent360bank.ui.service.DashboardService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tableau de bord RH sur le vrai classeur, apres le vrai import et le vrai
 * calcul de T3 2026, compare a sa feuille 00_DASHBOARD (chiffres cles des
 * lignes 6, 10 et 15, repartition 9-box des lignes 21 a 29).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tableau-de-bord-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class TableauDeBordDatasetTest {

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
    private DashboardService dashboardService;

    private LecteurXlsx classeur;
    private TableauDeBordView tableau;
    private Map<String, String> kpis;

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
        Trimestre t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        calculTrimestreService.calculer(t3);

        classeur = new LecteurXlsx(FICHIER);
        tableau = dashboardService.construire(t3);
        kpis = new LinkedHashMap<>();
        for (KpiCard kpi : tableau.kpis()) {
            kpis.put(kpi.getLabel(), kpi.getValue());
        }
    }

    private String cellule(int ligne, String colonne) {
        return classeur.feuille("00_DASHBOARD").get(ligne).get(colonne);
    }

    private int entier(int ligne, String colonne) {
        return (int) Double.parseDouble(cellule(ligne, colonne));
    }

    @Test
    void les_chiffres_cles_sont_ceux_de_00_dashboard() {
        assertThat(tableau.erreur()).isNull();
        // E6 Talents valides (Comite), G6 Hauts potentiels proposes, C6 Postes critiques suivis.
        assertThat(kpis.get("Talents validés par le Comité")).isEqualTo(String.valueOf(entier(6, "E")))
                .isEqualTo("8");
        assertThat(kpis.get("Hauts potentiels")).isEqualTo(String.valueOf(entier(6, "G"))).isEqualTo("21");
        assertThat(kpis.get("Postes critiques")).isEqualTo(String.valueOf(entier(6, "C"))).isEqualTo("15");
        // A10 Taux de couverture succession, arrondi a l'unite dans le classeur (93), au centieme ici.
        assertThat(new BigDecimal(kpis.get("Couverture succession").replace(" %", ""))
                .setScale(0, RoundingMode.HALF_UP).intValue()).isEqualTo(entier(10, "A")).isEqualTo(93);
        // C10 Successeurs 'Ready Now', E10 Postes sans successeur, G10 Competences en gap 'Prioritaire'.
        assertThat(kpis.get("Successeurs Ready Now")).isEqualTo(String.valueOf(entier(10, "C"))).isEqualTo("16");
        assertThat(kpis.get("Postes critiques sans successeur")).isEqualTo(String.valueOf(entier(10, "E")))
                .isEqualTo("1");
        assertThat(kpis.get("Compétences en gap prioritaire")).isEqualTo(String.valueOf(entier(10, "G")))
                .isEqualTo("141");
        // E15 vigilance elevee + G15 vigilance moderee = a risque.
        assertThat(kpis.get("À risque (vigilance modérée ou élevée)"))
                .isEqualTo(String.valueOf(entier(15, "E") + entier(15, "G"))).isEqualTo("45");
        assertThat(tableau.vigilance().get(2).nombre()).isEqualTo(entier(15, "E"));
        assertThat(tableau.vigilance().get(1).nombre()).isEqualTo(entier(15, "G"));
        // A6 compte toutes les lignes de 01_COLLABORATEURS ; le tableau de bord, les actifs (colonne O).
        long actifs = classeur.feuille("01_COLLABORATEURS").values().stream()
                .filter(ligne -> ligne.get("A") != null && ligne.get("A").matches("BP\\d+"))
                .filter(ligne -> "actif".equals(normaliser(ligne.get("O"))))
                .count();
        assertThat(kpis.get("Collaborateurs actifs")).isEqualTo(String.valueOf(actifs));
        assertThat(entier(6, "A")).isGreaterThanOrEqualTo((int) actifs);
    }

    @Test
    void la_repartition_9_box_est_celle_de_00_dashboard() {
        Map<String, Integer> classeur9Box = new HashMap<>();
        for (int ligne = 21; ligne <= 29; ligne++) {
            classeur9Box.put(normaliser(cellule(ligne, "A")), entier(ligne, "B"));
        }
        Map<String, Integer> nous = new HashMap<>();
        for (CaseTableau case9 : tableau.neufBox()) {
            nous.put(normaliser(case9.libelle()), case9.nombre());
        }
        assertThat(nous).isEqualTo(classeur9Box);
        assertThat(tableau.nbPlaces9Box()).isEqualTo(100);
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
