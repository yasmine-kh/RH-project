package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompetenceCollaborateurRepository extends JpaRepository<CompetenceCollaborateur, Integer> {

    @Query("select s from CompetenceCollaborateur s join fetch s.competence join fetch s.collaborateur "
            + "where s.collaborateur.idCollaborateur in :idsCollaborateurs")
    List<CompetenceCollaborateur> findByCollaborateurIdsAvecCompetence(
            @Param("idsCollaborateurs") Collection<String> idsCollaborateurs);

    @Query("select s from CompetenceCollaborateur s join fetch s.competence join fetch s.collaborateur "
            + "where s.collaborateur = :collaborateur")
    List<CompetenceCollaborateur> findByCollaborateurAvecCompetence(
            @Param("collaborateur") Collaborateur collaborateur);
}