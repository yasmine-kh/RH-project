package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Competence;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Critere;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.EntiteFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Evaluation;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.HistoriqueTrimestre;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Identite;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.ManagerFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.PosteCible;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.RaisonVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Succession;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Talent;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.Vigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.VivierFiche;
import com.talent360bank.talent360bank.ui.service.FicheCollaborateurViewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

/** Contrat JSON de la fiche collaborateur, tel que la page de Ima le lira. */
@Import(SecurityConfig.class)
@WebMvcTest(FicheCollaborateurController.class)
class FicheCollaborateurControllerTest {

    private static final String URL = "/api/trimestres/2026/3/collaborateurs/BP001/fiche";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FicheCollaborateurViewService service;

    private static FicheCollaborateur fiche() {
        return new FicheCollaborateur(
                new TrimestreFiche(2026, 3, "T3 2026", LocalDate.of(2026, 9, 15)),
                new Identite("BP001", "Bouzid", "Hind", "Conseillere clientele", "C2",
                        new EntiteFiche("DIR:RESEAU_RETAIL/DEP:NORD/AGE:TANGER", "Tanger", "AGENCE",
                                List.of("Reseau Retail", "Nord", "Tanger")),
                        new ManagerFiche("BP050", "Omar Idrissi"), "ACTIF", LocalDate.of(2018, 1, 8),
                        new BigDecimal("8.7")),
                new Evaluation(new BigDecimal("61.60"), "A_RENFORCER", "A renforcer",
                        List.of(new Critere("OBJECTIFS", "Objectifs", new BigDecimal("64"), new BigDecimal("30")))),
                null,
                new CaseNeufBox(2, "À développer"),
                new Talent(false, false, false, "OUI", "Oui",
                        List.of(new VivierFiche("COMMERCIAL", "Vivier Commercial", "THEMATIQUE"))),
                List.of(new Competence("C001", "Credit", "Metier", 4, 3, 1, "A_DEVELOPPER", "A developper")),
                new BigDecimal("76.00"),
                new Vigilance(new BigDecimal("40.00"), "MODEREE", "Moderee",
                        List.of(new RaisonVigilance("SANS_MOBILITE_4_ANS", "Aucun mouvement depuis 4 ans",
                                new BigDecimal("20.00")))),
                List.of(new Succession("PST01", "Directeur d'agence", "Reseau Retail", "Haute",
                        new BigDecimal("72.35"), "ENTRE_1_ET_2_ANS", "1-2 ans", "Leadership", 2)),
                new PosteCible("PST08", "Directeur Conformite", "Conformite", "Elevee", new BigDecimal("74.10"),
                        "ENTRE_1_ET_2_ANS", "1-2 ans", false, "Conformite", 2),
                List.of(new HistoriqueTrimestre(2026, 2, "T2 2026", new BigDecimal("63.00"),
                        new BigDecimal("74.00"), new CaseNeufBox(2, "À développer"))),
                null,
                List.of("Pas d'évaluation de potentiel pour ce trimestre"));
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_fiche_est_rendue_en_json_avec_tous_ses_blocs() throws Exception {
        when(service.construire("BP001", 2026, 3)).thenReturn(fiche());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trimestre.libelle").value("T3 2026"))
                .andExpect(jsonPath("$.trimestre.dateReference").value("2026-09-15"))
                .andExpect(jsonPath("$.identite.matricule").value("BP001"))
                .andExpect(jsonPath("$.identite.entite.chemin[0]").value("Reseau Retail"))
                .andExpect(jsonPath("$.identite.manager.matricule").value("BP050"))
                .andExpect(jsonPath("$.identite.anciennete").value(8.7))
                .andExpect(jsonPath("$.performance.score").value(61.60))
                .andExpect(jsonPath("$.performance.criteres[0].poids").value(30))
                .andExpect(jsonPath("$.neufBox.numero").value(2))
                .andExpect(jsonPath("$.talent.estTalent").value(false))
                .andExpect(jsonPath("$.talent.decisionComite").value("OUI"))
                .andExpect(jsonPath("$.talent.viviers[0].origine").value("THEMATIQUE"))
                .andExpect(jsonPath("$.competences[0].niveauRequis").value(4))
                .andExpect(jsonPath("$.competences[0].statut").value("A_DEVELOPPER"))
                .andExpect(jsonPath("$.engagement").value(76.00))
                .andExpect(jsonPath("$.vigilance.raisons[0].points").value(20.00))
                .andExpect(jsonPath("$.successions[0].readinessLibelle").value("1-2 ans"))
                .andExpect(jsonPath("$.successions[0].gapCompetence").value("Leadership"))
                .andExpect(jsonPath("$.successions[0].gapNiveaux").value(2))
                .andExpect(jsonPath("$.posteCible.posteId").value("PST08"))
                .andExpect(jsonPath("$.posteCible.scoreMatching").value(74.10))
                .andExpect(jsonPath("$.posteCible.readiness").value("ENTRE_1_ET_2_ANS"))
                .andExpect(jsonPath("$.posteCible.successeurIdentifie").value(false))
                .andExpect(jsonPath("$.posteCible.gapCompetence").value("Conformite"))
                .andExpect(jsonPath("$.historique[0].neufBox.libelle").value("À développer"))
                .andExpect(jsonPath("$.donneesManquantes[0]").value("Pas d'évaluation de potentiel pour ce trimestre"))
                // Blocs absents : presents en JSON avec la valeur null, pour un contrat stable.
                .andExpect(jsonPath("$.potentiel").isEmpty())
                .andExpect(jsonPath("$.autoEvaluation").isEmpty());
    }

    @Test
    @WithMockUser(roles = "RH")
    void un_matricule_inconnu_rend_404() throws Exception {
        when(service.construire("BP001", 2026, 3))
                .thenThrow(new RessourceIntrouvableException("Aucun collaborateur BP001"));

        mockMvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erreur").value("ressource_introuvable"));
    }

    @Test
    void sans_connexion_l_api_rend_401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erreur").value("non_authentifie"));
        verifyNoInteractions(service);
    }
}
