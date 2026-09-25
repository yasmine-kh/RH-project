package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.service.CompetenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/competences")
public class CompetenceController {

    @Autowired
    private CompetenceService competenceService;

    @GetMapping
    public List<Competence> listerTous() {
        return competenceService.listerTous();
    }

    @PostMapping
    public Competence creer(@RequestBody Competence competence) {
        return competenceService.enregistrer(competence);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable String id) {
        competenceService.supprimer(id);
    }
}