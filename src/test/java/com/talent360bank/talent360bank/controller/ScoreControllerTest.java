package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScoreController.class)
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScoreService scoreService;
    @MockBean
    private ScoreRepository scoreRepository;
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

    private Score score(String performance, String potentiel, String box) {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal(potentiel));
        score.setPositionBox(box);
        score.setDateCalcul(LocalDate.of(2026, 4, 1));
        return score;
    }

    @Test
    void le_recalcul_rend_le_bilan_avec_les_ignores() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(scoreService.recalculerTrimestre(trimestre)).thenReturn(new ResultatRecalcul(
                List.of(score("90.00", "80.00", "Performant")),
                List.of(new ResultatRecalcul.EmployeIgnore("E002", "Notes de potentiel absentes"))));

        mockMvc.perform(post("/api/trimestres/2026/1/scores/recalcul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCalcules").value(1))
                .andExpect(jsonPath("$.nombreIgnores").value(1))
                .andExpect(jsonPath("$.scores[0].employeeId").value("E001"))
                .andExpect(jsonPath("$.scores[0].nomComplet").value("Sara Bennani"))
                .andExpect(jsonPath("$.scores[0].scorePerformance").value(90.00))
                .andExpect(jsonPath("$.ignores[0].employeeId").value("E002"))
                .andExpect(jsonPath("$.ignores[0].motif").value("Notes de potentiel absentes"));
    }

    @Test
    void les_scores_du_trimestre_sont_aplatis() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre))
                .thenReturn(List.of(score("90.00", "80.00", "Performant")));

        mockMvc.perform(get("/api/trimestres/2026/1/scores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].positionBox").value("Performant"))
                .andExpect(jsonPath("$[0].dateCalcul").value("2026-04-01"));
    }

    @Test
    void un_trimestre_inconnu_rend_404_avec_le_corps_d_erreur() throws Exception {
        when(chargeur.exigerTrimestre(2099, 3))
                .thenThrow(new RessourceIntrouvableException("Aucun trimestre T3 2099"));

        mockMvc.perform(get("/api/trimestres/2099/3/scores"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statut").value(404))
                .andExpect(jsonPath("$.erreur").value("ressource_introuvable"))
                .andExpect(jsonPath("$.message").value("Aucun trimestre T3 2099"));
    }

    @Test
    void l_historique_d_un_employe_est_expose() throws Exception {
        when(chargeur.exigerEmploye("E001")).thenReturn(employe);
        when(scoreService.historique(employe)).thenReturn(List.of(score("90.00", "80.00", "Performant")));

        mockMvc.perform(get("/api/employes/E001/scores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value("E001"));
    }

    @Test
    void un_employe_inconnu_rend_404() throws Exception {
        when(chargeur.exigerEmploye(any()))
                .thenThrow(new RessourceIntrouvableException("Aucun employe E999"));

        mockMvc.perform(get("/api/employes/E999/scores"))
                .andExpect(status().isNotFound());
    }
}
