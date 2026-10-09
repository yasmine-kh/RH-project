package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.TrimestreFiche;
import com.talent360bank.talent360bank.ui.model.VueEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.AlerteVigilance;
import com.talent360bank.talent360bank.ui.model.VueEntite.ComparaisonEnfant;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteEnfant;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteVue;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.PosteCritiqueVue;
import com.talent360bank.talent360bank.ui.model.VueManager.Compte;
import com.talent360bank.talent360bank.ui.model.VueManager.CompteCase;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import com.talent360bank.talent360bank.ui.model.VueManager.Synthese;
import com.talent360bank.talent360bank.ui.service.VueEntiteViewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat JSON de la vue entite et de l'organigramme, tel que la page de Ima le lira. */
@Import(SecurityConfig.class)
@WebMvcTest(VueEntiteController.class)
class VueEntiteControllerTest {

    /** Code d'entite avec ses "/" : passe en parametre de requete "code". */
    private static final String CODE = "DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD/REG:TANGER_TETOUAN";
    private static final String VUE = "/api/trimestres/2026/3/entites/vue";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VueEntiteViewService service;

    private static VueEntite vue() {
        Synthese synthese = new Synthese(9, 9, new BigDecimal("78.10"), new BigDecimal("76.40"),
                List.of(new Compte("SOLIDE", "Solide", 6)), List.of(new Compte("MOYEN", "Moyen", 7)),
                List.of(new CompteCase(5, "Confirmé", 6)), 1, 1, 2, new BigDecimal("71.30"),
                List.of(new Compte("ELEVEE", "Elevee", 1)), 0);
        return new VueEntite(
                new TrimestreFiche(2026, 3, "T3 2026", LocalDate.of(2026, 9, 15)),
                new EntiteVue(CODE, "Tanger-Tetouan", "REGION",
                        List.of("Reseau Retail", "Reseau Retail - Sud", "Tanger-Tetouan"),
                        new EntiteRef("DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD", "Reseau Retail - Sud", "DEPARTEMENT"),
                        List.of(new EntiteEnfant(CODE + "/AGE:AGENCE_TANGER_NORD", "Agence Tanger Nord", "AGENCE", 5))),
                synthese,
                List.of(new ComparaisonEnfant(CODE + "/AGE:AGENCE_TANGER_NORD", "Agence Tanger Nord", "AGENCE", 5, 5,
                        new BigDecimal("80.00"), new BigDecimal("77.00"), new BigDecimal("20.0"),
                        new BigDecimal("70.00"), 1)),
                List.of(new PosteCritiqueVue("PST01", "Directeur regional", "Reseau Retail", "Tres elevee", "BP022",
                        "Rim Filali", "TITULAIRE", "READY_NOW", "Couverte - Ready Now", false, 4,
                        new BigDecimal("95.20"))),
                List.of(new AlerteVigilance("BP011", "Tazi", "Nadia",
                        new EntiteRef(CODE + "/AGE:AGENCE_TANGER_NORD", "Agence Tanger Nord", "AGENCE"),
                        new BigDecimal("65.00"), "ELEVEE", "Elevee")),
                List.of(new ManagerResume("BP005", "Chraibi", "Meryem", "Directeur regional", null, 5)),
                List.of());
    }

    @Test
    @WithMockUser(roles = "RH")
    void l_organigramme_est_rendu_en_arbre() throws Exception {
        when(service.listerEntites()).thenReturn(List.of(new NoeudEntite("DIR:RESEAU_RETAIL", "Reseau Retail",
                "DIRECTION", 40, List.of(new NoeudEntite("DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD",
                "Reseau Retail - Sud", "DEPARTEMENT", 12, List.of())))));

        mockMvc.perform(get("/api/entites"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].code").value("DIR:RESEAU_RETAIL"))
                .andExpect(jsonPath("$[0].effectif").value(40))
                .andExpect(jsonPath("$[0].enfants[0].type").value("DEPARTEMENT"))
                .andExpect(jsonPath("$[0].enfants[0].enfants").isEmpty());
    }

    @Test
    @WithMockUser(roles = "RH")
    void la_vue_prend_le_code_complet_avec_ses_barres_et_rend_tous_les_blocs() throws Exception {
        when(service.construire(CODE, 2026, 3)).thenReturn(vue());

        mockMvc.perform(get(VUE).param("code", CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trimestre.libelle").value("T3 2026"))
                .andExpect(jsonPath("$.entite.code").value(CODE))
                .andExpect(jsonPath("$.entite.chemin[2]").value("Tanger-Tetouan"))
                .andExpect(jsonPath("$.entite.parent.type").value("DEPARTEMENT"))
                .andExpect(jsonPath("$.entite.enfants[0].effectif").value(5))
                .andExpect(jsonPath("$.synthese.effectif").value(9))
                .andExpect(jsonPath("$.synthese.nbSansVigilance").value(0))
                .andExpect(jsonPath("$.enfants[0].pourcentageTalents").value(20.0))
                .andExpect(jsonPath("$.enfants[0].nbVigilanceElevee").value(1))
                .andExpect(jsonPath("$.postesCritiques[0].rattachement").value("TITULAIRE"))
                .andExpect(jsonPath("$.postesCritiques[0].nbSuccesseurs").value(4))
                .andExpect(jsonPath("$.alertes[0].entite.libelle").value("Agence Tanger Nord"))
                .andExpect(jsonPath("$.managers[0].matricule").value("BP005"))
                .andExpect(jsonPath("$.donneesManquantes").isArray());
    }

    @Test
    @WithMockUser(roles = "RH")
    void le_code_ecrit_tel_quel_ou_encode_dans_l_url_donne_la_meme_entite() throws Exception {
        when(service.construire(CODE, 2026, 3)).thenReturn(vue());

        // Tel quel, comme le construit @{...(code=${...})} de Thymeleaf
        mockMvc.perform(get(VUE + "?code=" + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entite.code").value(CODE));
        // Entierement encode (%3A, %2F), comme encodeURIComponent cote JavaScript ;
        // URI.create pour que MockMvc ne re-encode pas les "%"
        mockMvc.perform(get(URI.create(VUE + "?code=DIR%3ARESEAU_RETAIL%2FDEP%3ARESEAU_RETAIL_SUD%2FREG%3ATANGER_TETOUAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entite.code").value(CODE));
    }

    @Test
    @WithMockUser(roles = "RH")
    void l_ancienne_forme_avec_le_code_dans_le_chemin_n_existe_plus() throws Exception {
        mockMvc.perform(get(VUE + "/" + CODE)).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "RH")
    void sans_code_la_vue_rend_400() throws Exception {
        mockMvc.perform(get(VUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"))
                .andExpect(jsonPath("$.message").value("Paramètre obligatoire absent : code"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "RH")
    void une_entite_inconnue_rend_404() throws Exception {
        when(service.construire("DIR:INCONNUE", 2026, 3))
                .thenThrow(new RessourceIntrouvableException("Aucune entité DIR:INCONNUE"));

        mockMvc.perform(get(VUE).param("code", "DIR:INCONNUE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Aucune entité DIR:INCONNUE"));
    }

    @Test
    void sans_connexion_l_api_rend_401() throws Exception {
        mockMvc.perform(get("/api/entites")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(VUE).param("code", CODE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erreur").value("non_authentifie"));
        verifyNoInteractions(service);
    }
}
