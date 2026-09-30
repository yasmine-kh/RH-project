package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.CategoriePotentiel;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VivierReleveService;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WithMockUser(roles = "RH")
@WebMvcTest({TalentController.class, NeufBoxController.class})
class TalentEtNeufBoxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TalentService talentService;
    @MockBean
    private NeufBoxService neufBoxService;
    @MockBean
    private VivierReleveService vivierReleveService;
    @MockBean
    private ChargeurRessources chargeur;

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

    private Score score() {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal("92.00"));
        score.setScorePotentiel(new BigDecimal("88.00"));
        score.setPositionBox("Talent cle");
        score.setCategoriePerformance(CategoriePerformance.EXCEPTIONNELLE);
        score.setCategoriePotentiel(CategoriePotentiel.ELEVE);
        return score;
    }

    @Test
    void les_talents_du_trimestre_sont_exposes() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(talentService.detecterTalents(trimestre)).thenReturn(List.of(score()));

        mockMvc.perform(get("/api/trimestres/2026/1/talents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCollaborateur").value("E001"))
                .andExpect(jsonPath("$[0].scorePerformance").value(92.00))
                .andExpect(jsonPath("$[0].positionBox").value("Talent cle"))
                .andExpect(jsonPath("$[0].categoriePerformance").value("Exceptionnelle"))
                .andExpect(jsonPath("$[0].categoriePotentiel").value("Eleve"));
    }

    @Test
    void des_categories_pas_encore_posees_sont_rendues_nulles() throws Exception {
        Score sansCategorie = score();
        sansCategorie.setCategoriePerformance(null);
        sansCategorie.setCategoriePotentiel(null);
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(talentService.detecterTalents(trimestre)).thenReturn(List.of(sansCategorie));

        mockMvc.perform(get("/api/trimestres/2026/1/talents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoriePerformance").doesNotExist())
                .andExpect(jsonPath("$[0].categoriePotentiel").doesNotExist());
    }

    @Test
    void l_enregistrement_du_vivier_de_releve_rend_le_bilan() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(vivierReleveService.constituerViviers(trimestre)).thenReturn(new ResultatConstitutionVivier(
                3, List.of(new AppartenanceVivier(), new AppartenanceVivier()), List.of("E009")));

        mockMvc.perform(post("/api/trimestres/2026/1/vivier-releve").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbRemplaces").value(3))
                .andExpect(jsonPath("$.nbEcrits").value(2))
                .andExpect(jsonPath("$.dejaPresents[0]").value("E009"));
    }

    @Test
    void le_statut_de_talent_d_un_collaborateur_est_un_objet() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(chargeur.exigerCollaborateur("E001")).thenReturn(collaborateur);
        when(talentService.estTalent(collaborateur, trimestre)).thenReturn(true);

        mockMvc.perform(get("/api/trimestres/2026/1/talents/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCollaborateur").value("E001"))
                .andExpect(jsonPath("$.estTalent").value(true));
    }

    @Test
    void les_hauts_potentiels_du_trimestre_sont_exposes() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(talentService.detecterHautsPotentiels(trimestre)).thenReturn(List.of(score()));

        mockMvc.perform(get("/api/trimestres/2026/1/hauts-potentiels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCollaborateur").value("E001"))
                .andExpect(jsonPath("$[0].scorePotentiel").value(88.00));
    }

    @Test
    void le_statut_de_haut_potentiel_d_un_collaborateur_est_un_objet() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(chargeur.exigerCollaborateur("E001")).thenReturn(collaborateur);
        when(talentService.estHautPotentiel(collaborateur, trimestre)).thenReturn(true);

        mockMvc.perform(get("/api/trimestres/2026/1/hauts-potentiels/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCollaborateur").value("E001"))
                .andExpect(jsonPath("$.estHautPotentiel").value(true));
    }

    @Test
    void le_vivier_de_releve_expose_la_raison_de_chaque_membre() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(talentService.getVivierReleve(trimestre))
                .thenReturn(List.of(new MembreVivierReleve(score(), true, true)));

        mockMvc.perform(get("/api/trimestres/2026/1/vivier-releve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCollaborateur").value("E001"))
                .andExpect(jsonPath("$[0].nomComplet").isNotEmpty())
                .andExpect(jsonPath("$[0].talent").value(true))
                .andExpect(jsonPath("$[0].hautPotentiel").value(true));
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
                List.of(new ResultatRecalcul.CollaborateurIgnore("E002", "Score incomplet"))));

        mockMvc.perform(post("/api/trimestres/2026/1/9box/placement").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
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
