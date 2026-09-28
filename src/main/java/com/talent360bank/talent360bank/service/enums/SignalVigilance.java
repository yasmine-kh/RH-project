package com.talent360bank.talent360bank.service.enums;

import com.talent360bank.talent360bank.entity.PointsVigilance;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Les sept signaux de risque de depart. Chacun sait ou lire ses points dans
 * {@link PointsVigilance} : l'indice de vigilance est la somme des points des
 * signaux declenches, aucun bareme n'est ecrit dans le service.
 *
 * <p>{@link #estDetectable()} dit si le moteur sait lever le signal a partir
 * des donnees : l'engagement et la baisse de performance par le calcul, les
 * cinq autres par les faits importes (FaitsVigilanceSource).
 */
public enum SignalVigilance {

    ENGAGEMENT_FAIBLE("Engagement faible",
            PointsVigilance::getPointEngagementFaible, true),

    SANS_MOBILITE_4_ANS("Aucun mouvement depuis 4 ans",
            PointsVigilance::getPointSansMobilite4Ans, true),

    MOBILITE_NON_TRAITEE("Souhait de mobilite non traite",
            PointsVigilance::getPointMobiliteNonTraitee, true),

    SANS_DEVELOPPEMENT_RECENT("Aucune action de developpement recente",
            PointsVigilance::getPointSansDeveloppementRecent, true),

    BAISSE_PERFORMANCE("Baisse de performance",
            PointsVigilance::getPointBaissePerformance, true),

    FAIBLE_RECONNAISSANCE("Faible reconnaissance",
            PointsVigilance::getPointFaibleReconnaissance, true),

    FORMATION_NON_FAITE("Formation prevue non realisee",
            PointsVigilance::getPointFormationNonFaite, true);

    private final String libelle;
    private final Function<PointsVigilance, BigDecimal> lecteurPoints;
    private final boolean detectable;

    SignalVigilance(String libelle, Function<PointsVigilance, BigDecimal> lecteurPoints,
                    boolean detectable) {
        this.libelle = libelle;
        this.lecteurPoints = lecteurPoints;
        this.detectable = detectable;
    }

    /** Points que ce signal ajoute a l'indice, d'apres les reglages du trimestre. */
    public BigDecimal pointsDans(PointsVigilance points) {
        return lecteurPoints.apply(points);
    }

    /**
     * Vrai si le moteur peut lever ce signal a partir des donnees, sans saisie
     * a la main. Vrai pour les sept depuis l'ajout des faits importes ; garde
     * pour le contrat de l'API et pour un futur signal sans source.
     */
    public boolean estDetectable() {
        return detectable;
    }

    public String getLibelle() {
        return libelle;
    }
}
