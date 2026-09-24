package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrimestreRepository extends JpaRepository<Trimestre, Integer> {

    Optional<Trimestre> findTopByOrderByAnneeDescNumeroDesc();
}