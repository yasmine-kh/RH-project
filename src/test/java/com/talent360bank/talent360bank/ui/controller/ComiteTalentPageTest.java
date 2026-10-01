package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import org.springframework.security.test.context.support.WithMockUser;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.CategoriePotentiel;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.ui.service.ComiteTalentViewService;
import com.talent360bank.talent360bank.ui.service.DashboardService;
import com.talent360bank.talent360bank.ui.service.NineBoxViewService;
import com.talent360bank.talent360bank.ui.service.PosteCritiqueViewService;
import com.talent360bank.talent360bank.ui.service.VivierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Rendu reel du template comite-talent.html, avec le vrai service de vue :
 * seuls le moteur et le depot des trimestres sont simules.
 */
@WebMvcTest(PagesController.class)
@Import({ComiteTalentViewService.class, SecurityConfig.class})
@WithMockUser(roles = "RH")
class ComiteTalentPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidationComiteService validationComiteService;
    @MockBean
    private TrimestreRepository trimestreRepository;
    @MockBean
    private DashboardService dashboardService;
    @MockBean
    private NineBoxViewService nineBoxViewService;
    @MockBean
    private VivierService vivierService;
    @MockBean
    private PosteCritiqueViewService posteCritiqueViewService;

    private Trimestre t3;

    @BeforeEach
    void init() {
        t3 = new Trimestre();
        t3.setNumero(3);
        t3.setAnnee(2026);
    }

    private DecisionComite decision(String prenom, StatutValidationComite statut) {
        Collaborateur collaborateur = new Collaborateur();