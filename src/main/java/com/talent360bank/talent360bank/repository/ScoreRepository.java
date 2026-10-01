package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Integer> {

    Optional<Score> findByCollaborateurAndTrimestre(Collaborateur collaborateur, Trimestre trimestre);

    Optional<Score> findByCollaborateurIdCollaborateurAndTrimestre(String matricule, Trimestre trimestre);

    /**
     * Scores du trimestre avec tout ce que leurs lecteurs affichent, en une
     * requete : le collaborateur et son entite actuelle (direction des listes
     * de candidats), l'entite figee sur le score (direction du trimestre :
     * comite, viviers) et, pour chacune, ses parents sur les quatre niveaux.
     * Les entites sont LAZY : sans ces jointures, chaque ligne lue hors
     * transaction leverait une LazyInitializationException.
     */
    @Query("select s from Score s join fetch s.trimestre join fetch s.collaborateur c "
            + "left join fetch c.entite ce left join fetch ce.parent ce1 "
            + "left join fetch ce1.parent ce2 left join fetch ce2.parent "
            + "left join fetch s.entite se left join fetch se.parent se1 "
            + "left join fetch se1.parent se2 left join fetch se2.parent "
            + "where s.trimestre = :trimestre")
    List<Score> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);

    /** Scores du trimestre hors de la liste : ceux que le dernier recalcul n'a pas produits. */
    @Modifying
    @Query("delete from Score s where s.trimestre = :trimestre and s.idScore not in :conserves")
    int supprimerSaufCeux(@Param("trimestre") Trimestre trimestre, @Param("conserves") Collection<Integer> conserves);

    @Modifying
    @Query("delete from Score s where s.trimestre = :trimestre")
    int supprimerTous(@Param("trimestre") Trimestre trimestre);

    @Query("select s from Score s join fetch s.trimestre where s.collaborateur = :collaborateur "
            + "order by s.trimestre.annee desc, s.trimestre.numero desc")
    List<Score> findHistorique(@Param("collaborateur") Collaborateur collaborateur);

    /**
     * Scores d'un trimestre pour un lot de collaborateurs (une equipe), avec le
     * collaborateur et l'entite figee et ses parents : vue manager, vigilance d'un lot.
     */
    @Query("select s from Score s join fetch s.collaborateur "
            + "left join fetch s.entite e left join fetch e.parent e1 "
            + "left join fetch e1.parent e2 left join fetch e2.parent "
            + "where s.trimestre = :trimestre and s.collaborateur.idCollaborateur in :ids")
    List<Score> findByTrimestreEtCollaborateurs(@Param("trimestre") Trimestre trimestre,
                                                @Param("ids") Collection<String> ids);
}
