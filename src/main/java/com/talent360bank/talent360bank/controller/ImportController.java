package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ImportExcelResume;
import com.talent360bank.talent360bank.controller.dto.ImportResponse;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.service.CampagneService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Import du classeur TALENT_360_BANK_Dataset et journal des imports. Voir
 * docs/import-donnees.md.
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;
    private final CampagneService campagneService;

    public ImportController(ImportService importService, CampagneService campagneService) {
        this.importService = importService;
        this.campagneService = campagneService;
    }

    /**
     * Importe le classeur pour le trimestre (cree s'il n'existe pas), puis
     * lance le calcul complet du trimestre. 200 avec les deux bilans si des
     * lignes ont ete importees, meme avec des erreurs de ligne (statut
     * PARTIEL) ; 422 avec le meme corps si rien n'a ete importe (statut
     * ECHEC : fichier illisible, format non conforme, autre periode...).
     *
     * @param simulation valide et compte sans rien enregistrer, sans calcul
     * @param calcul     false pour importer sans recalculer
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

    /** Journal des imports, le plus recent en premier. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ImportExcelResume> journal() {
        return importService.journal().stream().map(ImportExcelResume::de).toList();
    }
}
