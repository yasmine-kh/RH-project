package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.service.PosteCibleService;
import com.talent360bank.talent360bank.service.VivierSyntheseService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat JSON des postes cibles et des syntheses de viviers, tel que les ecrans de Ima le liront. */
@Import(SecurityConfig.class)
@WebMvcTest({PosteCibleController.class, VivierSyntheseController.class})
class PosteCibleEtVivierSyntheseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PosteCibleService posteCibleService;
    @MockBean
    private VivierSyntheseService vivierSyntheseService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        trimestre.setDateReference(LocalDate.of(2026, 9, 15));
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
    }

    @Test
    @WithMockUser(roles = "RH")
    void les_postes_cibles_exposent_poste_matching_readiness_et_plus_grand_gap() throws Exception {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("BP001");
        collaborateur.setNom("Bouzid");
        collaborateur.setPrenom("Hind");
        collaborateur.setDateEntree(LocalDate.of(2023, 2, 20));
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        Poste poste = new Poste();
        poste.setPosteId("PST08");
        poste.setNomPoste("Directeur Conformite");
        poste.setEntite(new Entite("Conformite", TypeEntite.DIRECTION, null));
        poste.setCriticite("Elevee");
        ResultatMatching matching = new ResultatMatching(collaborateur, new BigDecimal("64.82"),
                NiveauReadiness.PLUS_2_ANS, null, new PlusGrandGap("C11", "Conformite", 4, 2, 2));
        when(posteCibleService.postesCibles(trimestre))
                .thenReturn(List.of(new ResultatPosteCible(collaborateur, poste, matching, false)));

        mockMvc.perform(get("/api/trimestres/2026/3/postes-cibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].collaborateur.idCollaborateur").value("BP001"))
                .andExpect(jsonPath("$[0].posteId").value("PST08"))
                .andExpect(jsonPath("$[0].direction").value("Conformite"))
                .andExpect(jsonPath("$[0].scoreMatching").value(64.82))
                .andExpect(jsonPath("$[0].readiness").value("PLUS_2_ANS"))
                .andExpect(jsonPath("$[0].readinessLibelle").value(NiveauReadiness.PLUS_2_ANS.getLibelle()))
                .andExpect(jsonPath("$[0].successeurIdentifie").value(false))
                .andExpect(jsonPath("$[0].plusGrandGap.competence").value("Conformite"))
                .andExpect(jsonPath("$[0].plusGrandGap.ecart").value(2));
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_synthese_des_viviers_donne_une_ligne_par_vivier() throws Exception {
        when(vivierSyntheseService.synthese(trimestre)).thenReturn(List.of(
                new SyntheseVivier("EXPERTISE", "Vivier Expertise", false, 17, 1, 4, 1, new BigDecimal("76.04"),
                        new BigDecimal("79.84"), 3,
                        List.of(new SyntheseVivier.GapFrequent("C20", "Juridique bancaire", 3))),
                new SyntheseVivier("RELEVE", "Vivier de relève", true, 21, 10, 21, 11, new BigDecimal("84.73"),
                        new BigDecimal("90.94"), 12, List.of())));

        mockMvc.perform(get("/api/viviers/synthese?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("EXPERTISE"))
                .andExpect(jsonPath("$[0].effectif").value(17))
                .andExpect(jsonPath("$[0].nbHautsPotentiels").value(4))
                .andExpect(jsonPath("$[0].performanceMoyenne").value(76.04))
                .andExpect(jsonPath("$[0].gapsIdentifies[0].competence").value("Juridique bancaire"))
                .andExpect(jsonPath("$[0].gapsIdentifies[0].nombre").value(3))
                .andExpect(jsonPath("$[1].releve").value(true))
                .andExpect(jsonPath("$[1].nbReadyNow").value(11));
    }

    @Test
    void sans_connexion_les_deux_api_rendent_401() throws Exception {
        mockMvc.perform(get("/api/trimestres/2026/3/postes-cibles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/viviers/synthese?annee=2026&numero=3")).andExpect(status().isUnauthorized());
        verifyNoInteractions(posteCibleService, vivierSyntheseService);
    }
}
