package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrimestreRepository extends JpaRepository<Trimestre, Integer> {

    Optional<Trimestre> findTopByOrderByAnneeDescNumeroDesc();

    /**
     * Un trimestre s'identifie par son couple (numero, annee), pas par sa cle
     * technique : c'est ce couple que portent les URL de l'API.
     */
    Optional<Trimestre> findByNumeroAndAnnee(Integer numero, Integer annee);

    /**
     * Trimestres anterieurs, du plus recent au plus ancien.
     */
    @Query("select t from Trimestre t "
            + "where t.annee < :annee or (t.annee = :annee and t.numero < :numero) "
            + "order by t.annee desc, t.numero desc")
    List<Trimestre> findPrecedents(@Param("annee") Integer annee,
                                   @Param("numero") Integer numero,
                                   Pageable pageable);
}