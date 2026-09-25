package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.Vivier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppartenanceVivierRepository extends JpaRepository<AppartenanceVivier, Integer> {

    List<AppartenanceVivier> findByTrimestre(Trimestre trimestre);

    @Query("select a from AppartenanceVivier a join fetch a.employe "
            + "where a.trimestre = :trimestre and a.vivier = :vivier")
    List<AppartenanceVivier> findByTrimestreEtVivier(@Param("trimestre") Trimestre trimestre,
                                                    @Param("vivier") Vivier vivier);

    /**
     * Supprime les appartenances d'une origine a un vivier sur un trimestre,
     * sans toucher aux autres origines, viviers ni trimestres.
     *
     * @return le nombre de lignes supprimees
     */
    @Modifying
    @Query("delete from AppartenanceVivier a "
            + "where a.trimestre = :trimestre and a.vivier = :vivier and a.origine = :origine")
    int supprimer(@Param("trimestre") Trimestre trimestre, @Param("vivier") Vivier vivier,
                  @Param("origine") String origine);
}