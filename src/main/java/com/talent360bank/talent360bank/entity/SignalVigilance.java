package com.talent360bank.talent360bank.entity;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Les sept signaux de risque de depart. Chacun sait ou lire ses points dans
 * {@link PointsVigilance} : l'indice de vigilance est la somme des points des
 * signaux declenches, aucun bareme n'est ecrit dans le service.
 *
 * <p>{@link #estDetectable()} distingue les signaux que le modele de donnees
 * sait aujourd'hui reconnaitre de ceux qui attendent leur source : ces
 * derniers restent dans le bareme et peuvent etre fournis a la main, mais
 * aucune detection automatique ne les leve.
 */
public enum SignalVigilance {

    ENGAGEMENT_FAIBLE("Engagement faible",
            PointsVigilance::getPointEngagementFaible, true),

    SANS_MOBILITE_4_ANS("Aucun mouvement depuis 4 ans",
            PointsVigilance::getPointSansMobilite4Ans, false),

    MOBILITE_NON_TRAITEE("Souhait de mobilite non traite",
            PointsVigilance::getPointMobiliteNonTraitee, false),

    SANS_DEVELOPPEMENT_RECENT("Aucune action de developpement recente",
            PointsVigilance::getPointSansDeveloppementRecent, false),

    BAISSE_PERFORMANCE("Baisse de performance",
            PointsVigilance::getPointBaissePerformance, true),

    FAIBLE_RECONNAISSANCE("Faible reconnaissance",
            PointsVigilance::getPointFaibleReconnaissance, false),

    FORMATION_NON_FAITE("Formation prevue non realisee",
            PointsVigilance::getPointFormationNonFaite, false);

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
     * Vrai si le modele de donnees permet de lever ce signal automatiquement.
     * Faux tant que la source n'existe pas : mobilite, plan de developpement,
     * reconnaissance et formations ne sont pas encore modelises.
     */
    public boolean estDetectable() {
        return detectable;
    }

    public String getLibelle() {
        return libelle;
    }
}
