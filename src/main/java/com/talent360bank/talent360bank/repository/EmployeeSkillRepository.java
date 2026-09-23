package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Integer> {

    /**
     * La competence etant LAZY, la lire hors transaction leverait une
     * LazyInitializationException : cette requete la charge d'emblee.
     */
    @Query("select s from EmployeeSkill s join fetch s.competence where s.employe = :employe")
    List<EmployeeSkill> findByEmployeAvecCompetence(@Param("employe") Employe employe);

    /**
     * Variante en lot : une seule requete pour tous les candidats d'un
     * classement, plutot qu'une par employe.
     */
    @Query("select s from EmployeeSkill s join fetch s.competence join fetch s.employe "
            + "where s.employe.employeeId in :employeeIds")
    List<EmployeeSkill> findByEmployeIdsAvecCompetence(
            @Param("employeeIds") Collection<String> employeeIds);
}
