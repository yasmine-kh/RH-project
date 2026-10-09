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

    /** Toutes les reponses d'un trimestre avec leur collaborateur (ecran Engagement), en une requete. */
    @Query("select r from ReponseQuestionnaire r join fetch r.collaborateur c left join fetch c.entite e "
            + "left join fetch e.parent e1 left join fetch e1.parent e2 left join fetch e2.parent e3 "
            + "where r.trimestre = :trimestre order by r.ordre, c.nom, c.prenom")
    List<ReponseQuestionnaire> findByTrimestreAvecCollaborateur(@Param("trimestre") Trimestre trimestre);

    /** {code, texte} de chaque question deja importee, tous trimestres (page Parametres). */
    @Query("select r.codeQuestion, max(r.question), min(r.ordre) from ReponseQuestionnaire r "
            + "group by r.codeQuestion order by min(r.ordre)")
    List<Object[]> findQuestions();

    /** Theme d'affichage (Talent Passport) recopie de la dimension reglee pour ce code de question. */
    @Modifying
    @Query("update ReponseQuestionnaire r set r.theme = :theme where r.codeQuestion = :code")
    int renseignerTheme(@Param("code") String code, @Param("theme") String theme);

    /** Reimport : les reponses precedentes de ces collaborateurs pour ce trimestre sont remplacees. */
    @Modifying
    @Query("delete from ReponseQuestionnaire r where r.trimestre = :trimestre "
            + "and r.collaborateur.idCollaborateur in :matricules")
    int supprimerPour(@Param("trimestre") Trimestre trimestre, @Param("matricules") Collection<String> matricules);
}
