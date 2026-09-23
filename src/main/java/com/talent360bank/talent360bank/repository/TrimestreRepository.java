package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrimestreRepository extends JpaRepository<Trimestre, Integer> {

    /**
     * Trimestres anterieurs, du plus recent au plus ancien. Appele avec une
     * page de taille 1 pour obtenir le trimestre precedent : la comparaison
     * porte sur le couple (annee, numero), pas sur l'identifiant technique,
     * qui suit l'ordre de creation des lignes et non l'ordre du calendrier.
     */
    @Query("select t from Trimestre t "
            + "where t.annee < :annee or (t.annee = :annee and t.numero < :numero) "
            + "order by t.annee desc, t.numero desc")
    List<Trimestre> findPrecedents(@Param("annee") Integer annee,
                                   @Param("numero") Integer numero,
                                   Pageable pageable);
}
