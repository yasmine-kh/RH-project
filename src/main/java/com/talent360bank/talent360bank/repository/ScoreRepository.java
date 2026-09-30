package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Integer> {

    Optional<Score> findByCollaborateurAndTrimestre(Collaborateur collaborateur, Trimestre trimestre);

    Optional<Score> findByCollaborateurIdCollaborateurAndTrimestre(String matricule, Trimestre trimestre);

    /**
     * Collaborateur et Trimestre etant LAZY, les parcourir hors transaction leverait
     * une LazyInitializationException : cette requete les charge d'emblee.
     */
    @Query("select s from Score s join fetch s.collaborateur join fetch s.trimestre "
            + "where s.trimestre = :trimestre")
    List<Score> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);

    /** Scores du trimestre hors de la liste : ceux que le dernier recalcul n'a pas produits. */
    @Modifying
    @Query("delete from Score s where s.trimestre = :trimestre and s.idScore not in :conserves")
    int supprimerSaufCeux(@Param("trimestre") Trimestre trimestre, @Param("conserves") Collection<Integer> conserves);

    @Modifying
    @Query("delete from Score s where s.trimestre = :trimestre")
    int supprimerTous(@Param("trimestre") Trimestre trimestre);

    @Query("select s from Score s join fetch s.trimestre where s.collaborateur = :collaborateur "
            + "order by s.trimestre.annee desc, s.trimestre.numero desc")
    List<Score> findHistorique(@Param("collaborateur") Collaborateur collaborateur);
}
