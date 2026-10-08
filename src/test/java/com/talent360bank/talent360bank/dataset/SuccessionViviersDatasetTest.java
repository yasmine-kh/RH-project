package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.PosteCibleService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.VivierSyntheseService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.PosteCritiqueRow;
import com.talent360bank.talent360bank.ui.service.DashboardService;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurViewService;
import com.talent360bank.talent360bank.ui.service.PosteCritiqueViewService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plus grand gap, successeurs des postes critiques, syntheses des viviers et
 * poste cible, sur le vrai classeur apres le vrai import et le vrai calcul de
 * T3 2026 (date de reference 15/09/2026, celle du classeur).
 *
 * <p>Les valeurs attendues sont lues dans le classeur (09_SUCCESSION,
 * 10_TALENTS, 02_PERFORMANCE, 03_POTENTIEL) ; seul le poste cible, que le
 * classeur ne calcule pas, a des valeurs ecrites ici (voir son test).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:succession-viviers-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class SuccessionViviersDatasetTest {

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
    private PosteCritiqueService posteCritiqueService;
    @Autowired
    private PosteCritiqueViewService posteCritiqueViewService;
    @Autowired
    private VivierSyntheseService vivierSyntheseService;
    @Autowired
    private PosteCibleService posteCibleService;
    @Autowired
    private FicheCollaborateurViewService ficheCollaborateurViewService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private com.talent360bank.talent360bank.ui.service.TableauDeBordInteractifService tableauDeBordInteractif;

    private LecteurXlsx classeur;
    private Trimestre t3;
    private Map<String, CouverturePoste> couvertures;
    /** Lignes de 09_SUCCESSION (Poste_ID en A, Employee_ID en B). */
    private List<Map<String, String>> successions;

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
        couvertures = new LinkedHashMap<>();
        for (CouverturePoste couverture : posteCritiqueService.listerPostesCritiques(t3)) {
            couvertures.put(couverture.poste().getPosteId(), couverture);
        }
        successions = classeur.feuille("09_SUCCESSION").values().stream()
                .filter(ligne -> ligne.getOrDefault("A", "").matches("PST\\d+"))
                .filter(ligne -> ligne.getOrDefault("B", "").matches("BP\\d+"))
                .toList();
    }

    // --- 1. plus grand gap ---------------------------------------------------

    @Test
    void le_plus_grand_gap_de_chaque_succession_est_celui_du_classeur() {
        assertThat(successions).hasSize(44);
        List<String> ecarts = new ArrayList<>();
        for (Map<String, String> ligne : successions) {
            ResultatMatching successeur = successeur(ligne.get("A"), ligne.get("B"));
            String obtenu = successeur.plusGrandGap() == null ? null : successeur.plusGrandGap().competence();
            if (!normaliser(ligne.get("M")).equals(normaliser(obtenu))) {
                ecarts.add(ligne.get("A") + "/" + ligne.get("B") + " attendu " + ligne.get("M") + " obtenu " + obtenu);
            }
            // Un ecart reel exactement quand un sous-score N:R est sous 100.
            boolean ecartClasseur = List.of("N", "O", "P", "Q", "R").stream()
                    .anyMatch(colonne -> decimal(ligne.get(colonne)).compareTo(new BigDecimal("100")) < 0);
            if (ecartClasseur != successeur.plusGrandGap().aUnEcart()) {
                ecarts.add(ligne.get("A") + "/" + ligne.get("B") + " ecart reel attendu " + ecartClasseur);
            }
        }
        assertThat(ecarts).isEmpty();
    }

    // --- 2. tous les successeurs d'un poste critique ---------------------------

    @Test
    void tous_les_successeurs_de_PST01_sont_ceux_du_classeur_du_meilleur_au_moins_bon() {
        List<Map<String, String>> attendus = successions.stream()
                .filter(ligne -> "PST01".equals(ligne.get("A")))
                .sorted(Comparator.comparing((Map<String, String> ligne) -> decimal(ligne.get("K")))
                        .reversed())
                .toList();

        List<ResultatMatching> obtenus = couvertures.get("PST01").successeurs();
        assertThat(obtenus).extracting(s -> s.candidat().getIdCollaborateur())
                .containsExactly("BP035", "BP013", "BP019", "BP005")
                .containsExactlyElementsOf(attendus.stream().map(ligne -> ligne.get("B")).toList());
        for (int i = 0; i < attendus.size(); i++) {
            assertThat(obtenus.get(i).scoreMatching()).isEqualByComparingTo(decimal(attendus.get(i).get("K")));
            assertThat(normaliser(libelleClasseur(obtenus.get(i).readiness())))
                    .isEqualTo(normaliser(attendus.get(i).get("L")));
        }

        // Vue de l'ecran : le meilleur successeur reste celui d'avant, la liste complete s'ajoute.
        PosteCritiqueRow ligne = ligneEcran("Directeur regional");
        assertThat(ligne.getCandidatPotentiel()).isEqualTo(obtenus.get(0).candidat().getNomComplet());
        assertThat(ligne.getScoreMatching()).isEqualByComparingTo("95.20");
        assertThat(ligne.getSuccesseurs()).extracting(PosteCritiqueRow.SuccesseurRow::matricule)
                .containsExactly("BP035", "BP013", "BP019", "BP005");
        assertThat(ligne.getSuccesseurs()).extracting(PosteCritiqueRow.SuccesseurRow::readiness)
                .containsExactly("READY_NOW", "READY_NOW", "READY_NOW", "MOINS_1_AN");
        // Aucun ecart sur PST01 dans le classeur : pas de competence affichee.
        assertThat(ligne.getSuccesseurs()).allSatisfy(successeur -> {
            assertThat(successeur.gapCompetence()).isNull();
            assertThat(successeur.gapNiveaux()).isZero();
        });
    }

    @Test
    void PST13_n_a_aucun_successeur_et_reste_en_alerte() {
        assertThat(successions).noneMatch(ligne -> "PST13".equals(ligne.get("A")));
        CouverturePoste pst13 = couvertures.get("PST13");
        assertThat(pst13.successeurs()).isEmpty();
        assertThat(pst13.estEnAlerte()).isTrue();

        PosteCritiqueRow ligne = ligneEcran(pst13.poste().getNomPoste());
        assertThat(ligne.getSuccesseurs()).isEmpty();
        assertThat(ligne.getCandidatPotentiel()).isEqualTo("-");
        assertThat(ligne.isEnAlerte()).isTrue();
    }

    // --- 3. syntheses des viviers -----------------------------------------------

    @Test
    void les_syntheses_des_viviers_sont_celles_du_classeur() {
        Map<String, Map<String, String>> talents = parEmploye("10_TALENTS");
        Map<String, Map<String, String>> performances = parEmploye("02_PERFORMANCE");
        Map<String, Map<String, String>> potentiels = parEmploye("03_POTENTIEL");

        List<SyntheseVivier> syntheses = vivierSyntheseService.synthese(t3);
        assertThat(syntheses).extracting(SyntheseVivier::code)
                .containsExactly("COMMERCIAL", "DIGITAL", "EXPERTISE", "MANAGEMENT", "RISQUES", "RELEVE");

        for (SyntheseVivier synthese : syntheses) {
            Predicate<Map<String, String>> membre = synthese.releve()
                    ? ligne -> "Oui".equals(ligne.get("E")) || "Oui".equals(ligne.get("F"))
                    : ligne -> normaliser(synthese.libelle()).equals(normaliser(ligne.get("I")));
            Set<String> ids = talents.entrySet().stream().filter(e -> membre.test(e.getValue()))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            List<Map<String, String>> successionsDesMembres = successions.stream()
                    .filter(ligne -> ids.contains(ligne.get("B"))).toList();

            String nom = synthese.code();
            assertThat(synthese.effectif()).as(nom + " effectif").isEqualTo(ids.size());
            assertThat(synthese.nbTalents()).as(nom + " talents")
                    .isEqualTo(compter(ids, id -> "Oui".equals(talents.get(id).get("E"))));
            assertThat(synthese.nbHautsPotentiels()).as(nom + " hauts potentiels")
                    .isEqualTo(compter(ids, id -> "Oui".equals(talents.get(id).get("F"))));
            assertThat(synthese.nbReadyNow()).as(nom + " Ready Now").isEqualTo((int) successionsDesMembres.stream()
                    .filter(ligne -> "Ready Now".equals(ligne.get("L"))).map(ligne -> ligne.get("B")).distinct()
                    .count());
            assertThat(synthese.performanceMoyenne()).as(nom + " performance moyenne")
                    .isEqualByComparingTo(moyenne(ids, id -> decimal(performances.get(id).get("I"))));
            assertThat(synthese.potentielMoyen()).as(nom + " potentiel moyen")
                    .isEqualByComparingTo(moyenne(ids, id -> decimal(potentiels.get(id).get("K"))));
            assertThat(synthese.nbPostesCouverts()).as(nom + " postes couverts").isEqualTo((int) successionsDesMembres
                    .stream().map(ligne -> ligne.get("A")).distinct().count());

            // Gaps identifies : 09!M des successions des membres dont un sous-score N:R est sous 100.
            Map<String, Integer> gapsAttendus = new TreeMap<>();
            for (Map<String, String> ligne : successionsDesMembres) {
                boolean ecart = List.of("N", "O", "P", "Q", "R").stream()
                        .anyMatch(colonne -> decimal(ligne.get(colonne)).compareTo(new BigDecimal("100")) < 0);
                if (ecart) {
                    gapsAttendus.merge(normaliser(ligne.get("M")), 1, Integer::sum);
                }
            }
            Map<String, Integer> gapsObtenus = new TreeMap<>();
            synthese.gapsIdentifies().forEach(gap -> gapsObtenus.put(normaliser(gap.competence()), gap.nombre()));
            assertThat(gapsObtenus).as(nom + " gaps identifies").isEqualTo(gapsAttendus);
            assertThat(synthese.gapsIdentifies()).as(nom + " du plus frequent au moins frequent")
                    .isSortedAccordingTo(Comparator.comparing(SyntheseVivier.GapFrequent::nombre).reversed());
        }

        // Valeurs relevees dans le classeur, pour lire le test sans l'ouvrir.
        SyntheseVivier expertise = syntheses.get(2);
        assertThat(expertise.effectif()).isEqualTo(17);
        assertThat(expertise.gapsIdentifies().get(0).competence()).isEqualTo("Juridique bancaire");
        assertThat(expertise.gapsIdentifies().get(0).nombre()).isEqualTo(3);
        SyntheseVivier releve = syntheses.get(5);
        assertThat(releve.effectif()).isEqualTo(21);
        assertThat(releve.nbTalents()).isEqualTo(10);
        assertThat(releve.nbReadyNow()).isEqualTo(11);
    }

    @Test
    void le_tableau_de_bord_garde_les_chiffres_des_viviers_thematiques() {
        // Les listes de viviers du tableau de bord ont les membres de l'ecran Viviers.
        var lignes = tableauDeBordInteractif.construire(t3, dashboardService.construire(t3), java.util.Map.of())
                .viviers().stream().filter(v -> !"RELEVE".equals(v.code())).toList();
        List<SyntheseVivier> thematiques = vivierSyntheseService.synthese(t3).stream()
                .filter(synthese -> !synthese.releve()).toList();

        assertThat(lignes).hasSize(5);
        for (int i = 0; i < lignes.size(); i++) {
            SyntheseVivier synthese = thematiques.get(i);
            assertThat(lignes.get(i).code()).isEqualTo(synthese.code());
            assertThat(lignes.get(i).libelle()).isEqualTo(synthese.libelle());
            assertThat(lignes.get(i).membres()).hasSize(synthese.effectif());
            assertThat(lignes.get(i).membres().stream().filter(m -> m.estTalent()).count())
                    .isEqualTo(synthese.nbTalents());
        }
        // Valeurs d'avant le deplacement dans VivierSyntheseService (Commercial : 47 membres, 6 talents).
        SyntheseVivier commercial = thematiques.get(0);
        assertThat(commercial.code()).isEqualTo("COMMERCIAL");
        assertThat(commercial.effectif()).isEqualTo(47);
        assertThat(commercial.performanceMoyenne()).isEqualByComparingTo("79.12");
        assertThat(commercial.potentielMoyen()).isEqualByComparingTo("76.68");
        assertThat(commercial.nbTalents()).isEqualTo(6);
        assertThat(commercial.nbReadyNow()).isEqualTo(3);
        assertThat(commercial.nbPostesCouverts()).isEqualTo(3);
    }

    // --- 4. poste cible -----------------------------------------------------------

    /**
     * Le classeur ne calcule pas de poste cible : les valeurs attendues ont ete
     * obtenues en appliquant la formule de 09_SUCCESSION (K, L, M) a chacun des
     * 15 postes critiques de 07_POSTES pour ces trois collaborateurs. BP001 et
     * BP003 font jeu egal sur PST08 et PST17 : le plus petit Poste_ID l'emporte.
     * BP019 fait 90.23 sur PST01, PST02 et PST03 : PST01, ou il est successeur
     * identifie (09_SUCCESSION!K = 90.23).
     */
    @Test
    void le_poste_cible_de_BP001_BP003_et_BP019() {
        Map<String, ResultatPosteCible> cibles = posteCibleService.postesCibles(t3).stream()
                .collect(Collectors.toMap(cible -> cible.collaborateur().getIdCollaborateur(), Function.identity()));
        assertThat(cibles).hasSize(100);

        verifierCible(cibles.get("BP001"), "PST08", "64.82", NiveauReadiness.PLUS_2_ANS, false, "Conformite", 2);
        verifierCible(cibles.get("BP003"), "PST08", "67.38", NiveauReadiness.ENTRE_1_ET_2_ANS, false,
                "Conformite", 1);
        verifierCible(cibles.get("BP019"), "PST01", "90.23", NiveauReadiness.READY_NOW, true, "Leadership", 0);
        Map<String, String> bp019Pst01 = successions.stream()
                .filter(ligne -> "PST01".equals(ligne.get("A")) && "BP019".equals(ligne.get("B")))
                .findFirst().orElseThrow();
        assertThat(cibles.get("BP019").matching().scoreMatching()).isEqualByComparingTo(decimal(bp019Pst01.get("K")));

        // La fiche porte le meme poste cible.
        FicheCollaborateur.PosteCible fiche001 = ficheCollaborateurViewService.construire("BP001", 2026, 3)
                .posteCible();
        assertThat(fiche001).isEqualTo(new FicheCollaborateur.PosteCible("PST08",
                cibles.get("BP001").poste().getNomPoste(), cibles.get("BP001").poste().getDirection(),
                cibles.get("BP001").poste().getCriticite(), new BigDecimal("64.82"), "PLUS_2_ANS",
                NiveauReadiness.PLUS_2_ANS.getLibelle(), false, "Conformite", 2));
        FicheCollaborateur fiche019 = ficheCollaborateurViewService.construire("BP019", 2026, 3);
        assertThat(fiche019.posteCible().posteId()).isEqualTo("PST01");
        assertThat(fiche019.posteCible().successeurIdentifie()).isTrue();
        assertThat(fiche019.posteCible().gapCompetence()).isNull();
        assertThat(fiche019.posteCible().gapNiveaux()).isZero();
        assertThat(fiche019.successions()).extracting(FicheCollaborateur.Succession::posteId)
                .containsExactly("PST01", "PST02");
    }

    private static void verifierCible(ResultatPosteCible cible, String posteId, String matching,
                                      NiveauReadiness readiness, boolean identifie, String gap, int ecart) {
        assertThat(cible.poste().getPosteId()).isEqualTo(posteId);
        assertThat(cible.matching().scoreMatching()).isEqualByComparingTo(matching);
        assertThat(cible.matching().readiness()).isEqualTo(readiness);
        assertThat(cible.successeurIdentifie()).isEqualTo(identifie);
        assertThat(normaliser(cible.matching().plusGrandGap().competence())).isEqualTo(normaliser(gap));
        assertThat(cible.matching().plusGrandGap().ecart()).isEqualTo(ecart);
    }

    // --- outils ---------------------------------------------------------------------

    private ResultatMatching successeur(String posteId, String id) {
        return couvertures.get(posteId).successeurs().stream()
                .filter(s -> s.candidat().getIdCollaborateur().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError("Successeur " + id + " absent de " + posteId));
    }

    private PosteCritiqueRow ligneEcran(String nomPoste) {
        return posteCritiqueViewService.buildRows(t3).stream()
                .filter(ligne -> ligne.getNomPoste().equals(nomPoste))
                .findFirst().orElseThrow();
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

    private static int compter(Set<String> ids, Predicate<String> condition) {
        return (int) ids.stream().filter(condition).count();
    }

    /** Moyenne des scores du classeur arrondis au centieme (comme le moteur), au centieme. */
    private static BigDecimal moyenne(Set<String> ids, Function<String, BigDecimal> valeur) {
        return ids.stream().map(valeur).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ids.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String valeur) {
        return new BigDecimal(valeur).setScale(2, RoundingMode.HALF_UP);
    }

    private static String libelleClasseur(NiveauReadiness readiness) {
        return switch (readiness) {
            case READY_NOW -> "Ready Now";
            case MOINS_1_AN -> "Ready < 1 an";
            case ENTRE_1_ET_2_ANS -> "Ready 1-2 ans";
            case PLUS_2_ANS -> "Ready > 2 ans";
        };
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
