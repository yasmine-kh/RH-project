package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import com.talent360bank.talent360bank.service.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/employes")
public class EmployeController {

    @Autowired
    private EmployeRepository employeRepository;

    @Autowired
    private ImportService importService;

    @GetMapping
    public List<Employe> listerTous() {
        return employeRepository.findAll();
    }

    @PostMapping
    public Employe creer(@RequestBody Employe employe) {
        return employeRepository.save(employe);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable String id) {
        employeRepository.deleteById(id);
    }

    @PostMapping("/import")
    public String importerDepuisExcel(@RequestParam String cheminFichier) throws IOException {
        int nb = importService.importerCollaborateurs(cheminFichier);
        return nb + " collaborateurs importés";
    }
}