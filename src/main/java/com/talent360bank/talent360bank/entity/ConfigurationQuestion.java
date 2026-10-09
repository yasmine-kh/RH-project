package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reglage RH d'une question du questionnaire d'engagement, par code (Q01, Q02... : rang de la
 * colonne dans le fichier de reponses) : sa dimension, et si c'est la question eNPS (0 a 10).
 * Saisi sur la page Parametres ; sans ligne, la question n'a ni dimension ni role eNPS. Vaut pour
 * tous les trimestres (les codes suivent l'ordre des colonnes du formulaire).
 */
@Entity
@Table(name = "configuration_question")
public class ConfigurationQuestion {

    public static final int DIMENSION_MAX = 100;

    @Id
    @NotBlank
    @Size(max = 10)
    @Column(name = "code_question", length = 10)
    private String codeQuestion;

    /** Dimension d'engagement (texte libre du RH : "Management", "Carrière"...), null sans dimension. */
    @Size(max = DIMENSION_MAX)
    @Column(length = DIMENSION_MAX)
    private String dimension;

    /** Question "recommanderiez-vous..." notee de 0 a 10 : une seule a la fois. */
    @Column(name = "question_enps", nullable = false)
    private boolean questionEnps;

    public ConfigurationQuestion() {
    }

    public ConfigurationQuestion(String codeQuestion, String dimension, boolean questionEnps) {
        this.codeQuestion = codeQuestion;
        this.dimension = dimension;
        this.questionEnps = questionEnps;
    }

    public String getCodeQuestion() { return codeQuestion; }
    public String getDimension() { return dimension; }
    public boolean isQuestionEnps() { return questionEnps; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public void setQuestionEnps(boolean questionEnps) { this.questionEnps = questionEnps; }
}
