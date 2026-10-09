package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CollaborateurResponse;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collaborateurs")
public class CollaborateurController {

    private final CollaborateurRepository collaborateurRepository;
    private final ChargeurRessources chargeur;
    private final com.talent360bank.talent360bank.service.JournalService journalService;

    public CollaborateurController(CollaborateurRepository collaborateurRepository, ChargeurRessources chargeur,
                                   com.talent360bank.talent360bank.service.JournalService journalService) {
        this.journalService = journalService;
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
        boolean existait = collaborateur.getIdCollaborateur() != null
                && collaborateurRepository.existsById(collaborateur.getIdCollaborateur());
        Collaborateur enregistre = collaborateurRepository.save(collaborateur);
        String id = enregistre.getIdCollaborateur();
        journalService.collaborateur(id, "Collaborateur " + id + " (" + enregistre.getNomComplet() + ") "
                + (existait ? "modifié" : "ajouté") + " par l'API.", "/fiche-collaborateur?matricule=" + id);
        return collaborateurRepository.findAllByIdAvecEntite(List.of(id)).get(0);
    }

    // Soft Delete (B11) : Passage du statut a INACTIF au lieu de supprimer physiquement
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        Collaborateur collab = chargeur.exigerCollaborateur(id);
        collab.setStatut(StatutCollaborateur.INACTIF);
        collaborateurRepository.save(collab);
        journalService.collaborateur(id, "Collaborateur " + id + " (" + collab.getNomComplet()
                + ") supprimé par l'API : passé INACTIF, ses données sont conservées.",
                "/fiche-collaborateur?matricule=" + id);
        return ResponseEntity.noContent().build();
    }
}