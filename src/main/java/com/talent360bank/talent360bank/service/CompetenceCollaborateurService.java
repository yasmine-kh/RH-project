package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.SeuilsGapCompetence;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Competences d'un collaborateur et statut de leur gap (06_EMPLOYEE_SKILLS H).
 *
 * <p>Les competences ne dependent pas du trimestre ; le trimestre choisit le
 * seuil de Prioritaire, lu dans son Parametre. Lecture seule.
 */
@Service
public class CompetenceCollaborateurService {

    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final CalculService calculService;

    public CompetenceCollaborateurService(CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                          CalculService calculService) {
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
        this.calculService = calculService;
    }

    /**
     * Competences de le collaborateur, dans l'ordre du referentiel (identifiant).
     *
     * @throws RessourceIntrouvableException si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public List<GapCompetence> competences(Collaborateur collaborateur, Trimestre trimestre) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(trimestre, "trimestre");

        SeuilsGapCompetence seuils = calculService.chargerParametre(trimestre).getSeuilsGapCompetence();

        return competenceCollaborateurRepository.findByCollaborateurAvecCompetence(collaborateur).stream()
                .sorted(Comparator.comparing(skill -> skill.getCompetence().getCompetenceId()))
                .map(skill -> evaluer(skill, seuils))
                .toList();
    }

    /** Variante sans acces base, pour evaluer une competence deja chargee. */
    public GapCompetence evaluer(CompetenceCollaborateur skill, SeuilsGapCompetence seuils) {
        Integer gap = skill.getNiveauActuel() == null || skill.getNiveauCible() == null
                ? null
                : skill.getNiveauCible() - skill.getNiveauActuel();
        return new GapCompetence(skill, gap,
                calculService.statutGap(skill.getNiveauActuel(), skill.getNiveauCible(), seuils));
    }

    /**
     * Competences en gap Prioritaire des collaborateurs actifs, avec le seuil du
     * trimestre (00_DASHBOARD G10 : COUNTIF(06_EMPLOYEE_SKILLS!H, "Prioritaire")).
     * Deux requetes quel que soit le nombre de competences.
     *
     * @throws RessourceIntrouvableException si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public int compterGapsPrioritaires(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        SeuilsGapCompetence seuils = calculService.chargerParametre(trimestre).getSeuilsGapCompetence();
        int prioritaires = 0;
        for (Object[] niveaux : competenceCollaborateurRepository.findNiveauxParStatut(StatutCollaborateur.ACTIF)) {
            if (calculService.statutGap((Integer) niveaux[0], (Integer) niveaux[1], seuils)
                    == StatutGapCompetence.PRIORITAIRE) {
                prioritaires++;
            }
        }
        return prioritaires;
    }
}
