package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Manager;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagerRepository extends JpaRepository<Manager, Integer> {

    Optional<Manager> findByCollaborateurIdCollaborateur(String idCollaborateur);
}
