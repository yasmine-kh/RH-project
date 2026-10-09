package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.TypeEntite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EntiteRepository extends JpaRepository<Entite, Integer> {

    Optional<Entite> findByCode(String code);

    List<Entite> findByType(TypeEntite type);

    /** Tout l'organigramme en une requete, parent charge : arbre de la vue entite. */
    @Query("select e from Entite e left join fetch e.parent")
    List<Entite> findAllAvecParent();

    /** Identifiants de toutes les entites (table de quelques centaines de lignes), en une requete. */
    @Query("select e.idEntite from Entite e")
    java.util.Set<Integer> findAllIds();
}
