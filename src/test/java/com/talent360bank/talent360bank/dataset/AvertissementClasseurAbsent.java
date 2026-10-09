package com.talent360bank.talent360bank.dataset;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Les tests de comparaison avec le classeur du client (docs/data/TALENT_360_BANK_Dataset_V1.xlsx, non
 * versionne) sont ignores quand le fichier manque (@EnabledIf("classeurPresent"), ou assumeTrue). Ignores en
 * silence, ils laisseraient croire a une verification complete : ce listener les liste en fin d'execution,
 * avec un avertissement visible dans la console Maven (et la CI).
 *
 * <p>Enregistre par src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener.
 */
public class AvertissementClasseurAbsent implements TestExecutionListener {

    static final Path CLASSEUR = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    private final TreeSet<String> ignorees = new TreeSet<>();
    private final PrintStream sortie;
    private final boolean classeurPresent;

    public AvertissementClasseurAbsent() {
        this(System.out, Files.exists(CLASSEUR));
    }

    AvertissementClasseurAbsent(PrintStream sortie, boolean classeurPresent) {
        this.sortie = sortie;
        this.classeurPresent = classeurPresent;
    }

    @Override
    public void executionSkipped(TestIdentifier test, String raison) {
        if (!classeurPresent) {
            nomDeClasse(test).filter(AvertissementClasseurAbsent::estTestClasseur).ifPresent(ignorees::add);
        }
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult resultat) {
        // assumeTrue(classeur present) : le test est "aborted", pas "skipped".
        if (!classeurPresent && resultat.getStatus() == TestExecutionResult.Status.ABORTED) {
            nomDeClasse(test).filter(AvertissementClasseurAbsent::estTestClasseur).ifPresent(ignorees::add);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        message().forEach(sortie::println);
    }

    /** Les lignes de l'avertissement ; vide si rien n'a ete ignore. */
    List<String> message() {
        List<String> lignes = new ArrayList<>();
        if (ignorees.isEmpty()) {
            return lignes;
        }
        String cadre = "!".repeat(100);
        lignes.add(cadre);
        lignes.add("ATTENTION : classeur du client absent (" + CLASSEUR + ").");
        lignes.add(ignorees.size() + " classe(s) de comparaison avec le classeur n'ont PAS ete executees :");
        ignorees.forEach(nom -> lignes.add("   - " + nom));
        lignes.add("Les calculs n'ont donc pas ete compares au classeur. Deposer le fichier puis relancer les tests");
        lignes.add("(voir docs/guide-developpeur.md, section Tests).");
        lignes.add(cadre);
        return lignes;
    }

    private static Optional<String> nomDeClasse(TestIdentifier test) {
        Object source = test.getSource().orElse(null);
        if (source instanceof ClassSource classe) {
            return Optional.of(classe.getJavaClass().getSimpleName());
        }
        if (source instanceof org.junit.platform.engine.support.descriptor.MethodSource methode) {
            return Optional.of(methode.getJavaClass().getSimpleName());
        }
        return Optional.empty();
    }

    /** Les tests qui lisent le classeur du client : ceux du paquet dataset. */
    private static boolean estTestClasseur(String nomSimple) {
        try {
            Class<?> classe = Class.forName(AvertissementClasseurAbsent.class.getPackageName() + "." + nomSimple);
            return classe != AvertissementClasseurAbsent.class;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
