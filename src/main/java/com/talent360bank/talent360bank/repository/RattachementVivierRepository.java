package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.RattachementVivier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RattachementVivierRepository extends JpaRepository<RattachementVivier, Integer> {

    // 1. Si vous cherchez directement par l'objet Entite :
    Optional<RattachementVivier> findByDirection(Entite direction);

    // 2. Si vous cherchez par le libellé de la direction (champ libelle dans Entite) :
    Optional<RattachementVivier> findByDirectionLibelle(String libelle);


}