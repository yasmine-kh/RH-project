package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.controller.dto.CollaborateurDTO;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CollaborateurService {

    private final CollaborateurRepository collaborateurRepository;

    public CollaborateurService(CollaborateurRepository collaborateurRepository) {
        this.collaborateurRepository = collaborateurRepository;
    }

    // Récupérer uniquement les collaborateurs actifs
    public List<CollaborateurDTO> listerCollaborateursActifs() {
        return collaborateurRepository.findByStatut(StatutCollaborateur.ACTIF)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // Créer un collaborateur
    public CollaborateurDTO creer(CollaborateurDTO dto) {
        Collaborateur entity = toEntity(dto);
        entity.setStatut(StatutCollaborateur.ACTIF);
        Collaborateur saved = collaborateurRepository.save(entity);
        return toDto(saved);
    }

    // Archiver (Soft Delete)
    public void archiverCollaborateur(String idCollaborateur) {
        Collaborateur collab = collaborateurRepository.findById(idCollaborateur)
                .orElseThrow(() -> new EntityNotFoundException("Collaborateur non trouvé avec l'identifiant : " + idCollaborateur));

        collab.setStatut(StatutCollaborateur.ARCHIVE);
        collaborateurRepository.save(collab);
    }

    // Conversion Entity -> DTO
    private CollaborateurDTO toDto(Collaborateur entity) {
        CollaborateurDTO dto = new CollaborateurDTO();
        dto.setIdCollaborateur(entity.getIdCollaborateur());
        dto.setNom(entity.getNom());
        dto.setPrenom(entity.getPrenom());
        dto.setEmail(entity.getEmail());
        dto.setStatut(entity.getStatut());
        return dto;
    }

    // Conversion DTO -> Entity
    private Collaborateur toEntity(CollaborateurDTO dto) {
        Collaborateur entity = new Collaborateur();
        entity.setIdCollaborateur(dto.getIdCollaborateur());
        entity.setNom(dto.getNom());
        entity.setPrenom(dto.getPrenom());
        entity.setEmail(dto.getEmail());
        entity.setStatut(dto.getStatut() != null ? dto.getStatut() : StatutCollaborateur.ACTIF);
        return entity;
    }
}