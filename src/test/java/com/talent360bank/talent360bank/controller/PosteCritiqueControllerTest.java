package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosteCritiqueController.class)
class PosteCritiqueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PosteCritiqueService posteCritiqueService;
    @MockBean
    private ChargeurRessources chargeur;

    private Trimestre trimestre;
    private CouverturePoste couverte;
    private CouverturePoste enAlerte;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        when(chargeur.exigerTrimestre(2026, 3)).thenReturn(trimestre);

        Employe successeur = new Employe();
        successeur.setEmployeeId("BP035");
        successeur.setNom("El Ouafi");
        successeur.setPrenom("Aicha");
        successeur.setDateEntree(LocalDate.now().minusYears(12));
        successeur.setStatut(StatutEmploye.ACTIF);
        ResultatMatching matching = new ResultatMatching(successeur, new BigDecimal("95.20"),
                NiveauReadiness.READY_NOW, new ResultatMatching.DetailMatching(
                new BigDecimal("100"), new BigDecimal("96.30"), new BigDecimal("89.70"),
                new BigDecimal("100"), new BigDecimal("86"), new BigDecimal("94")));

        couverte = new CouverturePoste(poste("PST01", "Directeur regional", "BP022"), 1,
                List.of(matching), List.of(), NiveauCouverture.READY_NOW);
        enAlerte = new CouverturePoste(poste("PST13", "Responsable Cybersecurite", "BP075"), 0,
                List.of(), List.of(new CouverturePoste.SuccesseurIgnore("BP075", "Titulaire du poste")),
                NiveauCouverture.ALERTE);
    }

    private Poste poste(String posteId, String nom, String titulaireId) {
        Poste poste = new Poste();
        poste.setPosteId(posteId);
        poste.setNomPoste(nom);
        poste.setDirection("IT & Digital");
        poste.setCriticite("Tres elevee");
        poste.setPosteCritique("Oui");
        poste.setTitulaireId(titulaireId);
        return poste;
    }

    @Test
    void la_liste_expose_la_couverture_et_le_meilleur_successeur() throws Exception {
        when(posteCritiqueService.listerPostesCritiques(trimestre)).thenReturn(List.of(couverte, enAlerte));

        mockMvc.perform(get("/api/postes-critiques?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posteId").value("PST01"))
                .andExpect(jsonPath("$[0].titulaireId").value("BP022"))
                .andExpect(jsonPath("$[0].nbSuccesseurs").value(1))
                .andExpect(jsonPath("$[0].meilleurMatching").value(95.20))
                .andExpect(jsonPath("$[0].couverture").value("READY_NOW"))
                .andExpect(jsonPath("$[0].alerte").value(false))
                .andExpect(jsonPath("$[0].successeurs[0].candidat.employeeId").value("BP035"))
                .andExpect(jsonPath("$[1].alerte").value(true))
                .andExpect(jsonPath("$[1].meilleurMatching").doesNotExist())
                .andExpect(jsonPath("$[1].ignores[0].motif").value("Titulaire du poste"));
    }

    @Test
    void les_alertes_ne_rendent_que_les_postes_en_alerte() throws Exception {
        when(posteCritiqueService.detecterAlertes(trimestre)).thenReturn(List.of(enAlerte));

        mockMvc.perform(get("/api/postes-critiques/alertes?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].posteId").value("PST13"))
                .andExpect(jsonPath("$[0].couvertureLibelle").value("Successeurs insuffisants - ALERTE"));
    }

    @Test
    void la_synthese_donne_les_chiffres_du_tableau_de_bord() throws Exception {
        List<CouverturePoste> couvertures = List.of(couverte, enAlerte);
        when(posteCritiqueService.listerPostesCritiques(trimestre)).thenReturn(couvertures);
        when(posteCritiqueService.tauxCouverture(couvertures)).thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/postes-critiques/synthese?annee=2026&numero=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbPostesCritiques").value(2))
                .andExpect(jsonPath("$.nbAlertes").value(1))
                .andExpect(jsonPath("$.tauxCouverture").value(50.00))
                .andExpect(jsonPath("$.alertes[0].posteId").value("PST13"));
    }

    @Test
    void un_poste_non_critique_rend_404() throws Exception {
        when(posteCritiqueService.evaluerCouverture("PST04", trimestre))
                .thenThrow(new RessourceIntrouvableException("Le poste PST04 n'est pas un poste critique"));

        mockMvc.perform(get("/api/postes-critiques/PST04?annee=2026&numero=3"))
                .andExpect(status().isNotFound());
    }
}
