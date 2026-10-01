package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.FaitsVigilanceSource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Qui n'a aucune donnee de vigilance sur le trimestre, pour la fiche
 * collaborateur et la vue manager.
 *
 * <p>Regle d'affichage, pas de calcul : le moteur donnerait a ces
 * collaborateurs un indice de 0 (FAIBLE), faute de signal. Ce 0 ne dit rien
 * de leur risque de depart ; les vues affichent donc une vigilance absente
 * (null) et le disent dans donneesManquantes. Des qu'une donnee existe, la
 * vigilance est calculee comme avant.
 *
 * <p>Donnees de vigilance : un questionnaire d'engagement avec un score, une
 * declaration de faits (12_VIGILANCE F a K), ou une evaluation de performance
 * ou de potentiel sur le trimestre.
 */
@Component
public class EntreesVigilance {

    /** Ligne de donneesManquantes. */
    public static final String AUCUNE_DONNEE = "Aucune donnée de vigilance pour ce trimestre";

    private final FaitsVigilanceSource faitsVigilanceSource;

    public EntreesVigilance(FaitsVigilanceSource faitsVigilanceSource) {
        this.faitsVigilanceSource = faitsVigilanceSource;
    }

    /**
     * Matricules sans aucune donnee de vigilance, dans l'ordre donne. Une
     * requete (les declarations du lot), quelle que soit la taille du lot.
     *
     * @param avecQuestionnaire vrai si le matricule a un questionnaire d'engagement avec un score
     * @param evalue            vrai si le matricule a une evaluation de performance ou de potentiel
     */
    Set<String> sansDonnee(Collection<String> matricules, Trimestre trimestre,
                           Predicate<String> avecQuestionnaire, Predicate<String> evalue) {
        Set<String> sansDonnee = new LinkedHashSet<>();
        if (matricules.isEmpty()) {
            return sansDonnee;
        }
        Map<String, ?> declarations = faitsVigilanceSource.faitsDe(matricules, trimestre);
        for (String matricule : matricules) {
            if (!avecQuestionnaire.test(matricule) && !evalue.test(matricule)
                    && (declarations == null || !declarations.containsKey(matricule))) {
                sansDonnee.add(matricule);
            }
        }
        return sansDonnee;
    }
}
