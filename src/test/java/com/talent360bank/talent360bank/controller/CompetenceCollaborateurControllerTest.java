package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompetenceCollaborateurController.class)
class CompetenceCollaborateurControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompetenceCollaborateurService competenceCollaborateurService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private Collaborateur collaborateur;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("BP001");
    }

    private GapCompetence competence() {
        Competence competence = new Competence();
        competence.setCompetenceId("C01");
        competence.setNom("Credit");
        competence.setCategorie("Metier");
        CompetenceCollaborateur skill = new CompetenceCollaborateur();
        skill.setCompetence(competence);
        skill.setNiveauActuel(1);
        skill.setNiveauCible(3);
        return new GapCompetence(skill, 2, StatutGapCompetence.PRIORITAIRE);
    }

    @Test
    void les_competences_d_un_collaborateur_portent_gap_et_statut() throws Exception {
        when(chargeur.exigerTrimestreOuDernier(2026, 3)).thenReturn(trimestre);
        when(chargeur.exigerCollaborateur("BP001")).thenReturn(collaborateur);
        when(competenceCollaborateurService.competences(collaborateur, trimestre)).thenReturn(List.of(competence()));

        mockMvc.perform(get("/api/collaborateurs/BP001/competences").param("annee", "2026").param("numero", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competenceId").value("C01"))
                .andExpect(jsonPath("$[0].competence").value("Credit"))
                .andExpect(jsonPath("$[0].categorie").value("Metier"))
                .andExpect(jsonPath("$[0].niveauActuel").value(1))
                .andExpect(jsonPath("$[0].niveauCible").value(3))
                .andExpect(jsonPath("$[0].gap").value(2))
                .andExpect(jsonPath("$[0].statutGap").value("PRIORITAIRE"))
                .andExpect(jsonPath("$[0].statutGapLibelle").value("Prioritaire"));
    }

    @Test
    void sans_trimestre_le_plus_recent_est_demande() throws Exception {
        when(chargeur.exigerTrimestreOuDernier(null, null)).thenReturn(trimestre);
        when(chargeur.exigerCollaborateur("BP001")).thenReturn(collaborateur);
        when(competenceCollaborateurService.competences(collaborateur, trimestre)).thenReturn(List.of(competence()));

        mockMvc.perform(get("/api/collaborateurs/BP001/competences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statutGap").value("PRIORITAIRE"));
    }

    @Test
    void un_seul_des_deux_parametres_rend_400() throws Exception {
        when(chargeur.exigerTrimestreOuDernier(2026, null))
                .thenThrow(new IllegalArgumentException("annee et numero vont ensemble"));

        mockMvc.perform(get("/api/collaborateurs/BP001/competences").param("annee", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("argument_invalide"));
    }

    @Test
    void un_collaborateur_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestreOuDernier(null, null)).thenReturn(trimestre);
        when(chargeur.exigerCollaborateur("BP999"))
                .thenThrow(new RessourceIntrouvableException("Aucun collaborateur BP999"));

        mockMvc.perform(get("/api/collaborateurs/BP999/competences"))
                .andExpect(status().isNotFound());
    }
}
