package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollaborateurRepository extends JpaRepository<Collaborateur, String> {

    long countByStatut(StatutCollaborateur statut);

    /** Toute la population avec son manager, son entite et ses parents, en une requete (liste de l'API). */
    @Query("select c from Collaborateur c left join fetch c.manager m left join fetch m.collaborateur "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "order by c.idCollaborateur")
    List<Collaborateur> findAllAvecManager();

    /**
     * Un collaborateur avec son manager (et le collaborateur qui tient le role),
     * son entite et ses parents, en une requete : fiche collaborateur, et tout
     * endpoint qui exige un collaborateur (ChargeurRessources).
     */
    @Query("select c from Collaborateur c left join fetch c.manager m left join fetch m.collaborateur "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where c.idCollaborateur = :idCollaborateur")
    Optional<Collaborateur> findByIdAvecManager(@Param("idCollaborateur") String idCollaborateur);

    /**
     * Equipe directe d'un manager (collaborateurs dont il est le manager), hors
     * statut exclu, avec entite et parents, triee par nom : vue manager.
     */
    @Query("select c from Collaborateur c "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where c.manager = :manager and c.statut <> :exclu order by c.nom, c.prenom, c.idCollaborateur")
    List<Collaborateur> findEquipeDirecte(@Param("manager") Manager manager,
                                          @Param("exclu") StatutCollaborateur exclu);

    /** Taille de l'equipe directe de chaque manager, hors statut exclu : {idManager, nombre}. */
    @Query("select c.manager.idManager, count(c) from Collaborateur c "
            + "where c.manager is not null and c.statut <> :exclu group by c.manager.idManager")
    List<Object[]> compterEquipesDirectes(@Param("exclu") StatutCollaborateur exclu);

    /**
     * Collaborateurs rattaches a un ensemble d'entites (un sous-arbre), hors
     * statut exclu, avec entite et parents, tries par nom : vue entite.
     */
    @Query("select c from Collaborateur c "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where c.entite.idEntite in :idsEntites and c.statut <> :exclu "
            + "order by c.nom, c.prenom, c.idCollaborateur")
    List<Collaborateur> findByEntites(@Param("idsEntites") Collection<Integer> idsEntites,
                                      @Param("exclu") StatutCollaborateur exclu);

    /** Effectif rattache directement a chaque entite, hors statut exclu : {idEntite, nombre}. */
    @Query("select c.entite.idEntite, count(c) from Collaborateur c "
            + "where c.entite is not null and c.statut <> :exclu group by c.entite.idEntite")
    List<Object[]> compterParEntite(@Param("exclu") StatutCollaborateur exclu);

    /**
     * Collaborateurs d'un statut, avec entite et parents, par matricule :
     * liste de vigilance du trimestre (tous les actifs).
     */
    @Query("select c from Collaborateur c "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where c.statut = :statut order by c.idCollaborateur")
    List<Collaborateur> findByStatutAvecEntite(@Param("statut") StatutCollaborateur statut);

    /** Collaborateurs par matricule, avec entite et parents (evite un chargement d'entite par ligne). */
    @Query("select c from Collaborateur c "
            + "left join fetch c.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where c.idCollaborateur in :ids")
    List<Collaborateur> findAllByIdAvecEntite(@Param("ids") Collection<String> ids);
}
