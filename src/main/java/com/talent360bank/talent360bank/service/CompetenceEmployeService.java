package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.SeuilsGapCompetence;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.service.resultat.CompetenceEmploye;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Competences d'un employe et statut de leur gap (06_EMPLOYEE_SKILLS H).
 *
 * <p>Les competences ne dependent pas du trimestre ; le trimestre choisit le
 * seuil de Prioritaire, lu dans son Parametre. Lecture seule.
 */
@Service
public class CompetenceEmployeService {

    private final EmployeeSkillRepository employeeSkillRepository;
    private final CalculService calculService;

    public CompetenceEmployeService(EmployeeSkillRepository employeeSkillRepository,
                                    CalculService calculService) {
        this.employeeSkillRepository = employeeSkillRepository;
        this.calculService = calculService;
    }

    /**
     * Competences de l'employe, dans l'ordre du referentiel (identifiant).
     *
     * @throws RessourceIntrouvableException si les reglages du trimestre sont absents
     */
    @Transactional(readOnly = true)
    public List<CompetenceEmploye> competences(Employe employe, Trimestre trimestre) {
        Objects.requireNonNull(employe, "employe");
        Objects.requireNonNull(trimestre, "trimestre");

        SeuilsGapCompetence seuils = calculService.chargerParametre(trimestre).getSeuilsGapCompetence();

        return employeeSkillRepository.findByEmployeAvecCompetence(employe).stream()
                .sorted(Comparator.comparing(skill -> skill.getCompetence().getCompetenceId()))
                .map(skill -> evaluer(skill, seuils))
                .toList();
    }

    /** Variante sans acces base, pour evaluer une competence deja chargee. */
    public CompetenceEmploye evaluer(EmployeeSkill skill, SeuilsGapCompetence seuils) {
        Integer gap = skill.getNiveauActuel() == null || skill.getNiveauCible() == null
                ? null
                : skill.getNiveauCible() - skill.getNiveauActuel();
        return new CompetenceEmploye(skill, gap,
                calculService.statutGap(skill.getNiveauActuel(), skill.getNiveauCible(), seuils));
    }
}
