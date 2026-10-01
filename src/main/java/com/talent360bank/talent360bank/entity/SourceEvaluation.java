package com.talent360bank.talent360bank.entity;

/**
 * Qui a rempli une evaluation de performance ou de potentiel : le
 * collaborateur lui-meme (auto-evaluation) ou son manager. Les deux fichiers
 * portent les memes criteres ; un collaborateur a au plus une evaluation de
 * chaque source par trimestre.
 *
 * <p>Le score officiel melange les deux selon {@link PonderationSources} du
 * trimestre (100 % manager par defaut). Les criteres lus pour la succession
 * (leadership, mobilite) viennent de l'evaluation du manager.
 */
public enum SourceEvaluation {

    /** Auto-evaluation : le fichier rempli par le collaborateur. */
    AUTO("Auto-évaluation"),

    /** Evaluation du manager : le fichier rempli par le manager, seule source du classeur. */
    MANAGER("Évaluation du manager");

    private final String libelle;

    SourceEvaluation(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
