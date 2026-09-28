package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
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

    public Competence enregistrer(Competence competence) {
        return competenceRepository.save(competence);
    }

    public void supprimer(String competenceId) {
        competenceRepository.deleteById(competenceId);
    }
}