package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Matrice9BoxRepository extends JpaRepository<Matrice9Box, Integer> {

    Optional<Matrice9Box> findByNiveauPerformanceAndNiveauPotentiel(Integer niveauPerformance,
                                                                    Integer niveauPotentiel);
}
