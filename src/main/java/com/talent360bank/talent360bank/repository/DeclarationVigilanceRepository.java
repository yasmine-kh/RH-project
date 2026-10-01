package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface DeclarationVigilanceRepository extends JpaRepository<DeclarationVigilance, Integer> {

    @Query("select d from DeclarationVigilance d join fetch d.collaborateur where d.trimestre = :trimestre")
    List<DeclarationVigilance> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);

    /**
     * Faits d'un trimestre pour un lot de collaborateurs, sans charger les
     * collaborateurs (le matricule suffit) : vigilance d'une equipe.
     */
    @Query("select d from DeclarationVigilance d "
            + "where d.trimestre = :trimestre and d.collaborateur.idCollaborateur in :ids")
    List<DeclarationVigilance> findByTrimestreEtCollaborateurs(@Param("trimestre") Trimestre trimestre,
                                                              @Param("ids") Collection<String> ids);
}
