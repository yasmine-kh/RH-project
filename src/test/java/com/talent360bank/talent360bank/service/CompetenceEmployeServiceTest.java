package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.service.resultat.CompetenceEmploye;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetenceEmployeServiceTest {

    @Mock
    private EmployeeSkillRepository employeeSkillRepository;
    @Mock
    private ParametreRepository parametreRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private PotentielRepository potentielRepository;

    private CompetenceEmployeService service;
    private Employe employe;
    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        service = new CompetenceEmployeService(employeeSkillRepository,
                new CalculService(parametreRepository, performanceRepository, potentielRepository));
        employe = new Employe();
        employe.setEmployeeId("BP001");
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        parametre = Parametre.parDefaut(trimestre);
    }

    private EmployeeSkill skill(String competenceId, Integer actuel, Integer cible, String statutImporte) {
        Competence competence = new Competence();
        competence.setCompetenceId(competenceId);
        competence.setNom("Competence " + competenceId);
        EmployeeSkill skill = new EmployeeSkill();
        skill.setEmploye(employe);
        skill.setCompetence(competence);
        skill.setNiveauActuel(actuel);
        skill.setNiveauCible(cible);
        skill.setStatutGap(statutImporte);
        return skill;
    }

    @Test
    void les_competences_sont_rendues_dans_l_ordre_du_referentiel_avec_gap_et_statut() {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(employeeSkillRepository.findByEmployeAvecCompetence(employe)).thenReturn(List.of(
                skill("C03", 1, 3, null), skill("C01", 3, 3, null), skill("C02", 2, 3, null)));

        List<CompetenceEmploye> competences = service.competences(employe, trimestre);

        assertThat(competences).extracting(c -> c.competence().getCompetence().getCompetenceId())
                .containsExactly("C01", "C02", "C03");
        assertThat(competences).extracting(CompetenceEmploye::gap).containsExactly(0, 1, 2);
        assertThat(competences).extracting(CompetenceEmploye::statut).containsExactly(
                StatutGapCompetence.MAITRISE, StatutGapCompetence.A_DEVELOPPER, StatutGapCompetence.PRIORITAIRE);
    }

    @Test
    void le_seuil_vient_du_parametre_du_trimestre_et_non_du_statut_importe() {
        parametre.getSeuilsGapCompetence().setSeuilPrioritaire(3);
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(employeeSkillRepository.findByEmployeAvecCompetence(employe))
                .thenReturn(List.of(skill("C01", 1, 3, "Prioritaire")));

        assertThat(service.competences(employe, trimestre).get(0).statut())
                .isEqualTo(StatutGapCompetence.A_DEVELOPPER);
    }

    @Test
    void un_niveau_manquant_rend_gap_et_statut_inconnus() {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.of(parametre));
        when(employeeSkillRepository.findByEmployeAvecCompetence(employe))
                .thenReturn(List.of(skill("C01", null, 3, null)));

        CompetenceEmploye competence = service.competences(employe, trimestre).get(0);

        assertThat(competence.gap()).isNull();
        assertThat(competence.statut()).isNull();
    }

    @Test
    void un_trimestre_sans_reglages_est_signale() {
        when(parametreRepository.findByTrimestre(trimestre)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.competences(employe, trimestre))
                .isInstanceOf(RessourceIntrouvableException.class);
    }
}
