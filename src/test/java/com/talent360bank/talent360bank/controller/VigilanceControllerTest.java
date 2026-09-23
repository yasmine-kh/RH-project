package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.NiveauVigilance;
import com.talent360bank.talent360bank.entity.SignalVigilance;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.ResultatVigilance;
import com.talent360bank.talent360bank.service.VigilanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VigilanceController.class)
class VigilanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VigilanceService vigilanceService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private Employe employe;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(2);
        trimestre.setAnnee(2026);

        employe = new Employe();
        employe.setEmployeeId("E001");
        employe.setNom("Bennani");
        employe.setPrenom("Sara");
        employe.setDateEntree(LocalDate.of(2020, 1, 15));
        employe.setStatut(StatutEmploye.ACTIF);
    }

    @Test
    void la_vigilance_expose_l_indice_le_niveau_et_les_signaux() throws Exception {
        ResultatVigilance resultat = new ResultatVigilance(employe, new BigDecimal("35.00"),
                NiveauVigilance.MODEREE,
                Set.of(SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.BAISSE_PERFORMANCE));

        when(chargeur.exigerTrimestre(2026, 2)).thenReturn(trimestre);
        when(vigilanceService.evaluerTrimestre(trimestre)).thenReturn(List.of(resultat));

        mockMvc.perform(get("/api/trimestres/2026/2/vigilance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employe.employeeId").value("E001"))
                .andExpect(jsonPath("$[0].indice").value(35.00))
                .andExpect(jsonPath("$[0].niveau").value("MODEREE"))
                .andExpect(jsonPath("$[0].niveauLibelle").value("Moderee"))
                .andExpect(jsonPath("$[0].aRisque").value(true))
                .andExpect(jsonPath("$[0].signaux.length()").value(2));
    }

    @Test
    void les_signaux_sortent_dans_l_ordre_de_declaration_quel_que_soit_l_ensemble() throws Exception {
        // Ensemble volontairement construit a l'envers : la reponse doit
        // rester stable, sinon l'UI reordonne les signaux sans raison.
        Set<SignalVigilance> desordre = new LinkedHashSet<>();
        desordre.add(SignalVigilance.FORMATION_NON_FAITE);
        desordre.add(SignalVigilance.BAISSE_PERFORMANCE);
        desordre.add(SignalVigilance.ENGAGEMENT_FAIBLE);

        when(chargeur.exigerTrimestre(2026, 2)).thenReturn(trimestre);
        when(vigilanceService.evaluerTrimestre(trimestre)).thenReturn(List.of(
                new ResultatVigilance(employe, new BigDecimal("40.00"),
                        NiveauVigilance.MODEREE, desordre)));

        mockMvc.perform(get("/api/trimestres/2026/2/vigilance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signaux[0].code").value("ENGAGEMENT_FAIBLE"))
                .andExpect(jsonPath("$[0].signaux[1].code").value("BAISSE_PERFORMANCE"))
                .andExpect(jsonPath("$[0].signaux[2].code").value("FORMATION_NON_FAITE"));
    }

    @Test
    void chaque_signal_dit_s_il_est_detectable_automatiquement() throws Exception {
        when(chargeur.exigerTrimestre(2026, 2)).thenReturn(trimestre);
        when(vigilanceService.evaluerTrimestre(trimestre)).thenReturn(List.of(
                new ResultatVigilance(employe, new BigDecimal("45.00"), NiveauVigilance.MODEREE,
                        Set.of(SignalVigilance.ENGAGEMENT_FAIBLE, SignalVigilance.SANS_MOBILITE_4_ANS))));

        mockMvc.perform(get("/api/trimestres/2026/2/vigilance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signaux[0].code").value("ENGAGEMENT_FAIBLE"))
                .andExpect(jsonPath("$[0].signaux[0].detectable").value(true))
                .andExpect(jsonPath("$[0].signaux[1].code").value("SANS_MOBILITE_4_ANS"))
                .andExpect(jsonPath("$[0].signaux[1].detectable").value(false));
    }

    @Test
    void le_filtre_par_niveau_minimum_est_transmis_au_service() throws Exception {
        when(chargeur.exigerTrimestre(2026, 2)).thenReturn(trimestre);
        when(vigilanceService.evaluerTrimestre(trimestre, NiveauVigilance.ELEVEE))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/trimestres/2026/2/vigilance?minimum=ELEVEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void un_niveau_minimum_inconnu_rend_400_et_non_500() throws Exception {
        mockMvc.perform(get("/api/trimestres/2026/2/vigilance?minimum=CATASTROPHIQUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"));
    }

    @Test
    void un_employe_sans_signal_est_rendu_non_a_risque() throws Exception {
        when(chargeur.exigerTrimestre(2026, 2)).thenReturn(trimestre);
        when(chargeur.exigerEmploye("E001")).thenReturn(employe);
        when(vigilanceService.evaluer(employe, trimestre)).thenReturn(
                new ResultatVigilance(employe, new BigDecimal("0.00"), NiveauVigilance.FAIBLE, Set.of()));

        mockMvc.perform(get("/api/trimestres/2026/2/vigilance/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aRisque").value(false))
                .andExpect(jsonPath("$.signaux.length()").value(0));
    }
}
