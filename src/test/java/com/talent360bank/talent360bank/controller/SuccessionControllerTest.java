package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.SuccessionService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuccessionController.class)
class SuccessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SuccessionService successionService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private Employe candidat;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        candidat = new Employe();
        candidat.setEmployeeId("E001");
        candidat.setNom("Bennani");
        candidat.setPrenom("Sara");
        candidat.setDateEntree(LocalDate.now().minusYears(10));
        candidat.setStatut(StatutEmploye.ACTIF);
        candidat.setDirection("Reseau");
    }

    private ResultatMatching matching() {
        return new ResultatMatching(candidat, new BigDecimal("87.00"), NiveauReadiness.MOINS_1_AN,
                new ResultatMatching.DetailMatching(
                        new BigDecimal("100.00"), new BigDecimal("90.00"), new BigDecimal("80.00"),
                        new BigDecimal("100.00"), new BigDecimal("70.00"), new BigDecimal("60.00")));
    }

    @Test
    void le_classement_expose_le_score_la_readiness_et_le_detail() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(successionService.classerCandidats("P001", trimestre)).thenReturn(List.of(matching()));

        mockMvc.perform(get("/api/postes/P001/candidats?annee=2026&numero=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidat.employeeId").value("E001"))
                .andExpect(jsonPath("$[0].candidat.direction").value("Reseau"))
                .andExpect(jsonPath("$[0].candidat.anciennete").value(10))
                .andExpect(jsonPath("$[0].scoreMatching").value(87.00))
                .andExpect(jsonPath("$[0].readiness").value("MOINS_1_AN"))
                .andExpect(jsonPath("$[0].readinessLibelle").value("< 1 an"))
                .andExpect(jsonPath("$[0].detail.competences").value(100.00))
                .andExpect(jsonPath("$[0].detail.mobilite").value(60.00));
    }

    @Test
    void la_limite_declenche_la_short_list() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(successionService.classerCandidats("P001", trimestre, 3)).thenReturn(List.of(matching()));

        mockMvc.perform(get("/api/postes/P001/candidats?annee=2026&numero=1&limite=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void une_limite_negative_rend_400() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(successionService.classerCandidats("P001", trimestre, -1))
                .thenThrow(new IllegalArgumentException("La limite ne peut pas etre negative"));

        mockMvc.perform(get("/api/postes/P001/candidats?annee=2026&numero=1&limite=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("argument_invalide"));
    }

    @Test
    void un_poste_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(successionService.classerCandidats("INCONNU", trimestre))
                .thenThrow(new RessourceIntrouvableException("Aucun poste INCONNU"));

        mockMvc.perform(get("/api/postes/INCONNU/candidats?annee=2026&numero=1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Aucun poste INCONNU"));
    }

    @Test
    void le_trimestre_est_obligatoire_et_son_absence_rend_400() throws Exception {
        mockMvc.perform(get("/api/postes/P001/candidats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"));
    }

    @Test
    void un_candidat_seul_est_evalue() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(chargeur.exigerEmploye("E001")).thenReturn(candidat);
        when(successionService.evaluer(candidat, "P001", trimestre)).thenReturn(matching());

        mockMvc.perform(get("/api/postes/P001/candidats/E001?annee=2026&numero=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreMatching").value(87.00));
    }
}
