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
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.ui.service.ComiteTalentViewService;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurPageViewService;
import com.talent360bank.talent360bank.ui.service.NineBoxViewService;
import com.talent360bank.talent360bank.ui.service.PosteCritiqueViewService;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import com.talent360bank.talent360bank.ui.service.VivierService;
import org.junit.jupiter.api.BeforeEach;
import com.talent360bank.talent360bank.service.VivierSyntheseService;
import com.talent360bank.talent360bank.ui.service.ProfilsService;
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
@Import({ComiteTalentViewService.class, TrimestreCourantService.class, SecurityConfig.class})
@WithMockUser(roles = "RH")
class ComiteTalentPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidationComiteService validationComiteService;
    @MockBean
    private TrimestreRepository trimestreRepository;
    @MockBean
    private ScoreRepository scoreRepository;
    @MockBean
    private NineBoxViewService nineBoxViewService;
    @MockBean
    private VivierService vivierService;
    @MockBean
    private PosteCritiqueViewService posteCritiqueViewService;
    @MockBean
    private FicheCollaborateurPageViewService ficheCollaborateurPageViewService;
    @MockBean
    private VivierSyntheseService vivierSyntheseService;
    @MockBean
    private com.talent360bank.talent360bank.ui.service.ReponsesQuestionnaireViewService reponsesQuestionnaireViewService;
    @MockBean
    private ProfilsService profilsService;
    @MockBean
    private com.talent360bank.talent360bank.service.PosteCritiqueService posteCritiqueService;
    @MockBean
    private com.talent360bank.talent360bank.repository.ValidationComiteRepository validationComiteRepository;
    @MockBean
    private com.talent360bank.talent360bank.repository.ValidationSuccessionRepository validationSuccessionRepository;
    @MockBean
    private com.talent360bank.talent360bank.service.RevuesComiteService revuesComiteService;
    @MockBean
    private com.talent360bank.talent360bank.ui.service.HistoriqueCollaborateurViewService historiqueCollaborateurViewService;
    @MockBean
    private com.talent360bank.talent360bank.service.ConfigurationQuestionnaireService configurationQuestionnaireService;

    private Trimestre t3;

    @BeforeEach
    void init() {
        t3 = new Trimestre();
        t3.setNumero(3);
        t3.setAnnee(2026);
    }

    private DecisionComite decision(String prenom, StatutValidationComite statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("ID-" + prenom);
        collaborateur.setPrenom(prenom);
        collaborateur.setNom("Nom");
        collaborateur.setEntite(new Entite("Corporate Banking", TypeEntite.DIRECTION, null));
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.figerOrganisation();
        score.setScorePerformance(new BigDecimal("94.60"));
        score.setCategoriePerformance(CategoriePerformance.EXCEPTIONNELLE);
        score.setScorePotentiel(new BigDecimal("90.95"));
        score.setCategoriePotentiel(CategoriePotentiel.ELEVE);
        score.setPositionBox("Talent clé");
        return new DecisionComite(score, statut);
    }

    private void unTalentValideEtUnEnAttente() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t3));
        when(validationComiteService.getDecisionsComite(t3)).thenReturn(List.of(
                decision("Ilham", StatutValidationComite.OUI),
                decision("Ghita", StatutValidationComite.EN_ATTENTE)));
    }

    @Test
    void la_page_affiche_kpi_filtres_et_tableau() throws Exception {
        unTalentValideEtUnEnAttente();

        mockMvc.perform(get("/comite-talent"))
                .andExpect(status().isOk())
                .andExpect(view().name("comite-talent"))
                .andExpect(content().string(allOf(
                        containsString("Talents proposés en attente"),
                        containsString("T3 2026"),
                        containsString("Ilham Nom"),
                        containsString("Corporate Banking"),
                        containsString("94.60"),
                        containsString("Exceptionnelle"),
                        containsString("90.95"),
                        containsString("Talent clé"),
                        containsString("data-statut=\"OUI\""),
                        containsString("data-statut=\"EN_ATTENTE\""),
                        containsString("Tous (2)"),
                        containsString("Oui (1)"),
                        // Pas la page "en cours de developpement" (son bandeau), meme si les decisions y sont grisees.
                        not(containsString("<div class=\"sev\">EN COURS</div>")))));
    }

    @Test
    void le_lien_de_la_sidebar_est_actif() throws Exception {
        unTalentValideEtUnEnAttente();

        mockMvc.perform(get("/comite-talent"))
                .andExpect(content().string(containsString("href=\"/comite-talent\" class=\"navitem active\"")));
    }

    @Test
    void le_filtre_de_statut_est_applique_et_reste_selectionne() throws Exception {
        unTalentValideEtUnEnAttente();

        mockMvc.perform(get("/comite-talent").param("trimestre", "2026-3").param("statut", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Ghita Nom"),
                        not(containsString("Ilham Nom")),
                        containsString("selected=\"selected\">En attente (1)</option>"))));
    }

    @Test
    void un_statut_sans_talent_affiche_un_message() throws Exception {
        unTalentValideEtUnEnAttente();

        mockMvc.perform(get("/comite-talent").param("statut", "NON"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Aucun talent proposé pour ce statut."),
                        not(containsString("<table")))));
    }

    @Test
    void sans_trimestre_la_page_le_dit_sans_filtres() throws Exception {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of());

        mockMvc.perform(get("/comite-talent"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Aucun trimestre importé pour le moment"),
                        not(containsString("class=\"filters\"")))));
    }

    @Test
    void sans_talent_propose_la_page_le_dit() throws Exception {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t3));
        when(validationComiteService.getDecisionsComite(t3)).thenReturn(List.of());

        mockMvc.perform(get("/comite-talent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun talent proposé par le moteur pour ce trimestre.")));
    }
}
