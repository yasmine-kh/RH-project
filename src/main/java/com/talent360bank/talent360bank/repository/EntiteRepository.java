package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.TypeEntite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntiteRepository extends JpaRepository<Entite, Integer> {

    Optional<Entite> findByCode(String code);

    List<Entite> findByType(TypeEntite type);
}
