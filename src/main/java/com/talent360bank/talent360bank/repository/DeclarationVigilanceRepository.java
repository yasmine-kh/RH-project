package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeclarationVigilanceRepository extends JpaRepository<DeclarationVigilance, Integer> {

    @Query("select d from DeclarationVigilance d join fetch d.collaborateur where d.trimestre = :trimestre")
    List<DeclarationVigilance> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);
}
