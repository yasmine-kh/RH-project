package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Score;

import java.util.List;

/**
 * Bilan d'un recalcul de trimestre. Les employes ecartes sont remontes avec
 * leur motif plutot que passes sous silence : le RH doit pouvoir savoir qui
 * n'a pas ete score et pourquoi.
 */
public record ResultatRecalcul(List<Score> scoresEnregistres, List<EmployeIgnore> ignores) {

    public record EmployeIgnore(String matricule, String motif) {
    }

    public int nombreCalcules() {
        return scoresEnregistres.size();
    }

    public int nombreIgnores() {
        return ignores.size();
    }
}
