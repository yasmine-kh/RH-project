package com.talent360bank.talent360bank.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.talent360bank.talent360bank.service.SourcesEnBaseContexteTest.source;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Profil demo : la source du classeur local (dev/DemoClasseurSources) et les
 * sources en base (@Fallback) sont toutes deux declarees ; chaque service doit
 * recevoir la premiere. C'est ce que ObjectProvider.getIfAvailable doit
 * trancher, et ce que ce test verifie sur le vrai contexte.
 *
 * <p>Le dossier dev/ et le jeu de donnees ne sont pas versionnes : sans eux
 * (CI, poste d'un collegue), le test est ignore. La classe de demo est
 * designee par son nom pour que ce fichier compile sans elle.
 *
 * <p>Base en memoire a part : le chargeur de demo y importe tout le classeur,
 * sans toucher la base partagee par les autres tests.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sources-demo;DB_CLOSE_DELAY=-1;MODE=MySQL")
@ActiveProfiles("demo")
@EnabledIf("demoDisponible")
class SourcesDemoContexteTest {

    private static final String SOURCE_DEMO = "com.talent360bank.talent360bank.dev.DemoClasseurSources";
    private static final Path CLASSEUR = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    @Autowired
    private ApplicationContext contexte;

    static boolean demoDisponible() {
        try {
            Class.forName(SOURCE_DEMO);
        } catch (ClassNotFoundException e) {
            return false;
        }
        return Files.exists(CLASSEUR);
    }

    @Test
    void les_services_recoivent_la_source_de_demo_et_non_celles_en_base() {
        assertThat(source(contexte, PosteCritiqueService.class, "successeurIdentifieSource"))
                .extracting(objet -> objet.getClass().getName()).isEqualTo(SOURCE_DEMO);
        assertThat(source(contexte, ValidationComiteService.class, "validationComiteSource"))
                .extracting(objet -> objet.getClass().getName()).isEqualTo(SOURCE_DEMO);
        assertThat(source(contexte, VivierThematiqueService.class, "vivierThematiqueSource"))
                .extracting(objet -> objet.getClass().getName()).isEqualTo(SOURCE_DEMO);
        assertThat(source(contexte, VigilanceService.class, "faitsVigilanceSource"))
                .extracting(objet -> objet.getClass().getName()).isEqualTo(SOURCE_DEMO);
    }

    @Test
    void les_deux_implementations_coexistent_sans_conflit() {
        // Deux candidates par contrat : sans @Fallback, getIfAvailable leverait
        // NoUniqueBeanDefinitionException et le contexte ne demarrerait pas.
        assertThat(contexte.getBeansOfType(SuccesseurIdentifieSource.class)).hasSize(2);
        assertThat(contexte.getBeansOfType(ValidationComiteSource.class)).hasSize(2);
        assertThat(contexte.getBeansOfType(VivierThematiqueSource.class)).hasSize(2);
        assertThat(contexte.getBeansOfType(FaitsVigilanceSource.class)).hasSize(2);
    }
}
