package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.RattachementVivier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RattachementVivierRepository extends JpaRepository<RattachementVivier, Integer> {

    // 1. Si vous cherchez directement par l'objet Entite :
    Optional<RattachementVivier> findByDirection(Entite direction);

    // 2. Si vous cherchez par le libellé de la direction (champ libelle dans Entite) :
    Optional<RattachementVivier> findByDirectionLibelle(String libelle);

    /**
     * Les rattachements dont la direction existe, avec elle, en une requete. La jointure interne
     * ecarte une ligne dont entite_id pointe nulle part (0 ecrit par une migration ddl-auto) : elle
     * n'est jamais dereferencee. Voir le guide, section "Entites manquantes".
     */
    @Query("select r from RattachementVivier r join fetch r.direction")
    List<RattachementVivier> findAllAvecDirection();


}