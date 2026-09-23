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

    @NotNull(message = "L'employé est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_employe", nullable = false)
    private Employe employe;

    @NotNull(message = "Le trimestre est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    public QuestionnaireEngagement() {}

    public Integer getIdQuestionnaire() { return idQuestionnaire; }
    public void setIdQuestionnaire(Integer idQuestionnaire) { this.idQuestionnaire = idQuestionnaire; }
    public BigDecimal getScoreEngagement() { return scoreEngagement; }
    public void setScoreEngagement(BigDecimal scoreEngagement) { this.scoreEngagement = scoreEngagement; }
    public LocalDate getDateReponse() { return dateReponse; }
    public void setDateReponse(LocalDate dateReponse) { this.dateReponse = dateReponse; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }
    public Trimestre getTrimestre() { return trimestre; }
    public void setTrimestre(Trimestre trimestre) { this.trimestre = trimestre; }
}