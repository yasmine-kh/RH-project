package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.CompetenceDTO;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.service.CompetenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<CompetenceDTO> creer(@Valid @RequestBody CompetenceDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competenceService.creer(dto));
    }

    // Soft Delete (B11) : Archivage via le service
    @PutMapping("/{id}/archiver")
    public ResponseEntity<Void> archiver(@PathVariable String id) {
        competenceService.archiverCompetence(id);
        return ResponseEntity.noContent().build();
    }

    // Suppression définitive (si nécessaire)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        competenceService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}