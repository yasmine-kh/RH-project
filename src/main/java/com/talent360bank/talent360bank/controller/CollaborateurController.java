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

    private final com.talent360bank.talent360bank.repository.EntiteRepository entiteRepository;
    private final com.talent360bank.talent360bank.repository.ManagerRepository managerRepository;

    public CollaborateurController(CollaborateurRepository collaborateurRepository, ChargeurRessources chargeur,
                                   com.talent360bank.talent360bank.repository.EntiteRepository entiteRepository,
                                   com.talent360bank.talent360bank.repository.ManagerRepository managerRepository,
                                   com.talent360bank.talent360bank.service.JournalService journalService) {
        this.journalService = journalService;
        this.entiteRepository = entiteRepository;
        this.managerRepository = managerRepository;
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

    /**
     * Cree le collaborateur, ou met a jour celui qui a ce matricule. Corps valide (CollaborateurRequest) : une
     * valeur refusee rend 400 avec un message par champ ; une entite ou un manager inconnu, 400. A la mise a
     * jour, une entite ou un manager absent du corps garde sa valeur.
     */
    @PostMapping
    public Collaborateur creer(@jakarta.validation.Valid @RequestBody
                               com.talent360bank.talent360bank.controller.dto.CollaborateurRequest demande) {
        Collaborateur collaborateur = collaborateurRepository.findById(demande.idCollaborateur().trim())
                .orElse(null);
        boolean existait = collaborateur != null;
        if (collaborateur == null) {
            collaborateur = new Collaborateur();
            collaborateur.setIdCollaborateur(demande.idCollaborateur().trim());
        }
        collaborateur.setNom(demande.nom().trim());
        collaborateur.setPrenom(demande.prenom().trim());
        collaborateur.setSexe(demande.sexe());
        collaborateur.setDateNaissance(demande.dateNaissance());
        collaborateur.setDateEntree(demande.dateEntree());
        collaborateur.setFonction(demande.fonction());
        collaborateur.setGrade(demande.grade());
        collaborateur.setEmail(demande.email());
        collaborateur.setStatut(demande.statut() == null ? StatutCollaborateur.ACTIF : demande.statut());
        if (demande.entite() != null) {
            Integer idEntite = demande.entite().idEntite();
            collaborateur.setEntite(entiteRepository.findById(idEntite).orElseThrow(() ->
                    new IllegalArgumentException("Entité inconnue : " + idEntite)));
        }
        if (demande.manager() != null) {
            Integer idManager = demande.manager().idManager();
            collaborateur.setManager(managerRepository.findById(idManager).orElseThrow(() ->
                    new IllegalArgumentException("Manager inconnu : " + idManager)));
        }
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