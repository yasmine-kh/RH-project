package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "questionnaire_engagement")
public class QuestionnaireEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idQuestionnaire;

    @Column(name = "score_engagement")
    private BigDecimal scoreEngagement;

    @Column(name = "date_reponse")
    private LocalDate dateReponse;

    private String statut;

    @NotNull(message = "Le collaborateur est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @NotNull(message = "Le trimestre est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;
    // ... tes attributs existants ...

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private SourceEvaluation source;

    /**
     * Reponses detaillees par item (ex: Q1 -> 4, Q2 -> 5).
     * Genere automatiquement la table 'questionnaire_engagement_item'.
     */
    @ElementCollection
    @CollectionTable(
            name = "questionnaire_engagement_item",
            joinColumns = @JoinColumn(name = "id_questionnaire")
    )
    @MapKeyColumn(name = "item_code")
    @Column(name = "score")
    private java.util.Map<String, Integer> reponsesItems = new java.util.HashMap<>();

    // --- GETTERS ET SETTERS A AJOUTER ---

    public SourceEvaluation getSource() {
        return source;
    }

    public void setSource(SourceEvaluation source) {
        this.source = source;
    }

    public java.util.Map<String, Integer> getReponsesItems() {
        return reponsesItems;
    }

    public void setReponsesItems(java.util.Map<String, Integer> reponsesItems) {
        this.reponsesItems = reponsesItems;
    }
    public QuestionnaireEngagement() {}

    public Integer getIdQuestionnaire() { return idQuestionnaire; }
    public void setIdQuestionnaire(Integer idQuestionnaire) { this.idQuestionnaire = idQuestionnaire; }
    public BigDecimal getScoreEngagement() { return scoreEngagement; }
    public void setScoreEngagement(BigDecimal scoreEngagement) { this.scoreEngagement = scoreEngagement; }
    public LocalDate getDateReponse() { return dateReponse; }
    public void setDateReponse(LocalDate dateReponse) { this.dateReponse = dateReponse; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Collaborateur getCollaborateur() { return collaborateur; }
    public void setCollaborateur(Collaborateur collaborateur) { this.collaborateur = collaborateur; }
    public Trimestre getTrimestre() { return trimestre; }
    public void setTrimestre(Trimestre trimestre) { this.trimestre = trimestre; }
}