package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.service.DossierImportService;
import com.talent360bank.talent360bank.service.DossierImportService.RapportDossierImport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/imports/dossier")
public class DossierImportController {

    private final DossierImportService dossierImportService;

    public DossierImportController(DossierImportService dossierImportService) {
        this.dossierImportService = dossierImportService;
    }

    @PostMapping
    public ResponseEntity<RapportDossierImport> lancerImportDossier(
            @RequestParam int annee,
            @RequestParam int numero,
            @RequestParam(defaultValue = "false") boolean simulation) throws IOException {

        RapportDossierImport rapport = dossierImportService.importerDossier(annee, numero, simulation);
        return ResponseEntity.ok(rapport);
    }
}