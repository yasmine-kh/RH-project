package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ParametreRepository extends JpaRepository<Parametre, Integer> {

    Optional<Parametre> findByTrimestre(Trimestre trimestre);

    /** Evite d'avoir a charger le Trimestre pour atteindre ses reglages. */
    @Query("select p from Parametre p where p.trimestre.numero = :numero and p.trimestre.annee = :annee")
    Optional<Parametre> findByNumeroEtAnnee(@Param("numero") Integer numero, @Param("annee") Integer annee);

    boolean existsByTrimestre(Trimestre trimestre);
}
