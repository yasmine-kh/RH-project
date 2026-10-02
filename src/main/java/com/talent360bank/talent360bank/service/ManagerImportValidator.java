package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ManagerImportValidator {

    private final CollaborateurRepository collaborateurRepository;

    public ManagerImportValidator(CollaborateurRepository collaborateurRepository) {
        this.collaborateurRepository = collaborateurRepository;
    }

    /**
     * Vérifie qu'un manager n'évalue que les collaborateurs rattachés à son périmètre.
     */
    public boolean verifierPerimetreManager(String idManager, List<String> idsCollaborateursEvalues) {
        List<Collaborateur> equipeDuManager = collaborateurRepository.findAll(); // Ou méthode personnalisée par manager
        List<String> idsEquipe = equipeDuManager.stream().map(Collaborateur::getIdCollaborateur).toList();

        return idsEquipe.containsAll(idsCollaborateursEvalues);
    }
}