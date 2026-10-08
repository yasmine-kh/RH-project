package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.ReponseQuestionnaire;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ReponseQuestionnaireRepository;
import com.talent360bank.talent360bank.ui.model.ReponsesQuestionnaire;
import com.talent360bank.talent360bank.ui.model.ReponsesQuestionnaire.Reponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Section "Reponses au questionnaire" de la fiche : les reponses importees d'un
 * collaborateur pour un trimestre, dans l'ordre des colonnes du fichier.
 * Une lecture ; aucun calcul ni regroupement.
 */
@Service
public class ReponsesQuestionnaireViewService {

    private final ReponseQuestionnaireRepository repository;

    public ReponsesQuestionnaireViewService(ReponseQuestionnaireRepository repository) {
        this.repository = repository;
    }

    /** @param trimestre null : aucune reponse (pas de trimestre) */
    @Transactional(readOnly = true)
    public ReponsesQuestionnaire construire(String matricule, Trimestre trimestre) {
        if (trimestre == null) {
            return new ReponsesQuestionnaire(null, null, null, List.of());
        }
        List<ReponseQuestionnaire> reponses = repository.findDuCollaborateur(matricule, trimestre);
        LocalDateTime date = reponses.stream().map(ReponseQuestionnaire::getDateReponse).filter(Objects::nonNull)
                .findFirst().orElse(null);
        return new ReponsesQuestionnaire(TrimestreCourantService.valeur(trimestre),
                TrimestreCourantService.libelle(trimestre), date,
                reponses.stream().map(r -> new Reponse(r.getCodeQuestion(), r.getTheme(), r.getQuestion(),
                        r.getReponse())).toList());
    }
}
