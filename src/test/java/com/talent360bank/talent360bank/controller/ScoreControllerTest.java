package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.ScoreService;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Import({SecurityConfig.class, VerrouCalculTrimestre.class})
@WithMockUser(roles = "RH")
@WebMvcTest(ScoreController.class)
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoreService scoreService;
    @MockitoBean
    private ScoreRepository scoreRepository;
    @MockitoBean
    private ChargeurRessources chargeur;
    @Autowired
    private VerrouCalculTrimestre verrou;

    private Trimestre trimestre;
    private Collaborateur collaborateur;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);

        collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("E001");
        collaborateur.setNom("Bennani");
        collaborateur.setPrenom("Sara");
        collaborateur.setDateEntree(LocalDate.of(2020, 1, 15));
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
    }

    private Score score(String performance, String potentiel, String box) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
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
                List.of(new ResultatRecalcul.CollaborateurIgnore("E002", "Notes de potentiel absentes"))));

        mockMvc.perform(post("/api/trimestres/2026/1/scores/recalcul").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCalcules").value(1))
                .andExpect(jsonPath("$.nombreIgnores").value(1))
                .andExpect(jsonPath("$.scores[0].idCollaborateur").value("E001"))
                .andExpect(jsonPath("$.scores[0].nomComplet").value("Sara Bennani"))
                .andExpect(jsonPath("$.scores[0].scorePerformance").value(90.00))
                .andExpect(jsonPath("$.ignores[0].idCollaborateur").value("E002"))
                .andExpect(jsonPath("$.ignores[0].motif").value("Notes de potentiel absentes"));
    }

    @Test
    void les_scores_du_trimestre_sont_aplatis() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(scoreRepository.findByTrimestreAvecCollaborateur(trimestre))
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
    void l_historique_d_un_collaborateur_est_expose() throws Exception {
        when(chargeur.exigerCollaborateur("E001")).thenReturn(collaborateur);
        when(scoreService.historique(collaborateur)).thenReturn(List.of(score("90.00", "80.00", "Performant")));

        mockMvc.perform(get("/api/collaborateurs/E001/scores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCollaborateur").value("E001"));
    }

    @Test
    void un_collaborateur_inconnu_rend_404() throws Exception {
        when(chargeur.exigerCollaborateur(any()))
                .thenThrow(new RessourceIntrouvableException("Aucun collaborateur E999"));

        mockMvc.perform(get("/api/collaborateurs/E999/scores"))
                .andExpect(status().isNotFound());
    }

    @Test
    @SuppressWarnings("try") // le bloc tient le verrou du calcul, la variable n'est pas utilisee
    void le_recalcul_des_scores_rend_409_pendant_un_calcul_du_trimestre() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);

        try (VerrouTenu calculEnCours = VerrouTenu.tenir(verrou, trimestre)) {
            mockMvc.perform(post("/api/trimestres/2026/1/scores/recalcul").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.erreur").value("recalcul_en_cours"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                            "Recalcul déjà en cours pour T1 2026")));
        }
        verify(scoreService, never()).recalculerTrimestre(any());
    }
}
