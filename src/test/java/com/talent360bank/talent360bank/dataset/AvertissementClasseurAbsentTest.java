package com.talent360bank.talent360bank.dataset;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestIdentifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Sans le classeur du client, les classes de comparaison ignorees sont listees, pas passees sous silence. */
class AvertissementClasseurAbsentTest {

    private static TestIdentifier classe(Class<?> classe) {
        TestSource source = ClassSource.from(classe);
        return TestIdentifier.from(new AbstractTestDescriptor(UniqueId.forEngine("junit-jupiter")
                .append("class", classe.getName()), classe.getSimpleName(), source) {
            @Override
            public Type getType() {
                return Type.CONTAINER;
            }
        });
    }

    private static String sortie(boolean classeurPresent) {
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        AvertissementClasseurAbsent avertissement = new AvertissementClasseurAbsent(
                new PrintStream(octets, true, StandardCharsets.UTF_8), classeurPresent);
        avertissement.executionSkipped(classe(FicheCollaborateurDatasetTest.class), "classeurPresent() est faux");
        avertissement.executionFinished(classe(DatasetExcelComparaisonTest.class), TestExecutionResult.aborted(null));
        // Une classe hors du paquet dataset n'est pas un test du classeur.
        avertissement.executionSkipped(classe(String.class), "autre raison");
        avertissement.testPlanExecutionFinished(null);
        return octets.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sans_le_classeur_les_classes_ignorees_sont_listees() {
        assertThat(sortie(false)).contains("ATTENTION : classeur du client absent",
                        "2 classe(s) de comparaison avec le classeur n'ont PAS ete executees",
                        "   - DatasetExcelComparaisonTest", "   - FicheCollaborateurDatasetTest",
                        "docs/guide-developpeur.md")
                .doesNotContain("String");
    }

    @Test
    void avec_le_classeur_rien_n_est_affiche() {
        assertThat(sortie(true)).isEmpty();
    }
}
