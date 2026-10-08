package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ImportExcelResume;
import com.talent360bank.talent360bank.controller.dto.ImportResponse;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.service.CampagneService;
import com.talent360bank.talent360bank.service.DossierImportService;
import com.talent360bank.talent360bank.service.DossierImportService.RapportDossierImport;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Import du classeur TALENT_360_BANK_Dataset, dossiers automatiques et journal des imports.
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;
    private final CampagneService campagneService;


    public ImportController(ImportService importService,
                            CampagneService campagneService,
                            DossierImportService dossierImportService) {
        this.importService = importService;
        this.campagneService = campagneService;

    }

    /**
     * Importe le classeur individuel pour un trimestre donne.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importer(@RequestParam("fichier") MultipartFile fichier,
                                                   @RequestParam int annee,
                                                   @RequestParam int numero,
                                                   @RequestParam(defaultValue = "false") boolean simulation,
                                                   @RequestParam(defaultValue = "true") boolean calcul) {
        ResultatCampagne resultat = campagneService.importer(fichier, annee, numero, simulation, calcul);
        HttpStatus statut = resultat.importation().statut() == StatutImport.ECHEC
                ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK;
        return ResponseEntity.status(statut).body(ImportResponse.de(resultat));
    }

    /**
     * Journal / historique des imports, le plus recent en premier.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ImportExcelResume> journal() {
        return importService.journal().stream().map(ImportExcelResume::de).toList();
    }

    /**
     * Historique des campagnes d'importation.
     */
    @GetMapping("/historique")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ImportExcelResume>> obtenirHistorique() {
        return ResponseEntity.ok(importService.journal().stream().map(ImportExcelResume::de).toList());
    }
}