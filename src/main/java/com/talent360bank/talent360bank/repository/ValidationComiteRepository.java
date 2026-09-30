package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ValidationComiteRepository extends JpaRepository<ValidationComite, Integer> {

    @Query("select v.statut from ValidationComite v "
            + "where v.collaborateur.idCollaborateur = :idCollaborateur and v.trimestre = :trimestre")
    Optional<StatutValidationComite> findStatut(@Param("idCollaborateur") String idCollaborateur,
                                                @Param("trimestre") Trimestre trimestre);

    @Query("select v from ValidationComite v join fetch v.collaborateur where v.trimestre = :trimestre")
    List<ValidationComite> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);
}
