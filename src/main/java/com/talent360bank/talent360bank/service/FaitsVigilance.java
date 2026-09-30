package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.service.enums.SignalVigilance;

/**
 * Ce que l'import sait d'un collaborateur sur un trimestre pour les signaux de
 * vigilance qui ne se deduisent pas des scores (12_VIGILANCE, colonnes F a K).
 *
 * <p>Chaque signal est un drapeau a trois etats : {@code TRUE} leve le signal,
 * {@code FALSE} et {@code null} ne le levent pas. {@code null} veut dire
 * inconnu : sans donnee, on ne suppose pas le risque.
 *
 * <p>Construit par {@link #builder()} et non par un constructeur a six
 * parametres : des faits bruts (date du dernier mouvement, formations prevues
 * et realisees...) pourront s'ajouter au builder, avec des regles et des
 * seuils dans Parametre, sans casser les imports qui ne fournissent que les
 * drapeaux.
 *
 * <p>L'engagement n'est pas ici : il vient du score du QuestionnaireEngagement.
 */
public final class FaitsVigilance {

    /** Aucun fait connu : ne leve aucun signal. */
    public static final FaitsVigilance AUCUN = builder().build();

    private final Boolean sansMobilite4Ans;
    private final Boolean mobiliteNonTraitee;
    private final Boolean sansDeveloppementRecent;
    private final Boolean baissePerformance;
    private final Boolean faibleReconnaissance;
    private final Boolean formationNonFaite;

    private FaitsVigilance(Builder builder) {
        this.sansMobilite4Ans = builder.sansMobilite4Ans;
        this.mobiliteNonTraitee = builder.mobiliteNonTraitee;
        this.sansDeveloppementRecent = builder.sansDeveloppementRecent;
        this.baissePerformance = builder.baissePerformance;
        this.faibleReconnaissance = builder.faibleReconnaissance;
        this.formationNonFaite = builder.formationNonFaite;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Drapeau declare pour le signal, {@code null} si inconnu. Toujours
     * {@code null} pour ENGAGEMENT_FAIBLE, qui ne passe pas par ces faits.
     */
    public Boolean declare(SignalVigilance signal) {
        return switch (signal) {
            case ENGAGEMENT_FAIBLE -> null;
            case SANS_MOBILITE_4_ANS -> sansMobilite4Ans;
            case MOBILITE_NON_TRAITEE -> mobiliteNonTraitee;
            case SANS_DEVELOPPEMENT_RECENT -> sansDeveloppementRecent;
            case BAISSE_PERFORMANCE -> baissePerformance;
            case FAIBLE_RECONNAISSANCE -> faibleReconnaissance;
            case FORMATION_NON_FAITE -> formationNonFaite;
        };
    }

    /** Vrai seulement si le signal est declare a {@code TRUE}. */
    public boolean estDeclare(SignalVigilance signal) {
        return Boolean.TRUE.equals(declare(signal));
    }

    public Boolean getSansMobilite4Ans() {
        return sansMobilite4Ans;
    }

    public Boolean getMobiliteNonTraitee() {
        return mobiliteNonTraitee;
    }

    public Boolean getSansDeveloppementRecent() {
        return sansDeveloppementRecent;
    }

    public Boolean getBaissePerformance() {
        return baissePerformance;
    }

    public Boolean getFaibleReconnaissance() {
        return faibleReconnaissance;
    }

    public Boolean getFormationNonFaite() {
        return formationNonFaite;
    }

    public static final class Builder {

        private Boolean sansMobilite4Ans;
        private Boolean mobiliteNonTraitee;
        private Boolean sansDeveloppementRecent;
        private Boolean baissePerformance;
        private Boolean faibleReconnaissance;
        private Boolean formationNonFaite;

        private Builder() {
        }

        /** 12_VIGILANCE F : aucun mouvement depuis 4 ans. */
        public Builder sansMobilite4Ans(Boolean valeur) {
            this.sansMobilite4Ans = valeur;
            return this;
        }

        /** 12_VIGILANCE G : demande de mobilite non traitee. */
        public Builder mobiliteNonTraitee(Boolean valeur) {
            this.mobiliteNonTraitee = valeur;
            return this;
        }

        /** 12_VIGILANCE H : aucune action de developpement recente. */
        public Builder sansDeveloppementRecent(Boolean valeur) {
            this.sansDeveloppementRecent = valeur;
            return this;
        }

        /**
         * 12_VIGILANCE I : baisse de performance. Ne sert que si l'historique
         * des scores ne permet pas de la mesurer (pas de score au trimestre
         * precedent).
         */
        public Builder baissePerformance(Boolean valeur) {
            this.baissePerformance = valeur;
            return this;
        }

        /** 12_VIGILANCE J : faible reconnaissance. */
        public Builder faibleReconnaissance(Boolean valeur) {
            this.faibleReconnaissance = valeur;
            return this;
        }

        /** 12_VIGILANCE K : formation prevue non realisee. */
        public Builder formationNonFaite(Boolean valeur) {
            this.formationNonFaite = valeur;
            return this;
        }

        /**
         * Positionne le drapeau d'un signal par son code, pour les imports qui
         * lisent les colonnes dans une boucle.
         *
         * @throws IllegalArgumentException pour ENGAGEMENT_FAIBLE, qui ne passe
         *                                  pas par ces faits
         */
        public Builder declarer(SignalVigilance signal, Boolean valeur) {
            switch (signal) {
                case ENGAGEMENT_FAIBLE -> throw new IllegalArgumentException(
                        "L'engagement vient du QuestionnaireEngagement, pas des faits de vigilance");
                case SANS_MOBILITE_4_ANS -> sansMobilite4Ans = valeur;
                case MOBILITE_NON_TRAITEE -> mobiliteNonTraitee = valeur;
                case SANS_DEVELOPPEMENT_RECENT -> sansDeveloppementRecent = valeur;
                case BAISSE_PERFORMANCE -> baissePerformance = valeur;
                case FAIBLE_RECONNAISSANCE -> faibleReconnaissance = valeur;
                case FORMATION_NON_FAITE -> formationNonFaite = valeur;
            }
            return this;
        }

        public FaitsVigilance build() {
            return new FaitsVigilance(this);
        }
    }
}
