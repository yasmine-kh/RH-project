package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.service.CompetenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/competences")
public class CompetenceController {

    private final CompetenceService competenceService;

    public CompetenceController(CompetenceService competenceService) {
        this.competenceService = competenceService;
    }

    @GetMapping
    public List<Competence> listerTous() {
        return competenceService.listerTous();
    }

    @PostMapping
    public Competence creer(@Valid @RequestBody Competence competence) {
        return competenceService.enregistrer(competence);
    }

    // Soft Delete (B11) : Archivage / Desactivation via le service
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        competenceService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}