package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEvenement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface JournalEvenementRepository extends JpaRepository<JournalEvenement, Integer> {

    /**
     * Evenements filtres, le plus recent d'abord. Un filtre null est ignore ; {@code recherche} (en
     * minuscules, entoure de %) porte sur la description et le matricule.
     */
    @Query(value = "select e from JournalEvenement e left join fetch e.utilisateur left join fetch e.trimestre "
            + "where (:type is null or e.type = :type) and (:trimestre is null or e.trimestre = :trimestre) "
            + "and (:du is null or e.dateEvenement >= :du) and (:avant is null or e.dateEvenement < :avant) "
            + "and (:recherche is null or lower(e.description) like :recherche or lower(e.matricule) like :recherche) "
            + "order by e.dateEvenement desc, e.idEvenement desc",
            countQuery = "select count(e) from JournalEvenement e "
            + "where (:type is null or e.type = :type) and (:trimestre is null or e.trimestre = :trimestre) "
            + "and (:du is null or e.dateEvenement >= :du) and (:avant is null or e.dateEvenement < :avant) "
            + "and (:recherche is null or lower(e.description) like :recherche or lower(e.matricule) like :recherche)")
    Page<JournalEvenement> rechercher(@Param("type") TypeEvenement type, @Param("trimestre") Trimestre trimestre,
                                      @Param("du") LocalDateTime du, @Param("avant") LocalDateTime avant,
                                      @Param("recherche") String recherche, Pageable page);

    /** References deja journalisees parmi celles donnees (rattrapage sans doublon). */
    @Query("select e.reference from JournalEvenement e where e.reference in :references")
    Set<String> findReferencesExistantes(@Param("references") Collection<String> references);

    /** Evenements d'un collaborateur, le plus recent d'abord. */
    @Query("select e from JournalEvenement e left join fetch e.utilisateur left join fetch e.trimestre "
            + "where e.matricule = :matricule order by e.dateEvenement desc, e.idEvenement desc")
    List<JournalEvenement> findByMatricule(@Param("matricule") String matricule);
}
