package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.TableauDeBordService;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TableauDeBordController.class)
class TableauDeBordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableauDeBordService tableauDeBordService;
    @MockBean
    private ChargeurRessources chargeur;

    @Test
    void la_synthese_est_exposee_telle_que_le_moteur_la_calcule() throws Exception {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);

        Map<NiveauVigilance, Integer> vigilance = new EnumMap<>(NiveauVigilance.class);
        vigilance.put(NiveauVigilance.FAIBLE, 55);
        vigilance.put(NiveauVigilance.MODEREE, 35);
        vigilance.put(NiveauVigilance.ELEVEE, 10);
        Map<String, Integer> repartition = new LinkedHashMap<>();
        repartition.put("Talent cle", 10);
        repartition.put("Performant", 15);
        Poste pst13 = new Poste();
        pst13.setPosteId("PST13");
        pst13.setNomPoste("Responsable Cybersecurite");

        when(tableauDeBordService.synthese(trimestre)).thenReturn(new SyntheseTableauDeBord(10, 21, vigilance,
                15, new BigDecimal("93.33"),
                List.of(new CouverturePoste(pst13, 0, List.of(), List.of(), NiveauCouverture.ALERTE)),
                repartition, 0));

        mockMvc.perform(get("/api/dashboard/synthese?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbTalents").value(10))
                .andExpect(jsonPath("$.nbHautsPotentiels").value(21))
                .andExpect(jsonPath("$.vigilanceParNiveau.ELEVEE").value(10))
                .andExpect(jsonPath("$.nbARisque").value(45))
                .andExpect(jsonPath("$.nbPostesCritiques").value(15))
                .andExpect(jsonPath("$.nbAlertesPostesCritiques").value(1))
                .andExpect(jsonPath("$.tauxCouverture").value(93.33))
                .andExpect(jsonPath("$.alertesPostesCritiques[0].posteId").value("PST13"))
                .andExpect(jsonPath("$.repartition9Box['Talent cle']").value(10))
                .andExpect(jsonPath("$.nbNonPlaces9Box").value(0));
    }

    @Test
    void un_trimestre_inconnu_rend_404() throws Exception {
        when(chargeur.exigerTrimestre(2030, 1)).thenThrow(new RessourceIntrouvableException("Aucun trimestre T1 2030"));

        mockMvc.perform(get("/api/dashboard/synthese?annee=2030&numero=1"))
                .andExpect(status().isNotFound());
    }
}
