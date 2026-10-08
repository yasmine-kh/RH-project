package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.config.SecurityConfig;
import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.CampagneService;
import com.talent360bank.talent360bank.service.DossierImportService; // <--- Import ajouté ici
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.BilanFeuille;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WithMockUser(roles = "RH")
@WebMvcTest(ImportController.class)
class ImportControllerTest {

    private static final String EN_TETE = ProtectionRequetesFilter.EN_TETE_ECRITURE;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DossierImportService dossierImportService;

    @MockitoBean
    private ImportService importService;

    @MockitoBean
    private CampagneService campagneService;

    private final MockMultipartFile fichier = new MockMultipartFile("fichier", "dataset.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

    private static ResultatCampagne resultat(StatutImport statut, List<ErreurImport> erreurs, String message) {
        return new ResultatCampagne(importation(statut, erreurs, message), null, null);
    }

    private static ResultatImport importation(StatutImport statut, List<ErreurImport> erreurs, String message) {
        return new ResultatImport(7, "dataset.xlsx", 2026, 3, false, statut,
                statut == StatutImport.ECHEC ? 0 : 102,
                List.of(new BilanFeuille("01_COLLABORATEURS", true, 100, 0, 0),
                        new BilanFeuille("02_PERFORMANCE", true, 2, 1, 3)),
                erreurs, List.of("BP099"), List.of(), message);
    }

    @Test
    void un_import_partiel_rend_200_avec_le_bilan_et_les_erreurs_de_ligne() throws Exception {
        when(campagneService.importer(any(), eq(2026), eq(3), eq(false), eq(true))).thenReturn(
                resultat(StatutImport.PARTIEL,
                        List.of(new ErreurImport("02_PERFORMANCE", 11, "colonne E : ..."),
                                new ErreurImport("02_PERFORMANCE", 12, "colonne D : ...")), null));

        mockMvc.perform(multipart("/api/imports").file(fichier)
                        .param("annee", "2026").param("numero", "3").header(EN_TETE, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idImport").value(7))
                .andExpect(jsonPath("$.statut").value("PARTIEL"))
                .andExpect(jsonPath("$.nbLignes").value(102))
                .andExpect(jsonPath("$.nbErreurs").value(2))
                .andExpect(jsonPath("$.feuilles[0].feuille").value("01_COLLABORATEURS"))
                .andExpect(jsonPath("$.feuilles[0].nbLignes").value(100))
                .andExpect(jsonPath("$.feuilles[1].nbRetirees").value(3))
                .andExpect(jsonPath("$.erreurs[1].feuille").value("02_PERFORMANCE"))
                .andExpect(jsonPath("$.erreurs[1].ligne").value(12))
                .andExpect(jsonPath("$.nbDesactives").value(1))
                .andExpect(jsonPath("$.desactives[0]").value("BP099"))
                .andExpect(jsonPath("$.simulation").value(false));
    }

    @Test
    void la_reponse_porte_le_bilan_du_calcul_lance_apres_l_import() throws Exception {
        ResultatCalculTrimestre calcul = new ResultatCalculTrimestre(
                new ResultatRecalcul(List.of(), List.of(new ResultatRecalcul.CollaborateurIgnore("BP099", "Statut"))),
                new ResultatRecalcul(List.of(), List.of()),
                new ResultatConstitutionVivier(4, List.of(), List.of()));
        when(campagneService.importer(any(), eq(2026), eq(3), eq(false), eq(true))).thenReturn(
                new ResultatCampagne(importation(StatutImport.SUCCES, List.of(), null), calcul, null));

        mockMvc.perform(multipart("/api/imports").file(fichier)
                        .param("annee", "2026").param("numero", "3").header(EN_TETE, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SUCCES"))
                .andExpect(jsonPath("$.calcul.scores.nombreIgnores").value(1))
                .andExpect(jsonPath("$.calcul.vivierReleve").exists())
                .andExpect(jsonPath("$.erreurCalcul").doesNotExist());
    }

    @Test
    void simulation_et_calcul_sont_transmis_au_service() throws Exception {
        when(campagneService.importer(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(resultat(StatutImport.SUCCES, List.of(), null));

        mockMvc.perform(multipart("/api/imports").file(fichier).param("annee", "2026").param("numero", "3")
                        .param("simulation", "true").header(EN_TETE, "1"))
                .andExpect(status().isOk());
        verify(campagneService).importer(any(), eq(2026), eq(3), eq(true), eq(true));

        mockMvc.perform(multipart("/api/imports").file(fichier).param("annee", "2026").param("numero", "3")
                        .param("calcul", "false").header(EN_TETE, "1"))
                .andExpect(status().isOk());
        verify(campagneService).importer(any(), eq(2026), eq(3), eq(false), eq(false));
    }

    @Test
    void un_import_en_echec_rend_422_avec_le_meme_corps() throws Exception {
        when(campagneService.importer(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(resultat(StatutImport.ECHEC, List.of(), "Fichier illisible : ..."));

        mockMvc.perform(multipart("/api/imports").file(fichier)
                        .param("annee", "2026").param("numero", "3").header(EN_TETE, "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.statut").value("ECHEC"))
                .andExpect(jsonPath("$.message").value("Fichier illisible : ..."));
    }

    @Test
    void sans_en_tete_d_ecriture_l_import_est_refuse() throws Exception {
        mockMvc.perform(multipart("/api/imports").file(fichier).param("annee", "2026").param("numero", "3"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erreur").value("en_tete_manquant"));

        verify(campagneService, never()).importer(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void sans_fichier_ou_sans_trimestre_la_requete_est_mal_formee() throws Exception {
        mockMvc.perform(multipart("/api/imports").param("annee", "2026").param("numero", "3").header(EN_TETE, "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"))
                .andExpect(jsonPath("$.message").value("Partie obligatoire absente : fichier"));

        mockMvc.perform(multipart("/api/imports").file(fichier).param("annee", "2026").header(EN_TETE, "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"))
                .andExpect(jsonPath("$.message").value("Paramètre obligatoire absent : numero"));
    }

    @Test
    void un_trimestre_invalide_rend_400() throws Exception {
        when(campagneService.importer(any(), eq(2026), eq(9), anyBoolean(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Le numero de trimestre doit etre entre 1 et 4 (recu 9)"));

        mockMvc.perform(multipart("/api/imports").file(fichier)
                        .param("annee", "2026").param("numero", "9").header(EN_TETE, "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le numero de trimestre doit etre entre 1 et 4 (recu 9)"));
    }

    @Test
    void le_journal_liste_les_imports_du_plus_recent_au_plus_ancien() throws Exception {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(3);
        when(importService.journal()).thenReturn(List.of(
                journal(9, "PARTIEL", trimestre, null), journal(8, "ECHEC", null, "Fichier illisible")));

        mockMvc.perform(get("/api/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idImport").value(9))
                .andExpect(jsonPath("$[0].trimestre").value("T3 2026"))
                .andExpect(jsonPath("$[0].statut").value("PARTIEL"))
                .andExpect(jsonPath("$[0].dateImport").value("2026-09-29"))
                .andExpect(jsonPath("$[1].trimestre").doesNotExist())
                .andExpect(jsonPath("$[1].message").value("Fichier illisible"));
    }

    private static ImportExcel journal(int id, String statut, Trimestre trimestre, String message) {
        ImportExcel journal = new ImportExcel();
        journal.setIdImport(id);
        journal.setSource("CLASSEUR_TALENT_360");
        journal.setNomFichier("dataset.xlsx");
        journal.setDateImport(LocalDate.of(2026, 9, 29));
        journal.setStatut(statut);
        journal.setTrimestre(trimestre);
        journal.setNbLignes(10);
        journal.setNbErreurs(1);
        journal.setMessage(message);
        return journal;
    }
}