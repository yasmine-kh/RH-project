package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.AlertesView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.service.AlertesViewService;
import com.talent360bank.talent360bank.ui.service.DashboardService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ecran Alertes sur le vrai classeur, apres le vrai import et le vrai calcul de
 * T3 2026 : les alertes qui ont un equivalent dans la feuille 00_DASHBOARD en
 * reprennent les chiffres, et le total est celui du panneau du tableau de bord.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:alertes-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class AlertesDatasetTest {

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
    private AlertesViewService alertesViewService;
    @Autowired
    private DashboardService dashboardService;

    private LecteurXlsx classeur;
    private AlertesView vue;
    private TableauDeBordView tableau;
    private Map<String, Integer> parType;

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
        vue = alertesViewService.construire(t3, AlertesView.Filtres.AUCUN);
        tableau = dashboardService.construire(t3);
        parType = vue.parType().stream()
                .collect(Collectors.toMap(AlertesView.Compteur::code, AlertesView.Compteur::nombre));
    }

    private int entier(int ligne, String colonne) {
        return (int) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(ligne).get(colonne));
    }

    @Test
    void les_alertes_reprennent_les_chiffres_de_00_dashboard() {
        assertThat(vue.erreur()).isNull();
        // E10 Postes sans successeur.
        assertThat(parType.get("POSTE_SANS_SUCCESSEUR")).isEqualTo(entier(10, "E")).isEqualTo(1);
        // E15 Vigilance elevee.
        assertThat(parType.get("VIGILANCE_ELEVEE")).isEqualTo(entier(15, "E")).isEqualTo(10);
        // Le classeur ne contient que des evaluations du manager, completes : ni manquante, ni auto-evaluation.
        assertThat(parType.get("EVALUATION_MANAGER_MANQUANTE")).isZero();
        assertThat(parType.get("ECART_AUTO_MANAGER")).isZero();
        assertThat(parType.get("POSTE_SOUS_MINIMUM")).isZero();
        // Plus de 10 collaborateurs ont un gap Prioritaire (141 au total) : seuls les 10 premiers sont listes.
        assertThat(parType.get("GAPS_COMPETENCES_PRIORITAIRES")).isEqualTo(AlertesViewService.TOP_GAPS_COMPETENCES);
        // Talents proposes sans ligne de decision dans 09_VALIDATION_COMITE.
        assertThat(parType.get("TALENT_SANS_DECISION")).isEqualTo(2);
        assertThat(vue.total()).isEqualTo(23);
        assertThat(vue.parSeverite()).extracting(AlertesView.Compteur::nombre).containsExactly(1, 10, 12);
    }

    @Test
    void le_total_est_celui_du_panneau_du_tableau_de_bord() {
        assertThat(tableau.nbAlertes()).isEqualTo(vue.total());
        assertThat(tableau.alertes()).isEqualTo(vue.alertes().subList(0, TableauDeBordView.ALERTES_AFFICHEES));
    }
}
