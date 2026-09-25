package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.StatutEmploye;

/**
 * Employe tel que l'API l'expose. Volontairement plat : serialiser l'entite
 * entrainerait son manager, lui-meme un Employe, et la relation etant LAZY,
 * Jackson echouerait hors transaction.
 */
public record EmployeResume(String employeeId, String nomComplet, String direction,
                            String departement, String fonction, String grade,
                            StatutEmploye statut, Integer anciennete) {

    public static EmployeResume de(Employe employe) {
        return new EmployeResume(employe.getEmployeeId(), employe.getNomComplet(),
                employe.getDirection(), employe.getDepartement(), employe.getFonction(),
                employe.getGrade(), employe.getStatut(), employe.getAnciennete());
    }
}
