package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.ValidationSuccession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ValidationSuccessionRepository extends JpaRepository<ValidationSuccession, Integer> {

    /** Decisions du trimestre, avec poste, successeur et auteur, en une requete. */
    @Query("select v from ValidationSuccession v join fetch v.poste join fetch v.successeur "
            + "left join fetch v.utilisateur where v.trimestre = :trimestre")
    List<ValidationSuccession> findByTrimestreAvecDetails(@Param("trimestre") Trimestre trimestre);

    @Query("select v from ValidationSuccession v where v.trimestre = :trimestre "
            + "and v.poste.posteId = :posteId and v.successeur.idCollaborateur = :idCollaborateur")
    Optional<ValidationSuccession> findDecision(@Param("trimestre") Trimestre trimestre,
                                                @Param("posteId") String posteId,
                                                @Param("idCollaborateur") String idCollaborateur);

    /** {posteId, matricule du successeur, date} des decisions datees du trimestre. */
    @Query("select v.poste.posteId, v.successeur.idCollaborateur, v.dateDecision from ValidationSuccession v "
            + "where v.trimestre = :trimestre and v.dateDecision is not null")
    List<Object[]> findDates(@Param("trimestre") Trimestre trimestre);
}
