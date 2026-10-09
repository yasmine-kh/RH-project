package com.talent360bank.talent360bank.ui.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Section "Historique" du Talent Passport : les scores du collaborateur sur chaque trimestre calcule
 * (du plus recent au plus ancien) et les evenements du journal qui le concernent.
 *
 * @param trimestres  un par trimestre ou le collaborateur a un score enregistre
 * @param evenements  decisions du Comite et changements le concernant (journal_evenement)
 */
public record HistoriqueCollaborateur(List<Trimestre> trimestres, List<Historique.Ligne> evenements) {

    /** Un seul trimestre calcule : la page le dit plutot que de laisser croire a une evolution. */
    public boolean unSeulTrimestre() {
        return trimestres.size() == 1;
    }

    /**
     * @param caseNumero  case 9-Box (1 a 9), null si non place
     * @param vigilance   code du niveau (FAIBLE, MODEREE, ELEVEE), null si non evaluable ce trimestre-la
     *                    (aucune donnee de vigilance, reglages absents)
     */
    public record Trimestre(String valeur, String libelle, BigDecimal performance, String categoriePerformance,
                            BigDecimal potentiel, String categoriePotentiel, Integer caseNumero, String caseLibelle,
                            String vigilance, String vigilanceLibelle, boolean courant) {
    }
}
