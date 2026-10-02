package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.AlertesView;
import com.talent360bank.talent360bank.ui.model.Notifications;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne;
import com.talent360bank.talent360bank.ui.model.TypeAlerte;
import com.talent360bank.talent360bank.ui.service.AlertesViewService;
import com.talent360bank.talent360bank.ui.service.NotificationsViewService;
import com.talent360bank.talent360bank.ui.service.SuiviCampagneViewService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nouvelles regles d'alerte, notifications et avancement de campagne sur le vrai
 * classeur (T3 2026), puis un scenario a deux trimestres pour les nouveaux talents.
 * Attendus lus dans 01_COLLABORATEURS, 02, 03, 08_POSTES_CRITIQUES et 10_TALENTS.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:alertes-notifs-campagne;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("classeurPresent")
class AlertesNotificationsCampagneDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreService trimestreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ScoreRepository scoreRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private AlertesViewService alertesViewService;
    @Autowired
    private NotificationsViewService notificationsViewService;
    @Autowired
    private SuiviCampagneViewService campagneService;
    @Autowired
    private PlatformTransactionManager transactionManager;

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

    private Map<String, Integer> parType(AlertesView vue) {
        return vue.parType().stream().collect(Collectors.toMap(AlertesView.Compteur::code, AlertesView.Compteur::nombre));
    }

    // --- 1. nouvelles regles, un seul trimestre -------------------------------------------

    @Test
    @Order(1)
    void un_seul_successeur_suit_08_et_aucun_nouveau_talent_sans_trimestre_precedent() {
        AlertesView vue = alertesViewService.construire(t3, AlertesView.Filtres.AUCUN);
        int unSeulAttendu = (int) classeur.feuille("08_POSTES_CRITIQUES").values().stream()
                .filter(l -> l.getOrDefault("A", "").matches("PST\\d+"))
                .filter(l -> (int) Double.parseDouble(l.get("G")) == 1)
                .count();
        assertThat(parType(vue).get("UN_SEUL_SUCCESSEUR")).isEqualTo(unSeulAttendu).isZero();
        assertThat(parType(vue).get("NOUVEAU_TALENT")).isZero();
        assertThat(vue.informations()).anyMatch(i -> i.contains("aucun trimestre précédent"));
        // Les alertes d'avant sont inchangees (AlertesDatasetTest) : 23 au total.
        assertThat(vue.total()).isEqualTo(23);
    }

    // --- 2. notifications ---------------------------------------------------------------------

    @Test
    @Order(2)
    void les_notifications_reprennent_les_compteurs_de_l_ecran_alertes() {
        AlertesView vue = alertesViewService.construire(t3, AlertesView.Filtres.AUCUN);
        Notifications notifications = notificationsViewService.notifications();

        assertThat(notifications.trimestreLibelle()).isEqualTo("T3 2026");
        assertThat(notifications.total()).isEqualTo(vue.total());
        assertThat(notifications.parSeverite()).isEqualTo(vue.parSeverite());
        assertThat(notifications.parType()).isEqualTo(vue.parType());
        assertThat(notifications.alertes()).isEqualTo(vue.alertes().subList(0, Notifications.NB_ALERTES));
        assertThat(notifications.alertes()).allSatisfy(a -> assertThat(a.lien()).isNotBlank());
        assertThat(notifications.lienAlertes()).isEqualTo("/alertes?trimestre=2026-3");

        Notifications.Badge badge = notificationsViewService.badge();
        assertThat(badge.total()).isEqualTo(vue.total());
        assertThat(badge.critiques() + badge.elevees() + badge.moyennes()).isEqualTo(vue.total());
        assertThat(badge.critiques()).isEqualTo(compte(vue, SeveriteAlerte.CRITIQUE)).isEqualTo(1);
    }

    private static int compte(AlertesView vue, SeveriteAlerte severite) {
        return vue.parSeverite().stream().filter(c -> c.code().equals(severite.name()))
                .mapToInt(AlertesView.Compteur::nombre).sum();
    }

    // --- 3. avancement de campagne -------------------------------------------------------------

    @Test
    @Order(3)
    void l_avancement_par_direction_suit_01_02_et_03() {
        Map<String, Integer> actifsParDirection = new HashMap<>();
        Map<String, String> directions = new HashMap<>();
        for (Map<String, String> l : classeur.feuille("01_COLLABORATEURS").values()) {
            if (l.getOrDefault("A", "").matches("BP\\d+") && "Actif".equalsIgnoreCase(l.get("O"))) {
                actifsParDirection.merge(l.get("H"), 1, Integer::sum);
                directions.put(l.get("A"), l.get("H"));
            }
        }
        // Evalues = presents dans 02_PERFORMANCE ET 03_POTENTIEL (evaluations du manager).
        Map<String, Integer> evaluesParDirection = new HashMap<>();
        java.util.Set<String> potentiels = classeur.feuille("03_POTENTIEL").values().stream()
                .map(l -> l.getOrDefault("A", "")).filter(id -> id.matches("BP\\d+")).collect(Collectors.toSet());
        classeur.feuille("02_PERFORMANCE").values().stream().map(l -> l.getOrDefault("A", ""))
                .filter(id -> id.matches("BP\\d+") && potentiels.contains(id) && directions.containsKey(id))
                .forEach(id -> evaluesParDirection.merge(directions.get(id), 1, Integer::sum));

        SuiviCampagne suivi = campagneService.construire(2026, 3, null);
        assertThat(suivi.lignes()).extracting(SuiviCampagne.Ligne::libelle)
                .containsExactlyInAnyOrderElementsOf(actifsParDirection.keySet());
        for (SuiviCampagne.Ligne ligne : suivi.lignes()) {
            assertThat(ligne.nbActifs()).as(ligne.libelle()).isEqualTo(actifsParDirection.get(ligne.libelle()));
            assertThat(ligne.nbEvalues()).as(ligne.libelle()).isEqualTo(evaluesParDirection.get(ligne.libelle()));
            assertThat(ligne.nbManquants()).isEqualTo(ligne.nbActifs() - ligne.nbEvalues());
            assertThat(ligne.type()).isEqualTo("DIRECTION");
        }
        assertThat(suivi.total().nbActifs()).isEqualTo(100);
        assertThat(suivi.total().nbEvalues()).isEqualTo(100);
        assertThat(suivi.total().pourcentageEvalues()).isEqualByComparingTo("100.0");
        assertThat(suivi.total().managers()).isEmpty();
        // Coherent avec l'alerte "Evaluation du manager manquante" (0 dans le classeur).
        assertThat(parType(alertesViewService.construire(t3, AlertesView.Filtres.AUCUN))
                .get("EVALUATION_MANAGER_MANQUANTE")).isEqualTo(suivi.total().nbManquants());
        // Aucune auto-evaluation dans le classeur : dit, pas invente.
        assertThat(suivi.total().nbAutoEvaluations()).isZero();
        assertThat(suivi.remarques()).anyMatch(r -> r.contains("auto-évaluation"));

        // Une direction detaillee : ses entites filles couvrent tout son effectif.
        String code = Entite.code(null, TypeEntite.DIRECTION, "Reseau Retail");
        SuiviCampagne retail = campagneService.construire(2026, 3, code);
        assertThat(retail.entite().libelle()).isEqualTo("Reseau Retail");
        assertThat(retail.total().nbActifs()).isEqualTo(actifsParDirection.get("Reseau Retail"));
        assertThat(retail.lignes()).isNotEmpty();
        assertThat(retail.lignes().stream().mapToInt(SuiviCampagne.Ligne::nbActifs).sum())
                .isEqualTo(retail.total().nbActifs());
    }

    // --- 4. nouveaux talents sur deux trimestres -------------------------------------------------

    /**
     * T2 2026 reprend les scores de T3 sauf BP019, dont la performance de T2 est
     * sous le seuil talent (70) : seul BP019 est un nouveau talent en T3. Les
     * talents de T2 sont ceux de T3 moins BP019, donc aucun autre.
     */
    @Test
    @Order(4)
    void un_talent_qui_ne_l_etait_pas_au_trimestre_precedent_est_un_nouveau_talent() {
        Trimestre t2 = trimestreService.creerSiAbsent(2026, 2).trimestre();
        new TransactionTemplate(transactionManager).executeWithoutResult(statut -> {
            for (Score courant : scoreRepository.findByTrimestreAvecCollaborateur(t3)) {
                Score ancien = new Score();
                ancien.setCollaborateur(courant.getCollaborateur());
                ancien.setTrimestre(t2);
                ancien.setScorePerformance("BP019".equals(courant.getCollaborateur().getIdCollaborateur())
                        ? new BigDecimal("70.00") : courant.getScorePerformance());
                ancien.setScorePotentiel(courant.getScorePotentiel());
                ancien.figerOrganisation();
                scoreRepository.save(ancien);
            }
        });

        AlertesView vue = alertesViewService.construire(t3, AlertesView.Filtres.AUCUN);
        List<AlerteVue> nouveaux = vue.alertes().stream().filter(a -> a.type() == TypeAlerte.NOUVEAU_TALENT).toList();
        assertThat(nouveaux).extracting(AlerteVue::matricule).containsExactly("BP019");
        assertThat(nouveaux.get(0).severite()).isEqualTo(AlertesViewService.SEVERITE_NOUVEAU_TALENT);
        assertThat(nouveaux.get(0).message()).contains("pas en T2 2026");
        assertThat(nouveaux.get(0).lien()).contains("/collaborateurs/BP019/fiche");
        assertThat(vue.informations()).anyMatch(i -> i.contains("comparés à T2 2026"));
        // Le total n'est pas compare a 23 + 1 : avec un trimestre precedent, la "baisse de
        // performance" de la vigilance vient de l'historique des scores et non plus du drapeau importe.

        // T2, le plus ancien, n'a pas de trimestre precedent : aucun nouveau talent.
        AlertesView vueT2 = alertesViewService.construire(t2, AlertesView.Filtres.AUCUN);
        assertThat(parType(vueT2).get("NOUVEAU_TALENT")).isZero();
        assertThat(vueT2.informations()).anyMatch(i -> i.contains("aucun trimestre précédent"));

        // Les notifications suivent (dernier trimestre avec scores : T3).
        assertThat(notificationsViewService.notifications().total()).isEqualTo(vue.total());
    }
}
