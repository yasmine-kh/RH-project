package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.SuccesseurIdentifie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuccesseurIdentifieRepository extends JpaRepository<SuccesseurIdentifie, Integer> {

    @Query("select s.collaborateur.idCollaborateur from SuccesseurIdentifie s where s.poste.posteId = :posteId")
    List<String> findIdsCollaborateursParPoste(@Param("posteId") String posteId);

    @Query("select s from SuccesseurIdentifie s join fetch s.poste join fetch s.collaborateur")
    List<SuccesseurIdentifie> findAllAvecPosteEtCollaborateur();
}
