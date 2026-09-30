package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Poste;
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

    /**
     * Postes pour lesquels le collaborateur est successeur identifie, avec leurs
     * competences requises, en une requete : fiche collaborateur.
     */
    @Query("select distinct p from SuccesseurIdentifie s join s.poste p "
            + "left join fetch p.competenceRequise1 left join fetch p.competenceRequise2 "
            + "left join fetch p.competenceRequise3 left join fetch p.competenceRequise4 "
            + "left join fetch p.competenceRequise5 left join fetch p.entite "
            + "where s.collaborateur.idCollaborateur = :idCollaborateur order by p.posteId")
    List<Poste> findPostesDuSuccesseur(@Param("idCollaborateur") String idCollaborateur);
}
