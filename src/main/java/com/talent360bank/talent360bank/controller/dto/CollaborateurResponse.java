package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Sexe;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;

import java.time.LocalDate;

/**
 * Fiche d'un collaborateur pour GET /api/collaborateurs[/{id}], a la place de
 * l'entite JPA : tous les champs (seul le RH se connecte), l'entite rendue par
 * ses libelles et le manager par son matricule.
 *
 * <p>Pas d'anciennete : elle se mesure a la date de reference d'un trimestre
 * (Trimestre.dateReference), que cette ressource n'a pas.
 */
public record CollaborateurResponse(String idCollaborateur, String nom, String prenom, Sexe sexe,
                                    LocalDate dateNaissance, LocalDate dateEntree,
                                    String direction, String departement, String region, String agence,
                                    String fonction, String grade, String idManager,
                                    StatutCollaborateur statut) {

    /** A appeler dans une transaction : le manager est charge a la demande. */
    public static CollaborateurResponse de(Collaborateur collaborateur) {
        Manager manager = collaborateur.getManager();
        return new CollaborateurResponse(collaborateur.getIdCollaborateur(), collaborateur.getNom(),
                collaborateur.getPrenom(), collaborateur.getSexe(), collaborateur.getDateNaissance(),
                collaborateur.getDateEntree(), collaborateur.getDirection(), collaborateur.getDepartement(),
                collaborateur.getRegion(), collaborateur.getAgence(), collaborateur.getFonction(),
                collaborateur.getGrade(), manager == null ? null : manager.getIdCollaborateur(),
                collaborateur.getStatut());
    }
}
