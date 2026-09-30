package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer> {

    Optional<Utilisateur> findByLogin(String login);

    boolean existsByRole(Role role);
}
