package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.TrimestreService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nombre de requetes du recalcul des scores sur le vrai classeur (100
 * collaborateurs), audit initial 3.4 : avant, une recherche du score existant
 * et le chargement EAGER des entites par collaborateur (285 requetes au premier
 * passage, 185 au second). Maintenant cinq lectures fixes (reglages, notes de
 * performance, de potentiel, scores existants, suppression groupee), plus une
 * insertion par nouveau score ; un second passage sans changement n'ecrit rien.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:requetes-moteur-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("classeurPresent")
class RequetesMoteurDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreService trimestreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ScoreService scoreService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Trimestre t3;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        trimestreService.modifierDateReference(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow(),
                LocalDate.of(2026, 9, 15));
        t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
    }

    @Test
    void le_recalcul_du_classeur_lit_la_base_en_cinq_requetes() {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistiques.clear();
        ResultatRecalcul premier = scoreService.recalculerTrimestre(t3);
        assertThat(premier.scoresEnregistres()).hasSize(100);
        assertThat(statistiques.getEntityInsertCount()).isEqualTo(100);
        assertThat(statistiques.getPrepareStatementCount()).isEqualTo(5 + 100);

        statistiques.clear();
        assertThat(scoreService.recalculerTrimestre(t3).scoresEnregistres()).hasSize(100);
        assertThat(statistiques.getPrepareStatementCount()).isEqualTo(5);
    }
}
