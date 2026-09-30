package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Alerte;
import com.talent360bank.talent360bank.ui.model.VueManager.Compte;
import com.talent360bank.talent360bank.ui.model.VueManager.CompteCase;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerVue;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import com.talent360bank.talent360bank.ui.model.VueManager.Synthese;
import com.talent360bank.talent360bank.ui.service.VueManagerViewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat JSON de la vue manager et de la liste des managers, tel que la page de Ima le lira. */
@Import(SecurityConfig.class)
@WebMvcTest(VueManagerController.class)
class VueManagerControllerTest {

    private static final String LISTE = "/api/trimestres/2026/3/managers";
    private static final String VUE = "/api/trimestres/2026/3/managers/BP005/vue";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VueManagerViewService service;

    private static final EntiteFiche AGENCE = new EntiteFiche("DIR:RESEAU_RETAIL/AGE:TANGER", "Tanger", "AGENCE",
            List.of("Reseau Retail", "Tanger"));

    private static VueManager vue() {
        Membre membre = new Membre("BP011", "Tazi", "Nadia", "Conseillere", new BigDecimal("81.20"), "ELEVEE",
                "Elevee", new BigDecimal("77.00"), "MOYEN", "Moyen", new CaseNeufBox(5, "Confirmé"), false, false,
                false, new BigDecimal("48.00"), new BigDecimal("65.00"), "ELEVEE", "Elevee", true);
        return new VueManager(
                new TrimestreFiche(2026, 3, "T3 2026", LocalDate.of(2026, 9, 15)),
                new ManagerVue("BP005", "Chraibi", "Meryem", "Directeur regional", AGENCE,
                        new CaseNeufBox(9, "Talent clé")),
                new Synthese(1, 1, new BigDecimal("81.20"), new BigDecimal("77.00"),
                        List.of(new Compte("ELEVEE", "Elevee", 1)), List.of(new Compte("MOYEN", "Moyen", 1)),
                        List.of(new CompteCase(5, "Confirmé", 1)), 0, 0, 0, new BigDecimal("48.00"),
                        List.of(new Compte("ELEVEE", "Elevee", 1)), 0),
                List.of(membre),
                List.of(new Alerte("BP011", "Nadia Tazi", "VIGILANCE_ELEVEE", "Vigilance élevée : indice 65 / 100")),
                null,
                List.of());
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_liste_des_managers_est_rendue_en_json() throws Exception {
        when(service.listerManagers(2026, 3)).thenReturn(List.of(
                new ManagerResume("BP005", "Chraibi", "Meryem", "Directeur regional", AGENCE, 5)));

        mockMvc.perform(get(LISTE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].matricule").value("BP005"))
                .andExpect(jsonPath("$[0].entite.chemin[0]").value("Reseau Retail"))
                .andExpect(jsonPath("$[0].tailleEquipe").value(5));
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_vue_est_rendue_en_json_avec_tous_ses_blocs() throws Exception {
        when(service.construire("BP005", 2026, 3)).thenReturn(vue());

        mockMvc.perform(get(VUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trimestre.libelle").value("T3 2026"))
                .andExpect(jsonPath("$.manager.matricule").value("BP005"))
                .andExpect(jsonPath("$.manager.neufBox.numero").value(9))
                .andExpect(jsonPath("$.synthese.effectif").value(1))
                .andExpect(jsonPath("$.synthese.moyennePerformance").value(81.20))
                .andExpect(jsonPath("$.synthese.categoriesPerformance[0].code").value("ELEVEE"))
                .andExpect(jsonPath("$.synthese.neufBox[0].numero").value(5))
                .andExpect(jsonPath("$.synthese.niveauxVigilance[0].nombre").value(1))
                .andExpect(jsonPath("$.synthese.nbSansVigilance").value(0))
                .andExpect(jsonPath("$.membres[0].matricule").value("BP011"))
                .andExpect(jsonPath("$.membres[0].neufBox.libelle").value("Confirmé"))
                .andExpect(jsonPath("$.membres[0].niveauVigilance").value("ELEVEE"))
                .andExpect(jsonPath("$.membres[0].aDesDonnees").value(true))
                .andExpect(jsonPath("$.alertes[0].type").value("VIGILANCE_ELEVEE"))
                .andExpect(jsonPath("$.autoVsManager").isEmpty())
                .andExpect(jsonPath("$.donneesManquantes").isArray());
    }

    @Test
    @WithMockUser(roles = "RH")
    void un_collaborateur_qui_n_est_pas_manager_rend_404() throws Exception {
        when(service.construire("BP005", 2026, 3))
                .thenThrow(new RessourceIntrouvableException("Le collaborateur BP005 n'est pas manager"));

        mockMvc.perform(get(VUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Le collaborateur BP005 n'est pas manager"));
    }

    @Test
    void sans_connexion_l_api_rend_401() throws Exception {
        mockMvc.perform(get(LISTE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(VUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erreur").value("non_authentifie"));
        verifyNoInteractions(service);
    }
}
