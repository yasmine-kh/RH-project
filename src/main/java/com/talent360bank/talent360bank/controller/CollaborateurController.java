package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** L'import des collaborateurs passe par POST /api/imports (ImportController), et par lui seul. */
@RestController
@RequestMapping("/api/collaborateurs")
public class CollaborateurController {

    @Autowired
    private CollaborateurRepository collaborateurRepository;

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
}
