package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.controller.dto.CompetenceDTO;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompetenceService {

    @Autowired
    private CompetenceRepository competenceRepository;

    public List<Competence> listerTous() {
        return competenceRepository.findAll();
    }

    public CompetenceDTO creer(CompetenceDTO dto) {
        Competence comp = new Competence();
        // Si l'ID est une String générée/fournie :
        if (dto.getIdCompetence() != null) {
            comp.setCompetenceId(String.valueOf(dto.getIdCompetence()));
        }
        comp.setNom(dto.getLibelle());
        comp.setCategorie(dto.getDescription());

        Competence enregistree = competenceRepository.save(comp);

        dto.setIdCompetence(null); // ou adapte selon la gestion de tes identifiants
        return dto;
    }

    // Soft Delete (Passage du flag à archivé) avec ID de type String
    public void archiverCompetence(String id) {
        Competence comp = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence non trouvée : " + id));

        // Note : Vérifie si ton entité Competence possède bien setArchivee(boolean)
        // ou adapte selon le nom de ton champ dans l'Entity.
        comp.setArchivee(true);
        competenceRepository.save(comp);
    }

    public void supprimer(String competenceId) {
        competenceRepository.deleteById(competenceId);
    }
}