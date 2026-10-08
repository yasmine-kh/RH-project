package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.ReponseQuestionnaire;
import com.talent360bank.talent360bank.entity.Trimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReponseQuestionnaireRepository extends JpaRepository<ReponseQuestionnaire, Integer> {

    /** Les reponses d'un collaborateur pour un trimestre, dans l'ordre des colonnes du fichier. */
    @Query("select r from ReponseQuestionnaire r where r.collaborateur.idCollaborateur = :matricule "
            + "and r.trimestre = :trimestre order by r.ordre")
    List<ReponseQuestionnaire> findDuCollaborateur(@Param("matricule") String matricule,
                                                   @Param("trimestre") Trimestre trimestre);

    /** Reimport : les reponses precedentes de ces collaborateurs pour ce trimestre sont remplacees. */
    @Modifying
    @Query("delete from ReponseQuestionnaire r where r.trimestre = :trimestre "
            + "and r.collaborateur.idCollaborateur in :matricules")
    int supprimerPour(@Param("trimestre") Trimestre trimestre, @Param("matricules") Collection<String> matricules);
}
