package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireEngagementRepository extends JpaRepository<QuestionnaireEngagement, Integer> {

    Optional<QuestionnaireEngagement> findByEmployeAndTrimestre(Employe employe, Trimestre trimestre);

    @Query("select q from QuestionnaireEngagement q join fetch q.employe "
            + "where q.trimestre = :trimestre")
    List<QuestionnaireEngagement> findByTrimestreAvecEmploye(@Param("trimestre") Trimestre trimestre);
}
