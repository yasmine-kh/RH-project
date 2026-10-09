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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
    private CollaborateurRepository collaborateurRepository;
    @MockitoBean
    private com.talent360bank.talent360bank.repository.EntiteRepository entiteRepository;
    @MockitoBean
    private com.talent360bank.talent360bank.repository.ManagerRepository managerRepository;
    @MockitoBean
    private com.talent360bank.talent360bank.service.JournalService journalService;
    @MockitoBean
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

    // ------------------------------------------------------------ POST valide

    private org.springframework.test.web.servlet.ResultActions creer(String json) throws Exception {
        return mockMvc.perform(post("/api/collaborateurs")
                .header(com.talent360bank.talent360bank.config.ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void un_collaborateur_invalide_rend_400_avec_un_message_francais_par_champ() throws Exception {
        creer("""
                {"idCollaborateur": " ", "nom": "", "dateEntree": "2999-01-01", "dateNaissance": "2999-01-01",
                 "email": "pas-un-mail", "grade": "%s", "entite": {}}
                """.formatted("G".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"))
                .andExpect(jsonPath("$.message").value("Le corps de la requête est invalide"))
                .andExpect(jsonPath("$.details", hasItems(
                        "idCollaborateur : Le matricule est obligatoire",
                        "nom : Le nom est obligatoire",
                        "prenom : Le prénom est obligatoire",
                        "dateEntree : La date d'entrée ne peut pas être dans le futur",
                        "dateNaissance : La date de naissance doit être dans le passé",
                        "email : L'adresse e-mail n'est pas valide",
                        "grade : Le grade fait au plus 50 caractères",
                        "entite.idEntite : L'identifiant de l'entité est obligatoire")));
        verify(collaborateurRepository, never()).save(any());
    }

    @Test
    void une_date_d_entree_absente_et_une_entite_inconnue_rendent_400() throws Exception {
        creer("""
                {"idCollaborateur": "E009", "nom": "Nouveau", "prenom": "Arrivant"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItems("dateEntree : La date d'entrée est obligatoire")));

        when(entiteRepository.findById(999)).thenReturn(java.util.Optional.empty());
        creer("""
                {"idCollaborateur": "E009", "nom": "Nouveau", "prenom": "Arrivant", "dateEntree": "2025-09-01",
                 "entite": {"idEntite": 999}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Entité inconnue : 999"));
        verify(collaborateurRepository, never()).save(any());
    }
}
