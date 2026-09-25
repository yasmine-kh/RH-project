package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppartenanceVivierRepository extends JpaRepository<AppartenanceVivier, Integer> {

    List<AppartenanceVivier> findByTrimestre(Trimestre trimestre);
}