package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Poste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PosteRepository extends JpaRepository<Poste, String> {

    @Query("select p from Poste p " +
            "left join fetch p.competenceRequise1 left join fetch p.competenceRequise2 " +
            "left join fetch p.competenceRequise3 left join fetch p.competenceRequise4 " +
            "left join fetch p.competenceRequise5 " +
            "where p.posteId = :posteId")
    Optional<Poste> findByIdAvecCompetences(@Param("posteId") String posteId);

    List<Poste> findByPosteCritique(String posteCritique);

    /** Tous les postes avec competences requises et entite, en une requete : couverture d'un lot de postes. */
    @Query("select p from Poste p " +
            "left join fetch p.competenceRequise1 left join fetch p.competenceRequise2 " +
            "left join fetch p.competenceRequise3 left join fetch p.competenceRequise4 " +
            "left join fetch p.competenceRequise5 left join fetch p.entite " +
            "order by p.posteId")
    List<Poste> findAllAvecCompetences();
}
