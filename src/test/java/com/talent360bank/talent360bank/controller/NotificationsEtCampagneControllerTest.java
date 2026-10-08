package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.AlertesView.Compteur;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.Notifications;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne.Ligne;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne.ManagerEnRetard;
import com.talent360bank.talent360bank.ui.model.TypeAlerte;
import com.talent360bank.talent360bank.ui.service.NotificationsViewService;
import com.talent360bank.talent360bank.ui.service.SuiviCampagneViewService;
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

/** Contrat JSON des notifications (et du badge) et de l'avancement de campagne. */
@Import(SecurityConfig.class)
@WebMvcTest({NotificationsController.class, SuiviCampagneController.class})
class NotificationsEtCampagneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationsViewService notificationsService;
    @MockBean
    private SuiviCampagneViewService campagneService;

    @Test
    @WithMockUser(roles = "RH")
    void les_notifications_rendent_compteurs_alertes_et_liens() throws Exception {
        when(notificationsService.notifications()).thenReturn(new Notifications("T3 2026", 2,
                List.of(new Compteur("CRITIQUE", "Critique", 1),
                        new Compteur("ELEVEE", "Élevée", 1)),
                List.of(new Compteur("POSTE_SANS_SUCCESSEUR", "Poste critique sans successeur", 1)),
                List.of(new AlerteVue(TypeAlerte.POSTE_SANS_SUCCESSEUR, SeveriteAlerte.CRITIQUE, "Responsable Cybersecurite",
                        "PST13", null, "IT & Digital", "Aucun successeur identifié",
                        "/postes-critiques?trimestre=2026-3#poste-PST13", "Postes critiques")),
                "/alertes?trimestre=2026-3", List.of("Nouveaux talents : comparés à T2 2026."), null));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.parSeverite[0].code").value("CRITIQUE"))
                .andExpect(jsonPath("$.alertes[0].type").value("POSTE_SANS_SUCCESSEUR"))
                .andExpect(jsonPath("$.alertes[0].lien").value("/postes-critiques?trimestre=2026-3#poste-PST13"))
                .andExpect(jsonPath("$.lienAlertes").value("/alertes?trimestre=2026-3"))
                .andExpect(jsonPath("$.informations[0]").value("Nouveaux talents : comparés à T2 2026."));
    }

    @Test
    @WithMockUser(roles = "RH")
    void le_badge_rend_le_total_et_les_gravites() throws Exception {
        when(notificationsService.badge()).thenReturn(new Notifications.Badge("T3 2026", 23, 1, 10, 12,
                "/alertes?trimestre=2026-3"));

        mockMvc.perform(get("/api/notifications/badge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(23))
                .andExpect(jsonPath("$.critiques").value(1))
                .andExpect(jsonPath("$.elevees").value(10))
                .andExpect(jsonPath("$.moyennes").value(12))
                .andExpect(jsonPath("$.lienAlertes").value("/alertes?trimestre=2026-3"));
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_campagne_rend_l_avancement_par_direction() throws Exception {
        Ligne retail = new Ligne("DIR:RESEAU_RETAIL", "Reseau Retail", "DIRECTION", 47, 45, new BigDecimal("95.7"), 2,
                0, List.of(new ManagerEnRetard("BP026", "Karim Benali", 2, List.of("BP001", "BP003"))));
        when(campagneService.construire(2026, 3, "DIR:RESEAU_RETAIL")).thenReturn(new SuiviCampagne(
                new TrimestreFiche(2026, 3, "T3 2026", LocalDate.of(2026, 9, 15)), null, retail, List.of(retail),
                List.of("Le classeur ne contient ni dates ni statut de campagne")));

        mockMvc.perform(get("/api/trimestres/2026/3/campagne").param("entite", " DIR:RESEAU_RETAIL "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.nbActifs").value(47))
                .andExpect(jsonPath("$.lignes[0].pourcentageEvalues").value(95.7))
                .andExpect(jsonPath("$.lignes[0].nbManquants").value(2))
                .andExpect(jsonPath("$.lignes[0].managers[0].matricule").value("BP026"))
                .andExpect(jsonPath("$.lignes[0].managers[0].manquants[1]").value("BP003"))
                .andExpect(jsonPath("$.remarques[0]").exists());
    }

    @Test
    @WithMockUser(roles = "RH")
    void une_entite_ou_un_trimestre_inconnu_rend_404() throws Exception {
        when(campagneService.construire(2026, 3, "INCONNU"))
                .thenThrow(new RessourceIntrouvableException("Aucune entité INCONNU"));
        mockMvc.perform(get("/api/trimestres/2026/3/campagne").param("entite", "INCONNU"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erreur").value("ressource_introuvable"));
    }

    @Test
    void sans_connexion_les_api_rendent_401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/badge")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/trimestres/2026/3/campagne")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationsService, campagneService);
    }
}
