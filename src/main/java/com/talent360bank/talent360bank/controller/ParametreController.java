package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.service.ParametreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parametres")
public class ParametreController {

    @Autowired
    private ParametreService parametreService;

    @GetMapping
    public List<Parametre> listerTous() {
        return parametreService.listerTous();
    }

    @PostMapping
    public Parametre creer(@RequestBody Parametre parametre) {
        return parametreService.enregistrer(parametre);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable String id) {
        parametreService.supprimer(id);
    }
}