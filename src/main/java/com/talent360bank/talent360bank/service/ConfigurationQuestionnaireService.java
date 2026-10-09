package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.ConfigurationQuestion;
import com.talent360bank.talent360bank.repository.ConfigurationQuestionRepository;
import com.talent360bank.talent360bank.repository.ReponseQuestionnaireRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglages RH du questionnaire d'engagement (page Parametres) : la dimension de chaque question et
 * la question eNPS, par code de question (configuration_question). Enregistrer recopie la dimension
 * dans reponse_questionnaire.theme (colonne Theme du Talent Passport) et journalise le changement.
 */
@Service
public class ConfigurationQuestionnaireService {

    private final ConfigurationQuestionRepository configurationRepository;
    private final ReponseQuestionnaireRepository reponseRepository;
    private final JournalService journalService;

    public ConfigurationQuestionnaireService(ConfigurationQuestionRepository configurationRepository,
                                             ReponseQuestionnaireRepository reponseRepository,
                                             JournalService journalService) {
        this.configurationRepository = configurationRepository;
        this.reponseRepository = reponseRepository;
        this.journalService = journalService;
    }

    /** Une question deja importee, avec son reglage actuel. */
    public record QuestionReglable(String code, String texte, String dimension, boolean questionEnps) {
    }

    /** Questions de tous les fichiers importes, dans l'ordre des colonnes, avec leur reglage. */
    @Transactional(readOnly = true)
    public List<QuestionReglable> questions() {
        Map<String, ConfigurationQuestion> reglages = new HashMap<>();
        configurationRepository.findAll().forEach(c -> reglages.put(c.getCodeQuestion(), c));
        List<QuestionReglable> questions = new ArrayList<>();
        for (Object[] ligne : reponseRepository.findQuestions()) {
            String code = (String) ligne[0];
            ConfigurationQuestion reglage = reglages.get(code);
            questions.add(new QuestionReglable(code, (String) ligne[1],
                    reglage == null ? null : reglage.getDimension(), reglage != null && reglage.isQuestionEnps()));
        }
        return List.copyOf(questions);
    }

    /** Dimensions deja utilisees, pour proposer les memes libelles. */
    @Transactional(readOnly = true)
    public Set<String> dimensions() {
        Set<String> dimensions = new java.util.TreeSet<>();
        configurationRepository.findAll().forEach(c -> {
            if (c.getDimension() != null) {
                dimensions.add(c.getDimension());
            }
        });
        return dimensions;
    }

    /**
     * Remplace les reglages de toutes les questions importees.
     *
     * @param dimensions code → dimension (vide ou absent : aucune)
     * @param codeEnps   code de la question eNPS, vide pour aucune
     * @return message de confirmation
     * @throws IllegalArgumentException code inconnu ou dimension trop longue : rien n'est enregistre
     */
    @Transactional
    public String enregistrer(Map<String, String> dimensions, String codeEnps) {
        Set<String> codes = new LinkedHashSet<>();
        reponseRepository.findQuestions().forEach(ligne -> codes.add((String) ligne[0]));
        String enps = codeEnps == null || codeEnps.isBlank() ? null : codeEnps.trim();
        if (enps != null && !codes.contains(enps)) {
            throw new IllegalArgumentException("Question eNPS inconnue : " + enps + ". Rien n'a été enregistré.");
        }
        for (String code : dimensions.keySet()) {
            if (!codes.contains(code)) {
                throw new IllegalArgumentException("Question inconnue : " + code + ". Rien n'a été enregistré.");
            }
        }
        int avecDimension = 0;
        Set<String> nomsDimensions = new java.util.TreeSet<>();
        for (String code : codes) {
            String dimension = dimensions.get(code) == null || dimensions.get(code).isBlank()
                    ? null : dimensions.get(code).strip().replaceAll("\\s+", " ");
            if (dimension != null && dimension.length() > ConfigurationQuestion.DIMENSION_MAX) {
                throw new IllegalArgumentException("La dimension de " + code + " dépasse "
                        + ConfigurationQuestion.DIMENSION_MAX + " caractères. Rien n'a été enregistré.");
            }
            boolean estEnps = code.equals(enps);
            if (dimension == null && !estEnps) {
                configurationRepository.deleteById(code);
            } else {
                ConfigurationQuestion reglage = configurationRepository.findById(code)
                        .orElseGet(() -> new ConfigurationQuestion(code, null, false));
                reglage.setDimension(dimension);
                reglage.setQuestionEnps(estEnps);
                configurationRepository.save(reglage);
            }
            reponseRepository.renseignerTheme(code, dimension);
            if (dimension != null) {
                avecDimension++;
                nomsDimensions.add(dimension);
            }
        }
        String message = "Questionnaire d'engagement : " + avecDimension + " question(s) rattachée(s) à "
                + nomsDimensions.size() + " dimension(s)" + (nomsDimensions.isEmpty() ? "" : " ("
                + String.join(", ", nomsDimensions) + ")") + " ; question eNPS : "
                + (enps == null ? "aucune" : enps) + ".";
        journalService.parametres(null, message, "/engagement");
        return message;
    }
}
