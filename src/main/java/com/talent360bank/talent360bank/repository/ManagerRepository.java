package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Manager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ManagerRepository extends JpaRepository<Manager, Integer> {

    Optional<Manager> findByCollaborateurIdCollaborateur(String idCollaborateur);

    /** Un manager par le matricule de son collaborateur, avec entite et parents : vue manager. */
    @Query("select m from Manager m join fetch m.collaborateur c "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where c.idCollaborateur = :idCollaborateur")
    Optional<Manager> findByMatriculeAvecEntite(@Param("idCollaborateur") String idCollaborateur);

    /** Tous les managers avec leur collaborateur, entite et parents, par nom : liste de la vue manager. */
    @Query("select m from Manager m join fetch m.collaborateur c "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "order by c.nom, c.prenom, c.idCollaborateur")
    List<Manager> findAllAvecEntite();
}
