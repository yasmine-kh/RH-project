package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Decisions du Comite Talent, telles que le RH les a saisies (10_TALENTS,
 * colonne Validation Comite Talent).
 *
 * <p>Une saisie, pas un calcul : le moteur propose les talents, le comite en
 * valide certains. Seul un talent propose ET valide est un talent valide.
 *
 * <p>Contrat cote moteur, sans dependance a la couche de persistance : le
 * depot de la future entite ValidationComite l'implemente ou s'y adapte.
 * Tant qu'aucune implementation n'est declaree, {@link ValidationComiteService}
 * considere toutes les decisions en attente.
 */
public interface ValidationComiteSource {

    /**
     * Decision du comite pour le collaborateur sur le trimestre, jamais null :
     * {@link StatutValidationComite#EN_ATTENTE} tant que rien n'est saisi.
     */
    StatutValidationComite statut(String idCollaborateur, Trimestre trimestre);

    /**
     * Decisions d'un lot de collaborateurs sur le trimestre, par Employee_ID,
     * jamais null ; un matricule absent de la map n'a pas de decision saisie.
     * Par defaut, une lecture par matricule ; une implementation en base peut
     * tout lire en une requete.
     */
    default Map<String, StatutValidationComite> statuts(Collection<String> idsCollaborateurs, Trimestre trimestre) {
        Map<String, StatutValidationComite> statuts = new HashMap<>();
        for (String id : idsCollaborateurs) {
            StatutValidationComite statut = statut(id, trimestre);
            if (statut != null) {
                statuts.put(id, statut);
            }
        }
        return statuts;
    }
}
