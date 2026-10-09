package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Une reponse brute d'un collaborateur au questionnaire d'engagement (fichier
 * de reponses du formulaire, importe par ImportQuestionnaireService) : la
 * question telle qu'ecrite dans l'en-tete du fichier et la reponse telle que
 * saisie, sans conversion. Le score d'engagement (QuestionnaireEngagement, lu
 * dans 12_VIGILANCE du classeur) n'en est pas deduit : les deux imports sont
 * independants.
 */
@Entity
@Table(name = "reponse_questionnaire", uniqueConstraints = @UniqueConstraint(
        name = "uk_reponse_questionnaire", columnNames = {"id_collaborateur", "id_trimestre", "code_question"}))
public class ReponseQuestionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idReponse;

    @NotNull(message = "Le collaborateur est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @NotNull(message = "Le trimestre est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    /** Q01, Q02... : rang de la colonne parmi les questions du fichier. */
    @NotBlank
    @Column(name = "code_question", nullable = false, length = 10)
    private String codeQuestion;

    /** Rang d'affichage : l'ordre des colonnes du fichier. */
    @Column(nullable = false)
    private int ordre;

    /** Theme de la question : la dimension reglee par le RH pour ce code (configuration_question), vide sinon. */
    @Column(length = 100)
    private String theme;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String question;

    @Column(length = 2000)
    private String reponse;

    /** Horodatage de la reponse (colonne Horodateur du fichier), null s'il manque. */
    @Column(name = "date_reponse")
    private LocalDateTime dateReponse;

    public ReponseQuestionnaire() {
    }

    public ReponseQuestionnaire(Collaborateur collaborateur, Trimestre trimestre, String codeQuestion, int ordre,
                                String theme, String question, String reponse, LocalDateTime dateReponse) {
        this.collaborateur = collaborateur;
        this.trimestre = trimestre;
        this.codeQuestion = codeQuestion;
        this.ordre = ordre;
        this.theme = theme;
        this.question = question;
        this.reponse = reponse;
        this.dateReponse = dateReponse;
    }

    public Integer getIdReponse() { return idReponse; }
    public Collaborateur getCollaborateur() { return collaborateur; }
    public Trimestre getTrimestre() { return trimestre; }
    public String getCodeQuestion() { return codeQuestion; }
    public int getOrdre() { return ordre; }
    public String getTheme() { return theme; }
    public String getQuestion() { return question; }
    public String getReponse() { return reponse; }
    public LocalDateTime getDateReponse() { return dateReponse; }
}
