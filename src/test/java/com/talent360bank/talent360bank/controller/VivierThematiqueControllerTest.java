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
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WithMockUser(roles = "RH")
@WebMvcTest(VivierThematiqueController.class)
class VivierThematiqueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VivierThematiqueService vivierThematiqueService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private List<MembreVivierThematique> commercial;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);

        commercial = List.of(
                new MembreVivierThematique(score(collaborateur("BP005", "Reseau Retail")), true, true),
                new MembreVivierThematique(score(collaborateur("BP026", "Corporate Banking")), false, true),
                new MembreVivierThematique(score(collaborateur("BP001", "Reseau Retail")), false, false));
    }

    @Test
    void tous_les_viviers_sont_exposes_avec_leurs_effectifs() throws Exception {
        Map<VivierThematique, List<MembreVivierThematique>> membres = new EnumMap<>(VivierThematique.class);
        for (VivierThematique vivier : VivierThematique.values()) {
            membres.put(vivier, List.of());
        }
        membres.put(VivierThematique.COMMERCIAL, commercial);
        when(vivierThematiqueService.getViviersThematiques(trimestre)).thenReturn(
                new ResultatViviersThematiques(membres, List.of(collaborateur("BP200", "Direction inconnue"))));

        mockMvc.perform(get("/api/viviers-thematiques?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viviers.length()").value(5))
                .andExpect(jsonPath("$.viviers[0].code").value("COMMERCIAL"))
                .andExpect(jsonPath("$.viviers[0].libelle").value("Vivier Commercial"))
                .andExpect(jsonPath("$.viviers[0].nbMembres").value(3))
                .andExpect(jsonPath("$.viviers[0].nbTalents").value(1))
                .andExpect(jsonPath("$.viviers[0].nbHautsPotentiels").value(2))
                .andExpect(jsonPath("$.viviers[0].membres[1].idCollaborateur").value("BP026"))
                .andExpect(jsonPath("$.viviers[0].membres[1].direction").value("Corporate Banking"))
                .andExpect(jsonPath("$.viviers[0].membres[1].hautPotentiel").value(true))
                .andExpect(jsonPath("$.viviers[1].code").value("DIGITAL"))
                .andExpect(jsonPath("$.viviers[1].nbMembres").value(0))
                .andExpect(jsonPath("$.nonClasses[0].idCollaborateur").value("BP200"));
    }

    @Test
    void un_vivier_se_demande_par_son_code_sans_tenir_compte_de_la_casse() throws Exception {
        when(vivierThematiqueService.getVivier(VivierThematique.COMMERCIAL, trimestre)).thenReturn(commercial);

        mockMvc.perform(get("/api/viviers-thematiques/commercial?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMERCIAL"))
                .andExpect(jsonPath("$.nbMembres").value(3))
                .andExpect(jsonPath("$.membres[0].talent").value(true));
    }

    @Test
    void un_code_inconnu_rend_404() throws Exception {
        mockMvc.perform(get("/api/viviers-thematiques/FINANCE?annee=2026&numero=3"))
                .andExpect(status().isNotFound());
    }

    private Collaborateur collaborateur(String idCollaborateur, String direction) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(idCollaborateur);
        collaborateur.setNom("Nom" + idCollaborateur);
        collaborateur.setPrenom("Prenom" + idCollaborateur);
        collaborateur.setEntite(direction == null ? null : new Entite(direction, TypeEntite.DIRECTION, null));
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        return collaborateur;
    }

    private Score score(Collaborateur collaborateur) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.figerOrganisation();
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal("90.00"));
        score.setScorePotentiel(new BigDecimal("90.00"));
        return score;
    }
}
