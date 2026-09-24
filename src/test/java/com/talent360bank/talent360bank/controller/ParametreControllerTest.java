package com.talent360bank.talent360bank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talent360bank.talent360bank.controller.dto.ParametreForm;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParametreController.class)
class ParametreControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParametreRepository parametreRepository;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        parametre = Parametre.parDefaut(trimestre);
    }

    private ParametreForm formDepuis(Parametre source) {
        return new ParametreForm("Reglages revus",
                source.getPoidsSources(), source.getPoidsPerformance(), source.getPoidsPotentiel(),
                source.getPoidsSuccession(), source.getBaremeExperience(), source.getSeuilsNeufBox(), source.getSeuilsReadiness(),
                source.getSeuilsTalent(), source.getPointsVigilance(), source.getSeuilsVigilance());
    }

    @Test
    void la_lecture_aplatit_le_trimestre_et_rend_les_dix_blocs() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        mockMvc.perform(get("/api/trimestres/2026/1/parametre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annee").value(2026))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.poidsSuccession.poidsCompetences").value(25))
                .andExpect(jsonPath("$.baremeExperience.pointsParAnnee").value(8))
                .andExpect(jsonPath("$.baremeExperience.plafond").value(100))
                .andExpect(jsonPath("$.seuilsReadiness.seuilReadyNow").value(90))
                .andExpect(jsonPath("$.seuilsVigilance.seuilEngagementFaible").value(60))
                .andExpect(jsonPath("$.pointsVigilance.pointEngagementFaible").value(25));
    }

    @Test
    void un_trimestre_sans_reglages_rend_404() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(3, 2026)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/trimestres/2026/3/parametre"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erreur").value("ressource_introuvable"));
    }

    @Test
    void la_creation_pose_les_valeurs_par_defaut_de_la_specification() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(parametreRepository.existsByTrimestre(trimestre)).thenReturn(false);
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        mockMvc.perform(post("/api/trimestres/2026/1/parametre"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poidsPerformance.poidsObjectifs").value(40))
                .andExpect(jsonPath("$.seuilsNeufBox.seuilEleve").value(85));
    }

    @Test
    void creer_des_reglages_deja_presents_rend_409() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(parametreRepository.existsByTrimestre(trimestre)).thenReturn(true);

        mockMvc.perform(post("/api/trimestres/2026/1/parametre"))
                .andExpect(status().isConflict());

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void la_mise_a_jour_remplace_les_dix_blocs() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsTalent().setSeuilPerformance(new BigDecimal("80"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Reglages revus"))
                .andExpect(jsonPath("$.seuilsTalent.seuilPerformance").value(80));
    }

    @Test
    void un_bloc_de_poids_qui_ne_fait_pas_cent_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getPoidsPerformance().setPoidsObjectifs(new BigDecimal("50"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"));

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void une_regle_inter_blocs_violee_rend_400_avec_le_detail() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        // Chaque bloc reste valide isolement : seul leur croisement ne l'est
        // pas, un seuil eleve a 500 depassant le total des points attribuables.
        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsVigilance().setSeuilEleve(new BigDecimal("500"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("reglages_invalides"))
                .andExpect(jsonPath("$.details.length()").value(1));

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void un_bloc_absent_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"incomplet\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"));
    }

    @Test
    void un_corps_illisible_rend_400_et_non_500() throws Exception {
        mockMvc.perform(put("/api/trimestres/2026/1/parametre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ceci n'est pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"));
    }
}
