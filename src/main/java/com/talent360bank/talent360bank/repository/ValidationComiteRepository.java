package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ValidationComiteRepository extends JpaRepository<ValidationComite, Integer> {

    @Query("select v.statut from ValidationComite v "
            + "where v.collaborateur.idCollaborateur = :idCollaborateur and v.trimestre = :trimestre")
    Optional<StatutValidationComite> findStatut(@Param("idCollaborateur") String idCollaborateur,
                                                @Param("trimestre") Trimestre trimestre);

    /**
     * Decisions d'un lot de collaborateurs sur le trimestre, en une requete :
     * {matricule, statut}. Un matricule sans ligne n'a pas de decision.
     */
    @Query("select v.collaborateur.idCollaborateur, v.statut from ValidationComite v "
            + "where v.trimestre = :trimestre and v.collaborateur.idCollaborateur in :ids")
    List<Object[]> findStatuts(@Param("trimestre") Trimestre trimestre, @Param("ids") Collection<String> ids);

    @Query("select v from ValidationComite v join fetch v.collaborateur where v.trimestre = :trimestre")
    List<ValidationComite> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);
}
