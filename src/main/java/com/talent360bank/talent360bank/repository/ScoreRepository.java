package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Integer> {

    Optional<Score> findByEmployeAndTrimestre(Employe employe, Trimestre trimestre);

    Optional<Score> findByEmployeMatriculeAndTrimestre(String matricule, Trimestre trimestre);

    /**
     * Employe et Trimestre etant LAZY, les parcourir hors transaction leverait
     * une LazyInitializationException : cette requete les charge d'emblee.
     */
    @Query("select s from Score s join fetch s.employe join fetch s.trimestre "
            + "where s.trimestre = :trimestre")
    List<Score> findByTrimestreAvecEmploye(@Param("trimestre") Trimestre trimestre);

    @Query("select s from Score s join fetch s.trimestre where s.employe = :employe "
            + "order by s.trimestre.annee desc, s.trimestre.numero desc")
    List<Score> findHistorique(@Param("employe") Employe employe);
}
