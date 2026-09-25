package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParametreService {

    @Autowired
    private ParametreRepository parametreRepository;

    public List<Parametre> listerTous() {
        return parametreRepository.findAll();
    }

    public Parametre enregistrer(Parametre parametre) {
        return parametreRepository.save(parametre);
    }

    public void supprimer(String id) {
        parametreRepository.deleteById(id);
    }
}