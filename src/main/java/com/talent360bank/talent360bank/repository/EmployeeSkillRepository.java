package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Integer> {

    @Query("select s from EmployeeSkill s join fetch s.competence join fetch s.employe "
            + "where s.employe.employeeId in :employeeIds")
    List<EmployeeSkill> findByEmployeIdsAvecCompetence(
            @Param("employeeIds") Collection<String> employeeIds);

    @Query("select s from EmployeeSkill s join fetch s.competence join fetch s.employe "
            + "where s.employe = :employe")
    List<EmployeeSkill> findByEmployeAvecCompetence(
            @Param("employe") Employe employe);
}