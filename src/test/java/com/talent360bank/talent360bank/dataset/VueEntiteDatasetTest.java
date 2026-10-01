package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.VueEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.ComparaisonEnfant;
import com.talent360bank.talent360bank.ui.service.VueEntiteViewService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vue entite d'une vraie region (Tanger-Tetouan sous Reseau Retail - Sud :
 * cinq collaborateurs dans quatre agences) comparee au classeur, apres le vrai
 * import et le vrai calcul de T3 2026 (date de reference : 15/09/2026).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vue-entite-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class VueEntiteDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    static final String REGION = "DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD/REG:TANGER_TETOUAN";
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
    private VueEntiteViewService service;

    private LecteurXlsx classeur;
    private VueEntite vue;
    /** Collaborateurs de la region dans le classeur, par agence (colonne K), hors archives. */
    private final Map<String, List<String>> parAgence = new LinkedHashMap<>();
    private final List<String> region = new ArrayList<>();

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
            if ("Reseau Retail".equals(ligne.get("H")) && "Reseau Retail - Sud".equals(ligne.get("I"))
                    && "Tanger-Tetouan".equals(ligne.get("J")) && !"archive".equals(normaliser(ligne.get("O")))) {
                region.add(ligne.get("A"));
                parAgence.computeIfAbsent(ligne.get("K"), agence -> new ArrayList<>()).add(ligne.get("A"));
            }
        }
        vue = service.construire(REGION, 2026, 3);
    }

    @Test
    void l_entite_et_ses_agences_sont_celles_du_classeur() {
        assertThat(region).hasSize(5);
        assertThat(vue.entite().libelle()).isEqualTo("Tanger-Tetouan");
        assertThat(vue.entite().type()).isEqualTo("REGION");
        assertThat(vue.entite().chemin()).containsExactly("Reseau Retail", "Reseau Retail - Sud", "Tanger-Tetouan");
        assertThat(vue.entite().parent().libelle()).isEqualTo("Reseau Retail - Sud");
        assertThat(vue.enfants()).extracting(ComparaisonEnfant::libelle)
                .containsExactlyInAnyOrderElementsOf(parAgence.keySet());
        for (ComparaisonEnfant agence : vue.enfants()) {
            List<String> membres = parAgence.get(agence.libelle());
            assertThat(agence.effectif()).as(agence.libelle()).isEqualTo(membres.size());
            assertProche(agence.libelle(), agence.moyennePerformance(), moyenne(membres, "02_PERFORMANCE", "I"));
            assertProche(agence.libelle(), agence.moyennePotentiel(), moyenne(membres, "03_POTENTIEL", "K"));
            assertProche(agence.libelle(), agence.moyenneEngagement(), moyenne(membres, "12_VIGILANCE", "D"));
        }
        // La synthese de la region est la somme de ses agences.
        assertThat(vue.enfants().stream().mapToInt(ComparaisonEnfant::effectif).sum())
                .isEqualTo(vue.synthese().effectif());
    }

    @Test
    void la_synthese_est_celle_du_classeur() {
        VueEntite.EntiteVue entite = vue.entite();
        var synthese = vue.synthese();
        assertThat(entite.enfants().stream().mapToInt(VueEntite.EntiteEnfant::effectif).sum()).isEqualTo(5);
        assertThat(synthese.effectif()).isEqualTo(5);
        assertThat(synthese.nbAvecScore()).isEqualTo(5);
        assertProche("moyenne performance", synthese.moyennePerformance(), moyenne(region, "02_PERFORMANCE", "I"));
        assertProche("moyenne potentiel", synthese.moyennePotentiel(), moyenne(region, "03_POTENTIEL", "K"));
        assertProche("moyenne engagement", synthese.moyenneEngagement(), moyenne(region, "12_VIGILANCE", "D"));
        assertThat(synthese.nbTalents()).isEqualTo(compter(id -> "Oui".equals(ligne("10_TALENTS", id).get("E"))));
        assertThat(synthese.nbHautsPotentiels())
                .isEqualTo(compter(id -> "Oui".equals(ligne("10_TALENTS", id).get("F"))));
        assertThat(synthese.nbVivierSuccession())
                .isEqualTo(compter(id -> "Oui".equals(ligne("10_TALENTS", id).get("J"))));
        for (var compte : synthese.neufBox()) {
            assertThat(compte.nombre()).as("case " + compte.numero()).isEqualTo(
                    compter(id -> (int) Double.parseDouble(ligne("04_9BOX", id).get("H")) == compte.numero()));
        }
        for (var compte : synthese.niveauxVigilance()) {
            assertThat(compte.nombre()).as(compte.code()).isEqualTo(compter(id ->
                    normaliser(ligne("12_VIGILANCE", id).get("M")).equals(normaliser(compte.libelle()))));
        }
        assertThat(synthese.nbSansVigilance()).isZero();
        assertThat(vue.alertes()).hasSize(synthese.niveauxVigilance().get(2).nombre());
        assertThat(vue.donneesManquantes()).isEmpty();
    }

    @Test
    void postes_critiques_tenus_dans_la_region() {
        List<String> attendus = new ArrayList<>();
        for (Map<String, String> ligne : classeur.feuille("08_POSTES_CRITIQUES").values()) {
            if (ligne.getOrDefault("A", "").matches("PST\\d+") && region.contains(ligne.get("E"))) {
                attendus.add(ligne.get("A"));
            }
        }
        assertThat(vue.postesCritiques()).extracting(VueEntite.PosteCritiqueVue::posteId)
                .containsExactlyInAnyOrderElementsOf(attendus);
        for (VueEntite.PosteCritiqueVue poste : vue.postesCritiques()) {
            Map<String, String> ligne = ligne("08_POSTES_CRITIQUES", poste.posteId());
            assertThat(poste.nbSuccesseurs()).as(poste.posteId()).isEqualTo((int) Double.parseDouble(ligne.get("G")));
            assertThat(poste.rattachement()).isEqualTo("TITULAIRE");
        }
    }

    // ------------------------------------------------------------ outils

    private Map<String, String> ligne(String feuille, String cle) {
        for (Map<String, String> ligne : classeur.feuille(feuille).values()) {
            if (cle.equals(ligne.get("A"))) {
                return ligne;
            }
        }
        throw new AssertionError(cle + " absent de " + feuille);
    }

    private int compter(Predicate<String> condition) {
        return (int) region.stream().filter(condition).count();
    }

    private String moyenne(List<String> matricules, String feuille, String colonne) {
        BigDecimal somme = BigDecimal.ZERO;
        for (String id : matricules) {
            somme = somme.add(new BigDecimal(ligne(feuille, id).get(colonne)));
        }
        return somme.divide(BigDecimal.valueOf(matricules.size()), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void assertProche(String quoi, BigDecimal obtenu, String attendu) {
        assertThat(obtenu).as(quoi).isNotNull();
        assertThat(obtenu.doubleValue()).as(quoi).isCloseTo(Double.parseDouble(attendu), within(TOLERANCE));
    }

    private static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
