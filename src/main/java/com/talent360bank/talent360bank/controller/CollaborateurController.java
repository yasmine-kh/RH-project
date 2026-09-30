package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.service.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/** /api/employes : ancien chemin, garde le temps que les clients passent a /api/collaborateurs. */
@RestController
@RequestMapping({"/api/collaborateurs", "/api/employes"})
public class CollaborateurController {

    @Autowired
    private CollaborateurRepository collaborateurRepository;

    @Autowired
    private ImportService importService;

    @GetMapping
    public List<Collaborateur> listerTous() {
        return collaborateurRepository.findAll();
    }

    @PostMapping
    public Collaborateur creer(@RequestBody Collaborateur collaborateur) {
        return collaborateurRepository.save(collaborateur);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable String id) {
        collaborateurRepository.deleteById(id);
    }

    @PostMapping("/import")
    public String importerDepuisExcel(@RequestParam String cheminFichier) throws IOException {
        int nb = importService.importerCollaborateurs(cheminFichier);
        return nb + " collaborateurs importés";
    }
}