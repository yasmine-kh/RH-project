package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.ManagerFiche;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Criteres;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.LigneCollaborateur;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.PosteCibleLigne;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Tri;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.VivierRef;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.PosteDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.SuccesseurDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.TalentDg;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;
import com.talent360bank.talent360bank.ui.service.ListeCollaborateursViewService;
import com.talent360bank.talent360bank.ui.service.TableauDeBordDgViewService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat JSON de la liste des collaborateurs et du tableau de bord DG, tel que les ecrans de Ima le liront. */
@Import(SecurityConfig.class)
@WebMvcTest({ListeCollaborateursController.class, TableauDeBordDgController.class})
class ListeCollaborateursEtDgControllerTest {

    private static final String LISTE = "/api/trimestres/2026/3/collaborateurs";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListeCollaborateursViewService listeService;
    @MockBean
    private TableauDeBordDgViewService dgService;
    @MockBean
    private ChargeurRessources chargeur;

    private static ListeCollaborateurs liste(Criteres criteres) {
        LigneCollaborateur ligne = new LigneCollaborateur("BP019", "El Khatib", "Youssef", "Youssef El Khatib",
                new EntiteRef("DIR:RESEAU_RETAIL", "Reseau Retail", "DIRECTION"), "Reseau Retail",
                new ManagerFiche("BP026", "Karim Benali"), "Directeur d'agence", "Manager",
                new BigDecimal("93.20"), new BigDecimal("91.85"), new CaseNeufBox(9, "Talent clé"), true, true,
                false, "EN_ATTENTE", List.of(new VivierRef("COMMERCIAL", "Vivier Commercial"),
                new VivierRef("RELEVE", "Vivier de relève")),
                new PosteCibleLigne("PST01", "Directeur regional", new BigDecimal("90.23"), "READY_NOW", "Ready Now",
                        true, null, 0),
                new BigDecimal("20.00"), "FAIBLE", "Faible");
        return new ListeCollaborateurs(new TrimestreFiche(2026, 3, "T3 2026", LocalDate.of(2026, 9, 15)), criteres,
                100, 1, 1, List.of(ligne), List.of());
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_liste_transmet_les_filtres_et_rend_les_lignes() throws Exception {
        Criteres attendus = new Criteres("DIR:RESEAU_RETAIL", 9, true, "RELEVE", "READY_NOW", "FAIBLE", "khatib",
                Tri.PERFORMANCE, true, 2, 10);
        when(listeService.construire(2026, 3, attendus)).thenReturn(liste(attendus));

        mockMvc.perform(get(LISTE).param("entite", "DIR:RESEAU_RETAIL").param("case", "9").param("talent", "true")
                        .param("vivier", "releve").param("readiness", "ready_now").param("vigilance", "faible")
                        .param("q", " khatib ").param("tri", "performance").param("page", "2").param("taille", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbTotal").value(100))
                .andExpect(jsonPath("$.nbFiltres").value(1))
                .andExpect(jsonPath("$.criteres.decroissant").value(true))
                .andExpect(jsonPath("$.lignes[0].matricule").value("BP019"))
                .andExpect(jsonPath("$.lignes[0].manager.nom").value("Karim Benali"))
                .andExpect(jsonPath("$.lignes[0].neufBox.numero").value(9))
                .andExpect(jsonPath("$.lignes[0].viviers[1].code").value("RELEVE"))
                .andExpect(jsonPath("$.lignes[0].posteCible.readinessLibelle").value("Ready Now"))
                .andExpect(jsonPath("$.lignes[0].niveauVigilance").value("FAIBLE"));
    }

    @Test
    @WithMockUser(roles = "RH")
    void sans_parametre_la_liste_est_triee_par_nom_croissant_page_1() throws Exception {
        Criteres defaut = new Criteres(null, null, null, null, null, null, null, Tri.NOM, false, 1,
                Criteres.TAILLE_PAR_DEFAUT);
        when(listeService.construire(2026, 3, defaut)).thenReturn(liste(defaut));

        mockMvc.perform(get(LISTE)).andExpect(status().isOk());
        verify(listeService).construire(2026, 3, defaut);
    }

    @Test
    @WithMockUser(roles = "RH")
    void un_tri_ou_un_ordre_inconnu_rend_400() throws Exception {
        mockMvc.perform(get(LISTE).param("tri", "age"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("argument_invalide"));
        mockMvc.perform(get(LISTE).param("ordre", "haut"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listeService);
    }

    @Test
    @WithMockUser(roles = "RH")
    void un_critere_refuse_par_le_service_rend_400_et_un_trimestre_inconnu_404() throws Exception {
        when(listeService.construire(eq(2026), eq(3), any()))
                .thenThrow(new IllegalArgumentException("La case 9-box doit etre entre 1 et 9"));
        mockMvc.perform(get(LISTE).param("case", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La case 9-box doit etre entre 1 et 9"));

        when(listeService.construire(eq(2030), eq(1), any()))
                .thenThrow(new RessourceIntrouvableException("Aucun trimestre T1 2030"));
        mockMvc.perform(get("/api/trimestres/2030/1/collaborateurs"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "RH")
    void le_tableau_de_bord_dg_rend_cartes_postes_et_talents() throws Exception {
        Trimestre trimestre = new Trimestre();
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
        when(dgService.construire(trimestre, 5)).thenReturn(new TableauDeBordDg("T3 2026",
                List.of(new KpiCard("Postes critiques", "15", "bi-briefcase", "kpi-blue")), List.of(),
                List.of(new PosteDg("PST01", "Directeur regional", "Reseau Retail", "Tres elevee", "Omar Idrissi", 4, 3,
                        new SuccesseurDg("BP035", "Aicha El Ouafi", new BigDecimal("95.20"), "READY_NOW", "Ready Now"),
                        "READY_NOW", "Couverte - Ready Now", false)),
                List.of(new TalentDg(1, "BP035", "Aicha El Ouafi", "Reseau Retail", new BigDecimal("96.30"),
                        new BigDecimal("89.70"), new BigDecimal("186.00"), true, "OUI", true, null)),
                null));

        mockMvc.perform(get("/api/dashboard/dg").param("annee", "2026").param("numero", "3").param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].label").value("Postes critiques"))
                .andExpect(jsonPath("$.kpis[0].value").value("15"))
                .andExpect(jsonPath("$.postesCritiques[0].nbReadyNow").value(3))
                .andExpect(jsonPath("$.postesCritiques[0].meilleurSuccesseur.matricule").value("BP035"))
                .andExpect(jsonPath("$.topTalents[0].rang").value(1))
                .andExpect(jsonPath("$.topTalents[0].scoreCumule").value(186.00))
                .andExpect(jsonPath("$.topTalents[0].estTalentValide").value(true));
    }

    @Test
    @WithMockUser(roles = "RH")
    void le_tableau_de_bord_dg_prend_8_talents_par_defaut() throws Exception {
        Trimestre trimestre = new Trimestre();
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);
        when(dgService.construire(trimestre, 8)).thenReturn(new TableauDeBordDg("T3 2026", List.of(), List.of(),
                List.of(), List.of(), null));

        mockMvc.perform(get("/api/dashboard/dg").param("annee", "2026").param("numero", "3"))
                .andExpect(status().isOk());
        verify(dgService).construire(trimestre, 8);
    }

    @Test
    void sans_connexion_les_deux_api_rendent_401() throws Exception {
        mockMvc.perform(get(LISTE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/dg").param("annee", "2026").param("numero", "3"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(listeService, dgService);
    }
}
