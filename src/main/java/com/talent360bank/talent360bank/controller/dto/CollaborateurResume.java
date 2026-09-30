package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;

/**
 * Collaborateur tel que l'API l'expose. Volontairement plat : serialiser l'entite
 * entrainerait son manager, lui-meme un Collaborateur, et la relation etant LAZY,
 * Jackson echouerait hors transaction.
 */
public record CollaborateurResume(String idCollaborateur, String nomComplet, String direction,
                                  String departement, String fonction, String grade,
                                  StatutCollaborateur statut, Integer anciennete) {

    public static CollaborateurResume de(Collaborateur collaborateur) {
        return new CollaborateurResume(collaborateur.getIdCollaborateur(), collaborateur.getNomComplet(),
                collaborateur.getDirection(), collaborateur.getDepartement(), collaborateur.getFonction(),
                collaborateur.getGrade(), collaborateur.getStatut(), collaborateur.getAnciennete());
    }
}
