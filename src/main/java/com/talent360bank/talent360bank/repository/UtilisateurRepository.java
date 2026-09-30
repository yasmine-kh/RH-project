package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer> {

    Optional<Utilisateur> findByLogin(String login);

    /** Pour la connexion : le statut du collaborateur rattache est lu dans la foulee. */
    @Query("select u from Utilisateur u left join fetch u.collaborateur where u.login = :login")
    Optional<Utilisateur> findByLoginAvecCollaborateur(@Param("login") String login);

    boolean existsByRole(Role role);
}
