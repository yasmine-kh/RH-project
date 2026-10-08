package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.service.TemplateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final CollaborateurRepository collaborateurRepository;

    public TemplateController(TemplateService templateService, CollaborateurRepository collaborateurRepository) {
        this.templateService = templateService;
        this.collaborateurRepository = collaborateurRepository;
    }

    @GetMapping("/collaborateur/{matricule}")
    public ResponseEntity<byte[]> telechargerTemplateCollaborateur(@PathVariable String matricule,
                                                                   @RequestParam(defaultValue = "T3_2026") String campagne) throws IOException {
        byte[] excelData = templateService.genererTemplateCollaborateur(matricule, campagne, "1.0");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template_collaborateur_" + matricule + ".xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }

    @GetMapping("/manager/{matriculeManager}")
    public ResponseEntity<byte[]> telechargerTemplateManager(@PathVariable String matriculeManager,
                                                             @RequestParam(defaultValue = "T3_2026") String campagne) throws IOException {
        // En pratique, récupère les membres de l'équipe du manager
        List<Collaborateur> equipe = collaborateurRepository.findAll();

        byte[] excelData = templateService.genererTemplateManager(matriculeManager, campagne, "1.0", equipe);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template_manager_" + matriculeManager + ".xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }
}