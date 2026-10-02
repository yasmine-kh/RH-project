package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class QuestionnaireEngagementService {

    private final QuestionnaireEngagementRepository questionnaireRepository;

    public QuestionnaireEngagementService(QuestionnaireEngagementRepository questionnaireRepository) {
        this.questionnaireRepository = questionnaireRepository;
    }

    /**
     * Enregistre ou met à jour un questionnaire d'engagement pour un collaborateur et un trimestre.
     */
    @Transactional
    public QuestionnaireEngagement enregistrerReponse(Collaborateur collaborateur, Trimestre trimestre, BigDecimal scoreCalculer) {
        QuestionnaireEngagement questionnaire = questionnaireRepository
                .findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseGet(() -> {
                    QuestionnaireEngagement q = new QuestionnaireEngagement();
                    q.setCollaborateur(collaborateur);
                    q.setTrimestre(trimestre);
                    return q;
                });

        questionnaire.setScoreEngagement(scoreCalculer);
        questionnaire.setDateReponse(LocalDate.now());
        questionnaire.setStatut("SOUMIS");

        return questionnaireRepository.save(questionnaire);
    }

    /**
     * Calcule la moyenne d'une liste de notes d'items d'engagement (sur 100 ou 5)
     * et enregistre le questionnaire.
     */
    @Transactional
    public QuestionnaireEngagement soumettreQuestionnaireAvecItems(Collaborateur collaborateur, Trimestre trimestre, List<BigDecimal> notesItems) {
        if (notesItems == null || notesItems.isEmpty()) {
            throw new IllegalArgumentException("La liste des réponses aux items d'engagement ne peut pas être vide.");
        }

        BigDecimal somme = notesItems.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal moyenne = somme.divide(BigDecimal.valueOf(notesItems.size()), 2, RoundingMode.HALF_UP);

        return enregistrerReponse(collaborateur, trimestre, moyenne);
    }

    /**
     * Récupère le questionnaire d'un collaborateur pour un trimestre donné.
     */
    public Optional<QuestionnaireEngagement> obtenirQuestionnaire(Collaborateur collaborateur, Trimestre trimestre) {
        return questionnaireRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre);
    }

    /**
     * Récupère tous les questionnaires soumis sur un trimestre.
     */
    public List<QuestionnaireEngagement> obtenirQuestionnairesDuTrimestre(Trimestre trimestre) {
        return questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre);
    }

    /**
     * Récupère les questionnaires d'une équipe de collaborateurs sur un trimestre.
     */
    public List<QuestionnaireEngagement> obtenirQuestionnairesEquipe(Trimestre trimestre, Collection<String> matricules) {
        if (matricules == null || matricules.isEmpty()) {
            return List.of();
        }
        return questionnaireRepository.findByTrimestreEtCollaborateurs(trimestre, matricules);
    }
}