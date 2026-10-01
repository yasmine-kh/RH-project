package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WithMockUser(roles = "RH")
@WebMvcTest(ComiteTalentController.class)
class ComiteTalentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidationComiteService validationComiteService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
    }

    @Test
    void les_decisions_exposent_le_statut_et_la_validation() throws Exception {
        when(validationComiteService.getDecisionsComite(trimestre)).thenReturn(List.of(
                new DecisionComite(score("BP005", "Reseau Retail"), StatutValidationComite.OUI),
                new DecisionComite(score("BP019", "Reseau Retail"), StatutValidationComite.EN_ATTENTE)));

        mockMvc.perform(get("/api/comite-talent?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idCollaborateur").value("BP005"))
                .andExpect(jsonPath("$[0].direction").value("Reseau Retail"))
                .andExpect(jsonPath("$[0].statut").value("OUI"))
                .andExpect(jsonPath("$[0].talentValide").value(true))
                .andExpect(jsonPath("$[1].statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$[1].statutLibelle").value("En attente"))
                .andExpect(jsonPath("$[1].talentValide").value(false));
    }

    @Test
    void les_talents_valides_sont_exposes() throws Exception {
        when(validationComiteService.getTalentsValides(trimestre)).thenReturn(List.of(score("BP005", "Reseau Retail")));

        mockMvc.perform(get("/api/comite-talent/talents-valides?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idCollaborateur").value("BP005"))
                .andExpect(jsonPath("$[0].scorePerformance").value(95.00));
    }

    @Test
    void un_trimestre_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestre(2030, 1)).thenThrow(new RessourceIntrouvableException("Aucun trimestre T1 2030"));

        mockMvc.perform(get("/api/comite-talent?annee=2030&numero=1"))
                .andExpect(status().isNotFound());
    }

    private Score score(String idCollaborateur, String direction) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(idCollaborateur);
        collaborateur.setNom("Nom" + idCollaborateur);
        collaborateur.setPrenom("Prenom" + idCollaborateur);
        collaborateur.setEntite(direction == null ? null : new Entite(direction, TypeEntite.DIRECTION, null));
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.figerOrganisation();
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal("95.00"));
        score.setScorePotentiel(new BigDecimal("92.00"));
        return score;
    }
}
