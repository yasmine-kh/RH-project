package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.ResultatRecalcul;
import com.talent360bank.talent360bank.service.TalentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TalentController.class, NeufBoxController.class})
class TalentEtNeufBoxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TalentService talentService;
    @MockBean
    private NeufBoxService neufBoxService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private Employe employe;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        employe = new Employe();
        employe.setEmployeeId("E001");
        employe.setNom("Bennani");
        employe.setPrenom("Sara");
        employe.setDateEntree(LocalDate.of(2020, 1, 15));
        employe.setStatut(StatutEmploye.ACTIF);
    }

    private Score score() {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal("92.00"));
        score.setScorePotentiel(new BigDecimal("88.00"));
        score.setPositionBox("Talent cle");
        return score;
    }

    @Test
    void les_talents_du_trimestre_sont_exposes() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(talentService.detecterTalents(trimestre)).thenReturn(List.of(score()));

        mockMvc.perform(get("/api/trimestres/2026/1/talents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value("E001"))
                .andExpect(jsonPath("$[0].scorePerformance").value(92.00))
                .andExpect(jsonPath("$[0].positionBox").value("Talent cle"));
    }

    @Test
    void le_statut_de_talent_d_un_employe_est_un_objet() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(chargeur.exigerEmploye("E001")).thenReturn(employe);
        when(talentService.estTalent(employe, trimestre)).thenReturn(true);

        mockMvc.perform(get("/api/trimestres/2026/1/talents/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("E001"))
                .andExpect(jsonPath("$.estTalent").value(true));
    }

    @Test
    void des_donnees_incompletes_rendent_422_et_non_500() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(talentService.detecterTalents(trimestre))
                .thenThrow(new DonneesIncompletesException("Les seuils de talent ne sont pas configures"));

        mockMvc.perform(get("/api/trimestres/2026/1/talents"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erreur").value("donnees_incompletes"))
                .andExpect(jsonPath("$.message").value("Les seuils de talent ne sont pas configures"));
    }

    @Test
    void le_placement_9box_rend_le_bilan() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(neufBoxService.placerTrimestre(trimestre)).thenReturn(new ResultatRecalcul(
                List.of(score()),
                List.of(new ResultatRecalcul.EmployeIgnore("E002", "Score incomplet"))));

        mockMvc.perform(post("/api/trimestres/2026/1/9box/placement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCalcules").value(1))
                .andExpect(jsonPath("$.scores[0].positionBox").value("Talent cle"))
                .andExpect(jsonPath("$.ignores[0].motif").value("Score incomplet"));
    }

    @Test
    void un_verbe_non_supporte_rend_405_et_non_500() throws Exception {
        // Sans corps JSON : l'exception est levee avant que Spring n'ait
        // resolu le controleur, donc l'advice cloisonnee sur le package de
        // l'API ne s'y applique pas. Le statut reste juste, c'est l'essentiel.
        mockMvc.perform(get("/api/trimestres/2026/1/9box/placement"))
                .andExpect(status().isMethodNotAllowed());
    }
}
