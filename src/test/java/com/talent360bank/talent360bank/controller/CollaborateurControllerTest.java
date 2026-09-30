package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Sexe;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les lectures rendent CollaborateurResponse, jamais l'entite JPA. */
@Import(SecurityConfig.class)
@WithMockUser(roles = "RH")
@WebMvcTest(CollaborateurController.class)
class CollaborateurControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CollaborateurRepository collaborateurRepository;
    @MockBean
    private ChargeurRessources chargeur;

    private Collaborateur sara;

    @BeforeEach
    void init() {
        Collaborateur chef = new Collaborateur();
        chef.setIdCollaborateur("M001");

        sara = new Collaborateur();
        sara.setIdCollaborateur("E001");
        sara.setNom("Bennani");
        sara.setPrenom("Sara");
        sara.setSexe(Sexe.FEMME);
        sara.setDateNaissance(LocalDate.of(1990, 5, 17));
        sara.setDateEntree(LocalDate.of(2015, 1, 1));
        sara.setFonction("Analyste");
        sara.setGrade("C2");
        sara.setStatut(StatutCollaborateur.ACTIF);
        sara.setEntite(new Entite("Reseau Retail", TypeEntite.DIRECTION, null));
        sara.setManager(new Manager(chef));
    }

    @Test
    void la_liste_rend_des_fiches_avec_tous_les_champs_pour_le_rh() throws Exception {
        when(collaborateurRepository.findAllAvecManager()).thenReturn(List.of(sara));

        mockMvc.perform(get("/api/collaborateurs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCollaborateur").value("E001"))
                .andExpect(jsonPath("$[0].nom").value("Bennani"))
                .andExpect(jsonPath("$[0].prenom").value("Sara"))
                .andExpect(jsonPath("$[0].sexe").value("FEMME"))
                .andExpect(jsonPath("$[0].dateNaissance").value("1990-05-17"))
                .andExpect(jsonPath("$[0].dateEntree").value("2015-01-01"))
                .andExpect(jsonPath("$[0].direction").value("Reseau Retail"))
                .andExpect(jsonPath("$[0].fonction").value("Analyste"))
                .andExpect(jsonPath("$[0].grade").value("C2"))
                .andExpect(jsonPath("$[0].idManager").value("M001"))
                .andExpect(jsonPath("$[0].statut").value("ACTIF"))
                // Pas l'entite : ni l'arbre Entite, ni les getters calcules de l'entite JPA.
                .andExpect(jsonPath("$[0].entite").doesNotExist())
                .andExpect(jsonPath("$[0].nomComplet").doesNotExist());
    }

    @Test
    void la_fiche_d_un_collaborateur_est_le_meme_dto() throws Exception {
        when(chargeur.exigerCollaborateur("E001")).thenReturn(sara);

        mockMvc.perform(get("/api/collaborateurs/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCollaborateur").value("E001"))
                .andExpect(jsonPath("$.dateNaissance").value("1990-05-17"))
                .andExpect(jsonPath("$.idManager").value("M001"))
                .andExpect(jsonPath("$.entite").doesNotExist());
    }

    @Test
    void un_collaborateur_inconnu_rend_404() throws Exception {
        when(chargeur.exigerCollaborateur("E999"))
                .thenThrow(new RessourceIntrouvableException("Aucun collaborateur E999"));

        mockMvc.perform(get("/api/collaborateurs/E999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erreur").value("ressource_introuvable"));
    }
}
