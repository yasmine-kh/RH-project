package com.talent360bank.talent360bank.controller;

import java.util.List;

/**
 * Les douze blocs sont valides pris un par un, mais leur combinaison ne l'est
 * pas : un seuil de vigilance plus haut que le total des points attribuables,
 * par exemple. Ces regles vivent sur Parametre et ne peuvent etre verifiees
 * qu'une fois les blocs reunis.
 */
public class ParametreInvalideException extends RuntimeException {

    private final transient List<String> violations;

    public ParametreInvalideException(List<String> violations) {
        super("Les reglages sont invalides");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
