package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Vivier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VivierRepository extends JpaRepository<Vivier, Integer> {

    /** Vivier gere par le moteur, retrouve par son code fixe et non par son nom. */
    Optional<Vivier> findByCode(String code);
}