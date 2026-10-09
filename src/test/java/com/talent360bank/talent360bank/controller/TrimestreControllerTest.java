package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import com.talent360bank.talent360bank.service.resultat.ResultatCreationTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WithMockUser(roles = "RH")
@WebMvcTest(TrimestreController.class)
class TrimestreControllerTest {

    private static final String EN_TETE = ProtectionRequetesFilter.EN_TETE_ECRITURE;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrimestreService trimestreService;
    @MockitoBean
    private com.talent360bank.talent360bank.service.JournalService journalService;
    @MockitoBean
    private CalculTrimestreService calculTrimestreService;
    @MockitoBean
    private TrimestreRepository trimestreRepository;
    @MockitoBean
    private ParametreRepository parametreRepository;
    @MockitoBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(3);
    }

    @Test
    void la_creation_rend_201_puis_200_quand_le_trimestre_existe() throws Exception {
        when(trimestreService.creerSiAbsent(2026, 3, null))
                .thenReturn(new ResultatCreationTrimestre(trimestre, true, true))
                .thenReturn(new ResultatCreationTrimestre(trimestre, false, false));

        mockMvc.perform(post("/api/trimestres").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annee\":2026,\"numero\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("T3 2026"))
                .andExpect(jsonPath("$.dateReference").value("2026-09-30"))
                .andExpect(jsonPath("$.trimestreCree").value(true))
                .andExpect(jsonPath("$.parametreCree").value(true));

        mockMvc.perform(post("/api/trimestres").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annee\":2026,\"numero\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trimestreCree").value(false));
    }

    @Test
    void la_creation_transmet_la_date_de_reference_choisie() throws Exception {
        trimestre.setDateReference(LocalDate.of(2026, 9, 15));
        when(trimestreService.creerSiAbsent(2026, 3, LocalDate.of(2026, 9, 15)))
                .thenReturn(new ResultatCreationTrimestre(trimestre, true, true));

        mockMvc.perform(post("/api/trimestres").header(EN_TETE, "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annee\":2026,\"numero\":3,\"dateReference\":\"2026-09-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateReference").value("2026-09-15"));
    }

    @Test
    void le_rh_change_la_date_de_reference_d_un_trimestre() throws Exception {
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
        when(trimestreService.modifierDateReference(trimestre, LocalDate.of(2026, 9, 15))).thenAnswer(appel -> {
            trimestre.setDateReference(LocalDate.of(2026, 9, 15));
            return trimestre;
        });

        mockMvc.perform(put("/api/trimestres/2026/3").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dateReference\":\"2026-09-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("T3 2026"))
                .andExpect(jsonPath("$.dateReference").value("2026-09-15"));
    }

    @Test
    void changer_la_date_de_reference_exige_une_date() throws Exception {
        mockMvc.perform(put("/api/trimestres/2026/3").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"));
        mockMvc.perform(put("/api/trimestres/2026/3").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dateReference\":\"15/09/2026\"}"))
                .andExpect(status().isBadRequest());
        verify(trimestreService, never()).modifierDateReference(any(), any());
    }

    @Test
    void changer_la_date_d_un_trimestre_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestre(2031, 1)).thenThrow(new RessourceIntrouvableException("Aucun trimestre T1 2031"));

        mockMvc.perform(put("/api/trimestres/2031/1").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dateReference\":\"2031-03-15\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_numero_hors_bornes_rend_400_sans_appel_au_service() throws Exception {
        mockMvc.perform(post("/api/trimestres").header(EN_TETE, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annee\":2026,\"numero\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"));

        verify(trimestreService, never()).creerSiAbsent(anyInt(), anyInt(), any());
    }

    @Test
    void la_creation_sans_en_tete_d_ecriture_est_refusee() throws Exception {
        mockMvc.perform(post("/api/trimestres")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annee\":2026,\"numero\":3}"))
                .andExpect(status().isForbidden());

        verify(trimestreService, never()).creerSiAbsent(anyInt(), anyInt(), any());
    }

    @Test
    void la_liste_indique_si_les_reglages_existent() throws Exception {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(trimestre));
        when(parametreRepository.existsByTrimestre(trimestre)).thenReturn(true);

        mockMvc.perform(get("/api/trimestres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].annee").value(2026))
                .andExpect(jsonPath("$[0].numero").value(3))
                .andExpect(jsonPath("$[0].dateReference").value("2026-09-30"))
                .andExpect(jsonPath("$[0].reglages").value(true));
    }

    @Test
    void le_calcul_rend_le_bilan_des_trois_etapes() throws Exception {
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
        when(calculTrimestreService.calculer(trimestre)).thenReturn(new ResultatCalculTrimestre(
                new ResultatRecalcul(List.of(), List.of(new ResultatRecalcul.CollaborateurIgnore("E1", "Aucune note"))),
                new ResultatRecalcul(List.of(), List.of()),
                new ResultatConstitutionVivier(1, List.of(), List.of("E9"))));

        mockMvc.perform(post("/api/trimestres/2026/3/calcul").header(EN_TETE, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scores.nombreIgnores").value(1))
                .andExpect(jsonPath("$.scores.ignores[0].idCollaborateur").value("E1"))
                .andExpect(jsonPath("$.placement9Box.nombreCalcules").value(0))
                .andExpect(jsonPath("$.vivierReleve.nbRemplaces").value(1))
                .andExpect(jsonPath("$.vivierReleve.dejaPresents[0]").value("E9"));
    }

    @Test
    void le_calcul_d_un_trimestre_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestre(2031, 1)).thenThrow(new RessourceIntrouvableException("Aucun trimestre T1 2031"));

        mockMvc.perform(post("/api/trimestres/2031/1/calcul").header(EN_TETE, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Aucun trimestre T1 2031"));
    }
}
