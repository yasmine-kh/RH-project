package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PotentielRepository extends JpaRepository<Potentiel, Integer> {

    Optional<Potentiel> findByCollaborateurAndTrimestre(Collaborateur collaborateur, Trimestre trimestre);

    Optional<Potentiel> findByCollaborateurIdCollaborateurAndTrimestre(String matricule, Trimestre trimestre);

    /**
     * Collaborateur et Trimestre etant LAZY, les parcourir hors transaction leverait
     * une LazyInitializationException : cette requete les charge d'emblee.
     */
    @Query("select p from Potentiel p join fetch p.collaborateur join fetch p.trimestre "
            + "where p.trimestre = :trimestre")
    List<Potentiel> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);

    boolean existsByCollaborateurAndTrimestre(Collaborateur collaborateur, Trimestre trimestre);
}
