package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CollaborateurResponse;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fiches collaborateurs. Les lectures rendent {@link CollaborateurResponse},
 * jamais l'entite.
 *
 * <p>L'import des collaborateurs passe par POST /api/imports (ImportController), et par lui seul.
 */
@RestController
@RequestMapping("/api/collaborateurs")
public class CollaborateurController {

    private final CollaborateurRepository collaborateurRepository;
    private final ChargeurRessources chargeur;

    public CollaborateurController(CollaborateurRepository collaborateurRepository, ChargeurRessources chargeur) {
        this.collaborateurRepository = collaborateurRepository;
        this.chargeur = chargeur;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CollaborateurResponse> listerTous() {
        return collaborateurRepository.findAllAvecManager().stream()
                .map(CollaborateurResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public CollaborateurResponse lire(@PathVariable String id) {
        return CollaborateurResponse.de(chargeur.exigerCollaborateur(id));
    }

    @PostMapping
    public Collaborateur creer(@RequestBody Collaborateur collaborateur) {
        return collaborateurRepository.save(collaborateur);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable String id) {
        collaborateurRepository.deleteById(id);
    }
}
