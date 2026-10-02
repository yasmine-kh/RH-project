package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.CompetenceSyntheseService;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.CompteNiveau;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Criteres;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Exigence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.LigneCompetence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.PosteCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier.GapFrequent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat JSON du module Competences, tel que l'ecran de Ima le lira. */
@Import(SecurityConfig.class)
@WebMvcTest(CompetenceSyntheseController.class)
class CompetenceSyntheseControllerTest {

    private static final String URL = "/api/trimestres/2026/3/competences";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompetenceSyntheseService service;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
    }

    private static SyntheseCompetences synthese(Criteres criteres) {
        LigneCompetence leadership = new LigneCompetence("C10", "Leadership", "Comportementale", 100,
                new BigDecimal("3.12"), new BigDecimal("4.05"), new BigDecimal("0.93"), 71, new BigDecimal("71.0"), 12,
                List.of(new CompteNiveau(1, 9), new CompteNiveau(2, 20), new CompteNiveau(3, 30),
                        new CompteNiveau(4, 25), new CompteNiveau(5, 16)));
        return new SyntheseCompetences(criteres, 100, List.of(leadership), List.of(leadership), 141,
                List.of(new PosteCompetences("PST01", "Directeur regional", 4,
                        List.of(new Exigence("C10", "Leadership", 5, new BigDecimal("5.00"), 0)),
                        List.of(new GapFrequent("C10", "Leadership", 1)))));
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_synthese_transmet_les_filtres_et_rend_lignes_top_et_postes() throws Exception {
        Criteres attendus = new Criteres("DIR:RESEAU_RETAIL", "RELEVE", "PST01", true, 3);
        when(service.synthese(trimestre, attendus)).thenReturn(synthese(attendus));

        mockMvc.perform(get(URL).param("entite", "DIR:RESEAU_RETAIL").param("vivier", "releve")
                        .param("poste", "pst01").param("ordre", "asc").param("top", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCollaborateurs").value(100))
                .andExpect(jsonPath("$.nbGapsPrioritaires").value(141))
                .andExpect(jsonPath("$.competences[0].competence").value("Leadership"))
                .andExpect(jsonPath("$.competences[0].gapMoyen").value(0.93))
                .andExpect(jsonPath("$.competences[0].pourcentageAvecGap").value(71.0))
                .andExpect(jsonPath("$.competences[0].repartition[4].niveau").value(5))
                .andExpect(jsonPath("$.topGaps[0].competenceId").value("C10"))
                .andExpect(jsonPath("$.postesCritiques[0].exigences[0].niveauRequis").value(5))
                .andExpect(jsonPath("$.postesCritiques[0].gapsFrequents[0].nombre").value(1))
                .andExpect(jsonPath("$.criteres.gapCroissant").value(true));
    }

    @Test
    @WithMockUser(roles = "RH")
    void sans_parametre_l_ordre_est_decroissant_et_le_top_vaut_5() throws Exception {
        Criteres defaut = new Criteres(null, null, null, false, SyntheseCompetences.TOP_PAR_DEFAUT);
        when(service.synthese(trimestre, defaut)).thenReturn(synthese(defaut));

        mockMvc.perform(get(URL)).andExpect(status().isOk());
        verify(service).synthese(trimestre, defaut);
    }

    @Test
    @WithMockUser(roles = "RH")
    void un_ordre_inconnu_ou_un_critere_refuse_rend_400() throws Exception {
        mockMvc.perform(get(URL).param("ordre", "haut"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("argument_invalide"));

        when(service.synthese(eq(trimestre), any()))
                .thenThrow(new IllegalArgumentException("Poste critique inconnu : PST99"));
        mockMvc.perform(get(URL).param("poste", "PST99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Poste critique inconnu : PST99"));
    }

    @Test
    @WithMockUser(roles = "RH")
    void un_trimestre_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestre(2030, 1)).thenThrow(new RessourceIntrouvableException("Aucun trimestre"));
        mockMvc.perform(get("/api/trimestres/2030/1/competences")).andExpect(status().isNotFound());
    }

    @Test
    void sans_connexion_l_api_rend_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
