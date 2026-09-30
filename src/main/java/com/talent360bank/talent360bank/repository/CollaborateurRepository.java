package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CollaborateurRepository extends JpaRepository<Collaborateur, String> {

    long countByStatut(StatutCollaborateur statut);

    /** Toute la population avec son manager, en une requete (liste de l'API). */
    @Query("select c from Collaborateur c left join fetch c.manager m left join fetch m.collaborateur "
            + "order by c.idCollaborateur")
    List<Collaborateur> findAllAvecManager();
}
