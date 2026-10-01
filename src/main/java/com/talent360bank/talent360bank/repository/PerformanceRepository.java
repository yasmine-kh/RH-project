package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Notes de performance : au plus une ligne par collaborateur, trimestre et source
 * (auto-evaluation ou manager). Chaque lecture nomme sa source, sauf les
 * lectures "ToutesSources" / "les deux sources" qui rendent les deux d'un coup
 * pour le melange du score officiel et la comparaison auto / manager.
 */
public interface PerformanceRepository extends JpaRepository<Performance, Integer> {

    Optional<Performance> findByCollaborateurAndTrimestreAndSource(Collaborateur collaborateur, Trimestre trimestre,
                                                            SourceEvaluation source);

    Optional<Performance> findByCollaborateurIdCollaborateurAndTrimestreAndSource(String matricule, Trimestre trimestre,
                                                                         SourceEvaluation source);

    /** Les evaluations du collaborateur sur le trimestre, des deux sources (AUTO puis MANAGER) : fiche. */
    List<Performance> findByCollaborateurAndTrimestreOrderBySource(Collaborateur collaborateur, Trimestre trimestre);

    /**
     * Notes d'une source sur le trimestre, collaborateur et trimestre charges
     * d'emblee (LAZY : les parcourir hors transaction leverait une
     * LazyInitializationException).
     */
    @Query("select x from Performance x join fetch x.collaborateur join fetch x.trimestre "
            + "where x.trimestre = :trimestre and x.source = :source")
    List<Performance> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre,
                                               @Param("source") SourceEvaluation source);

    /**
     * Notes des deux sources sur le trimestre, collaborateur et trimestre
     * charges, en une requete : recalcul des scores officiels.
     */
    @Query("select x from Performance x join fetch x.collaborateur join fetch x.trimestre "
            + "where x.trimestre = :trimestre")
    List<Performance> findByTrimestreToutesSources(@Param("trimestre") Trimestre trimestre);

    /**
     * Notes des deux sources pour un lot de collaborateurs (une equipe, un
     * sous-arbre), collaborateur charge, en une requete : vues manager et entite.
     */
    @Query("select x from Performance x join fetch x.collaborateur "
            + "where x.trimestre = :trimestre and x.collaborateur.idCollaborateur in :ids")
    List<Performance> findByTrimestreEtCollaborateurs(@Param("trimestre") Trimestre trimestre,
                                              @Param("ids") Collection<String> ids);

    boolean existsByCollaborateurAndTrimestreAndSource(Collaborateur collaborateur, Trimestre trimestre,
                                                      SourceEvaluation source);

    /** Parmi un lot de collaborateurs, les matricules notes en performance par cette source sur le trimestre. */
    @Query("select x.collaborateur.idCollaborateur from Performance x "
            + "where x.trimestre = :trimestre and x.source = :source and x.collaborateur.idCollaborateur in :ids")
    List<String> findMatriculesEvalues(@Param("trimestre") Trimestre trimestre,
                                       @Param("ids") Collection<String> ids,
                                       @Param("source") SourceEvaluation source);

    /** Matricules notes en performance par cette source sur le trimestre, sans charger les collaborateurs. */
    @Query("select x.collaborateur.idCollaborateur from Performance x "
            + "where x.trimestre = :trimestre and x.source = :source")
    List<String> findMatriculesEvaluesDuTrimestre(@Param("trimestre") Trimestre trimestre,
                                                  @Param("source") SourceEvaluation source);
}
