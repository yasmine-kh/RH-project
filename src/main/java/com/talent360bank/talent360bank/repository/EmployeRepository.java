package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Employe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeRepository extends JpaRepository<Employe, String> {
}