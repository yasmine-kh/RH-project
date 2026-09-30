package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Fiche d'un vrai collaborateur (BP005, successeur identifie sur PST01 et
 * PST02) comparee aux valeurs du classeur, bloc par bloc, apres le vrai
 * import et le vrai calcul du trimestre T3 2026 (date de reference du
 * classeur : 15/09/2026).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne (CI, poste d'un collegue).
 * Base en memoire a part : le classeur entier n'y gene pas les autres tests.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fiche-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class FicheCollaborateurDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final String MATRICULE = "BP005";
    private static final double TOLERANCE = 0.01;

    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreService trimestreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private FicheCollaborateurViewService service;

    private LecteurXlsx classeur;
    private FicheCollaborateur fiche;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));
        ResultatImport resultat = importService.importer(fichier, 2026, 3);
        assertThat(resultat.statut()).isEqualTo(StatutImport.SUCCES);

        Trimestre t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        trimestreService.modifierDateReference(t3, LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow());

        classeur = new LecteurXlsx(FICHIER);
        fiche = service.construire(MATRICULE, 2026, 3);
    }

    @Test
    void identite() {
        Map<String, String> ligne = ligne("01_COLLABORATEURS");
        assertThat(fiche.identite().nom()).isEqualTo(ligne.get("B"));
        assertThat(fiche.identite().prenom()).isEqualTo(ligne.get("C"));
        assertThat(fiche.identite().entite().chemin().get(0)).isEqualTo(ligne.get("H"));
        // Anciennete en annees decimales au 15/09/2026, comme la colonne G.
        assertProche(fiche.identite().anciennete(), ligne.get("G"));
        assertThat(fiche.identite().anciennete()).isEqualByComparingTo("3.3");
    }

    @Test
    void performance_potentiel_et_9_box() {
        Map<String, String> performance = ligne("02_PERFORMANCE");
        assertProche(fiche.performance().score(), performance.get("I"));
        assertThat(normaliser(fiche.performance().categorieLibelle())).isEqualTo(normaliser(performance.get("J")));
        assertProche(fiche.performance().criteres().get(0).note(), performance.get("D"));

        Map<String, String> potentiel = ligne("03_POTENTIEL");
        assertProche(fiche.potentiel().score(), potentiel.get("K"));
        assertThat(normaliser(fiche.potentiel().categorieLibelle())).isEqualTo(normaliser(potentiel.get("L")));

        Map<String, String> neufBox = ligne("04_9BOX");
        assertThat(fiche.neufBox().numero()).isEqualTo((int) Double.parseDouble(neufBox.get("H")));
        assertThat(normaliser(fiche.neufBox().libelle())).isEqualTo(normaliser(neufBox.get("I")));
    }

    @Test
    void talent_decision_du_comite_et_vivier_thematique() {
        Map<String, String> talents = ligne("10_TALENTS");
        assertThat(ouiNon(fiche.talent().estTalent())).isEqualTo(talents.get("E"));
        assertThat(ouiNon(fiche.talent().estHautPotentiel())).isEqualTo(talents.get("F"));
        assertThat(ouiNon(fiche.talent().estVivierSuccession())).isEqualTo(talents.get("J"));
        assertThat(normaliser(fiche.talent().decisionComiteLibelle())).isEqualTo(normaliser(talents.get("G")));
        assertThat(fiche.talent().viviers())
                .filteredOn(vivier -> "THEMATIQUE".equals(vivier.origine()))
                .extracting(FicheCollaborateur.VivierFiche::libelle)
                .containsExactly(talents.get("I"));
    }

    @Test
    void competences() {
        Map<String, Map<String, String>> attendues = new HashMap<>();
        for (Map<String, String> ligne : classeur.feuille("06_EMPLOYEE_SKILLS").values()) {
            if (MATRICULE.equals(ligne.get("B"))) {
                attendues.put(normaliser(ligne.get("C")), ligne);
            }
        }
        assertThat(fiche.competences()).hasSize(attendues.size()).isNotEmpty();
        for (FicheCollaborateur.Competence competence : fiche.competences()) {
            Map<String, String> ligne = attendues.get(normaliser(competence.nom()));
            assertThat(ligne).as(competence.nom()).isNotNull();
            assertThat(competence.niveauActuel()).isEqualTo((int) Double.parseDouble(ligne.get("E")));
            assertThat(competence.niveauRequis()).isEqualTo((int) Double.parseDouble(ligne.get("F")));
            assertThat(normaliser(competence.statutLibelle())).as(competence.nom())
                    .isEqualTo(normaliser(ligne.get("H")));
        }
    }

    @Test
    void engagement_et_vigilance() {
        Map<String, String> vigilance = ligne("12_VIGILANCE");
        assertProche(fiche.engagement(), vigilance.get("D"));
        assertProche(fiche.vigilance().indice(), vigilance.get("L"));
        assertThat(normaliser(fiche.vigilance().niveauLibelle())).isEqualTo(normaliser(vigilance.get("M")));
        BigDecimal somme = fiche.vigilance().raisons().stream()
                .map(FicheCollaborateur.RaisonVigilance::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somme).isEqualByComparingTo(fiche.vigilance().indice());
    }

    @Test
    void succession_sur_les_postes_critiques() {
        Map<String, String> critiques = new HashMap<>();
        for (Map<String, String> ligne : classeur.feuille("08_POSTES_CRITIQUES").values()) {
            String poste = ligne.getOrDefault("A", "");
            if (poste.matches("PST\\d+")) {
                critiques.put(poste, poste);
            }
        }
        Map<String, Map<String, String>> attendues = new LinkedHashMap<>();
        for (Map<String, String> ligne : classeur.feuille("09_SUCCESSION").values()) {
            if (MATRICULE.equals(ligne.get("B")) && critiques.containsKey(ligne.get("A"))) {
                attendues.put(ligne.get("A"), ligne);
            }
        }

        assertThat(fiche.successions()).extracting(FicheCollaborateur.Succession::posteId)
                .containsExactlyInAnyOrderElementsOf(attendues.keySet())
                .contains("PST01", "PST02");
        for (FicheCollaborateur.Succession succession : fiche.successions()) {
            Map<String, String> ligne = attendues.get(succession.posteId());
            assertProche(succession.scoreMatching(), ligne.get("K"));
            // Libelles du classeur, comme dans DatasetExcelComparaisonTest.
            String readinessClasseur = switch (succession.readiness()) {
                case "READY_NOW" -> "Ready Now";
                case "MOINS_1_AN" -> "Ready < 1 an";
                case "ENTRE_1_ET_2_ANS" -> "Ready 1-2 ans";
                default -> "Ready > 2 ans";
            };
            assertThat(normaliser(ligne.get("L"))).as(succession.posteId()).isEqualTo(normaliser(readinessClasseur));
        }
    }

    @Test
    void rien_ne_manque_pour_ce_collaborateur() {
        assertThat(fiche.donneesManquantes()).isEmpty();
        assertThat(fiche.historique()).isEmpty();
    }

    // ------------------------------------------------------------ outils

    private Map<String, String> ligne(String feuille) {
        for (Map<String, String> ligne : classeur.feuille(feuille).values()) {
            if (MATRICULE.equals(ligne.get("A"))) {
                return ligne;
            }
        }
        throw new AssertionError(MATRICULE + " absent de " + feuille);
    }

    private static void assertProche(BigDecimal obtenu, String attendu) {
        assertThat(obtenu).isNotNull();
        assertThat(obtenu.doubleValue()).isCloseTo(Double.parseDouble(attendu), within(TOLERANCE));
    }

    private static String ouiNon(Boolean valeur) {
        return Boolean.TRUE.equals(valeur) ? "Oui" : "Non";
    }

    /** Sans accents, casse ni espaces multiples : le classeur n'en porte pas. */
    private static String normaliser(String texte) {
        if (texte == null) {
            return null;
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
