package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;

import java.time.LocalDate;

/**
 * Collaborateur tel que l'API l'expose. Volontairement plat : serialiser l'entite
 * entrainerait son manager, lui-meme un Collaborateur, et la relation etant LAZY,
 * Jackson echouerait hors transaction.
 */
public record CollaborateurResume(String idCollaborateur, String nomComplet, String direction,
                                  String departement, String fonction, String grade,
                                  StatutCollaborateur statut, Integer anciennete) {

    /** @param dateReference date de reference du trimestre : l'anciennete y est mesuree */
    public static CollaborateurResume de(Collaborateur collaborateur, LocalDate dateReference) {
        return new CollaborateurResume(collaborateur.getIdCollaborateur(), collaborateur.getNomComplet(),
                collaborateur.getDirection(), collaborateur.getDepartement(), collaborateur.getFonction(),
                collaborateur.getGrade(), collaborateur.getStatut(), collaborateur.getAnciennete(dateReference));
    }
}
