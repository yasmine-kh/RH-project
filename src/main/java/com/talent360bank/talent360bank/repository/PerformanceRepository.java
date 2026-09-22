package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Integer> {

    Optional<Performance> findByEmployeAndTrimestre(Employe employe, Trimestre trimestre);

    Optional<Performance> findByEmployeMatriculeAndTrimestre(String matricule, Trimestre trimestre);

    /**
     * Employe et Trimestre etant LAZY, les parcourir hors transaction leverait
     * une LazyInitializationException : cette requete les charge d'emblee.
     */
    @Query("select p from Performance p join fetch p.employe join fetch p.trimestre "
            + "where p.trimestre = :trimestre")
    List<Performance> findByTrimestreAvecEmploye(@Param("trimestre") Trimestre trimestre);

    boolean existsByEmployeAndTrimestre(Employe employe, Trimestre trimestre);
}
