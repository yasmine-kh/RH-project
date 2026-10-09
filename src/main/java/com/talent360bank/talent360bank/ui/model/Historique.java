package com.talent360bank.talent360bank.ui.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * L'ecran Historique : evenements filtres (journal_evenement), le plus recent d'abord, par page.
 *
 * @param filtres  filtres appliques (valeurs de l'URL)
 * @param types    choix du filtre de type (valeur vide = tous)
 * @param erreur   filtre illisible (date mal ecrite), sinon null
 */
public record Historique(Filtres filtres, List<OptionFiltre> types, List<OptionFiltre> trimestres,
                         List<Ligne> lignes, long total, int page, int nbPages, String erreur) {

    /** Filtres de l'URL ; null = pas de filtre. */
    public record Filtres(String type, String trimestre, LocalDate du, LocalDate au, String q) {
        public boolean actif() {
            return type != null || trimestre != null || du != null || au != null || q != null;
        }
    }

    /**
     * @param auteur login du compte RH, null si inconnu
     * @param lien   page concernee, null sans page
     */
    public record Ligne(LocalDateTime date, String type, String typeLibelle, String trimestre, String description,
                        String auteur, String matricule, String lien) {
    }
}
