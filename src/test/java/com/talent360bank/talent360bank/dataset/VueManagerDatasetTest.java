package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import com.talent360bank.talent360bank.ui.service.VueManagerViewService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vue manager d'un vrai manager (BP005, cinq collaborateurs directs) comparee
 * au classeur, membre par membre puis en synthese, apres le vrai import et le
 * vrai calcul de T3 2026 (date de reference du classeur : 15/09/2026).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vue-manager-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class VueManagerDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final String MANAGER = "BP005";
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
    private VueManagerViewService service;

    private LecteurXlsx classeur;
    private VueManager vue;
    /** Equipe du classeur : lignes de 01_COLLABORATEURS dont le Manager_ID (N) est BP005, hors archives. */
    private final List<String> equipe = new ArrayList<>();

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        Trimestre t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        trimestreService.modifierDateReference(t3, LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow());

        classeur = new LecteurXlsx(FICHIER);
        for (Map<String, String> ligne : classeur.feuille("01_COLLABORATEURS").values()) {
            if (MANAGER.equals(ligne.get("N")) && !"archive".equals(normaliser(ligne.get("O")))) {
                equipe.add(ligne.get("A"));
            }
        }
        vue = service.construire(MANAGER, 2026, 3);
    }

    @Test
    void l_equipe_est_celle_du_classeur_triee_par_nom() {
        assertThat(equipe).hasSize(5);
        assertThat(vue.membres()).extracting(Membre::matricule).containsExactlyInAnyOrderElementsOf(equipe);
        List<String> noms = vue.membres().stream().map(m -> m.nom() + " " + m.prenom()).toList();
        assertThat(noms).isSortedAccordingTo(String::compareTo);
        assertThat(vue.manager().matricule()).isEqualTo(MANAGER);
        assertThat(vue.manager().neufBox().numero()).isEqualTo(entier(ligne("04_9BOX", MANAGER).get("H")));
        assertThat(vue.donneesManquantes()).isEmpty();
    }

    @Test
    void chaque_membre_a_les_valeurs_du_classeur() {
        for (Membre membre : vue.membres()) {
            String id = membre.matricule();
            assertProche(id, membre.scorePerformance(), ligne("02_PERFORMANCE", id).get("I"));
            assertThat(normaliser(membre.categoriePerformanceLibelle())).as(id)
                    .isEqualTo(normaliser(ligne("02_PERFORMANCE", id).get("J")));
            assertProche(id, membre.scorePotentiel(), ligne("03_POTENTIEL", id).get("K"));
            assertThat(membre.neufBox().numero()).as(id).isEqualTo(entier(ligne("04_9BOX", id).get("H")));
            assertThat(normaliser(membre.neufBox().libelle())).as(id)
                    .isEqualTo(normaliser(ligne("04_9BOX", id).get("I")));
            Map<String, String> talents = ligne("10_TALENTS", id);
            assertThat(ouiNon(membre.estTalent())).as(id).isEqualTo(talents.get("E"));
            assertThat(ouiNon(membre.estHautPotentiel())).as(id).isEqualTo(talents.get("F"));
            assertThat(ouiNon(membre.estVivierSuccession())).as(id).isEqualTo(talents.get("J"));
            Map<String, String> vigilance = ligne("12_VIGILANCE", id);
            assertProche(id, membre.engagement(), vigilance.get("D"));
            assertProche(id, membre.indiceVigilance(), vigilance.get("L"));
            assertThat(normaliser(membre.niveauVigilanceLibelle())).as(id).isEqualTo(normaliser(vigilance.get("M")));
            assertThat(membre.aDesDonnees()).isTrue();
        }
    }

    @Test
    void la_synthese_est_celle_du_classeur() {
        VueManager.Synthese synthese = vue.synthese();
        assertThat(synthese.effectif()).isEqualTo(equipe.size());
        assertThat(synthese.nbAvecScore()).isEqualTo(equipe.size());
        assertProche("moyenne performance", synthese.moyennePerformance(), moyenne("02_PERFORMANCE", "I"));
        assertProche("moyenne potentiel", synthese.moyennePotentiel(), moyenne("03_POTENTIEL", "K"));
        assertProche("moyenne engagement", synthese.moyenneEngagement(), moyenne("12_VIGILANCE", "D"));
        assertThat(synthese.nbTalents()).isEqualTo(compterOui("E"));
        assertThat(synthese.nbHautsPotentiels()).isEqualTo(compterOui("F"));
        assertThat(synthese.nbVivierSuccession()).isEqualTo(compterOui("J"));
        for (VueManager.CompteCase compte : synthese.neufBox()) {
            long attendu = equipe.stream().filter(id -> entier(ligne("04_9BOX", id).get("H")) == compte.numero())
                    .count();
            assertThat(compte.nombre()).as("case " + compte.numero()).isEqualTo((int) attendu);
        }
        for (VueManager.Compte compte : synthese.niveauxVigilance()) {
            long attendu = equipe.stream()
                    .filter(id -> normaliser(ligne("12_VIGILANCE", id).get("M")).equals(normaliser(compte.libelle())))
                    .count();
            assertThat(compte.nombre()).as(compte.code()).isEqualTo((int) attendu);
        }
        assertThat(synthese.nbSansVigilance()).isZero();
        // Une alerte de vigilance par membre au niveau Elevee.
        assertThat(vue.alertes()).filteredOn(a -> a.type().equals("VIGILANCE_ELEVEE"))
                .hasSize(synthese.niveauxVigilance().get(2).nombre());
    }

    // ------------------------------------------------------------ outils

    private Map<String, String> ligne(String feuille, String matricule) {
        for (Map<String, String> ligne : classeur.feuille(feuille).values()) {
            if (matricule.equals(ligne.get("A"))) {
                return ligne;
            }
        }
        throw new AssertionError(matricule + " absent de " + feuille);
    }

    private String moyenne(String feuille, String colonne) {
        BigDecimal somme = BigDecimal.ZERO;
        for (String id : equipe) {
            somme = somme.add(new BigDecimal(ligne(feuille, id).get(colonne)));
        }
        return somme.divide(BigDecimal.valueOf(equipe.size()), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private int compterOui(String colonne) {
        return (int) equipe.stream().filter(id -> "Oui".equals(ligne("10_TALENTS", id).get(colonne))).count();
    }

    private static int entier(String valeur) {
        return (int) Double.parseDouble(valeur);
    }

    private static void assertProche(String quoi, BigDecimal obtenu, String attendu) {
        assertThat(obtenu).as(quoi).isNotNull();
        assertThat(obtenu.doubleValue()).as(quoi).isCloseTo(Double.parseDouble(attendu), within(TOLERANCE));
    }

    private static String ouiNon(Boolean valeur) {
        return Boolean.TRUE.equals(valeur) ? "Oui" : "Non";
    }

    /** Sans accents, casse ni espaces multiples : le classeur n'en porte pas. */
    private static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
