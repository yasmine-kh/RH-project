package com.talent360bank.talent360bank.service.resultat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chiffres d'un vivier sur un trimestre : un vivier thematique (10_TALENTS!I)
 * ou le vivier de releve (10_TALENTS!J).
 *
 * @param code               COMMERCIAL, DIGITAL, EXPERTISE, MANAGEMENT, RISQUES ou RELEVE
 * @param releve             vrai pour le vivier de releve (talents OU hauts potentiels, toutes
 *                           directions confondues : ses membres sont aussi dans un vivier thematique)
 * @param effectif           membres du vivier
 * @param nbTalents          membres talents proposes (10_TALENTS!E)
 * @param nbHautsPotentiels  membres hauts potentiels proposes (10_TALENTS!F)
 * @param nbReadyNow         membres Ready Now sur au moins un poste critique ou ils sont
 *                           successeurs identifies (un membre pret sur deux postes compte une fois)
 * @param performanceMoyenne moyenne des scores de performance des membres, 2 decimales ; null sans membre
 * @param potentielMoyen     idem pour le potentiel
 * @param nbPostesCouverts   postes critiques dont au moins un successeur identifie est membre
 * @param gapsIdentifies     plus grands gaps (09_SUCCESSION!M) des successions des membres, par
 *                           competence, du plus frequent au moins frequent ; seules les successions
 *                           avec un ecart reel comptent (une par couple poste / successeur)
 */
public record SyntheseVivier(String code, String libelle, boolean releve, int effectif, int nbTalents,
                             int nbHautsPotentiels, int nbReadyNow, BigDecimal performanceMoyenne,
                             BigDecimal potentielMoyen, int nbPostesCouverts, List<GapFrequent> gapsIdentifies) {

    /** Une competence et le nombre de successions dont elle est le plus grand gap. */
    public record GapFrequent(String competenceId, String competence, int nombre) {
    }
}
