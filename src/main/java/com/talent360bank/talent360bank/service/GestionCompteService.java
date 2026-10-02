package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GestionCompteService {

    private final CollaborateurRepository collaborateurRepository;

    public GestionCompteService(CollaborateurRepository collaborateurRepository) {
        this.collaborateurRepository = collaborateurRepository;
    }

    @Transactional
    public String reinitialiserMotDePasse(String idCollaborateur) {
        Collaborateur collab = collaborateurRepository.findById(idCollaborateur)
                .orElseThrow(() -> new IllegalArgumentException("Collaborateur non trouvé : " + idCollaborateur));

        String nouveauMotDePasse = UUID.randomUUID().toString().substring(0, 8);
        // Sauvegarder ou notifier le nouveau mot de passe selon la logique de l'application
        return nouveauMotDePasse;
    }

    @Transactional
    public void desactiverCompte(String idCollaborateur) {
        Collaborateur collab = collaborateurRepository.findById(idCollaborateur)
                .orElseThrow(() -> new IllegalArgumentException("Collaborateur non trouvé : " + idCollaborateur));

        collab.setStatut(StatutCollaborateur.INACTIF);
        collaborateurRepository.save(collab);
    }
}