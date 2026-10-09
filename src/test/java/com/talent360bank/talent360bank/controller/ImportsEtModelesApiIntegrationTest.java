package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ClasseurDeTest;
import com.talent360bank.talent360bank.service.DossierImportService;
import com.talent360bank.talent360bank.service.ImportService;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les routes d'API sans test jusqu'ici : import d'un dossier du serveur (dossier configure par
 * talent360.import.folder), historique des imports, modeles Excel collaborateur et manager. Et la date de
 * reference de POST /api/imports, prise dans le classeur (00_DASHBOARD A2) quand elle n'est pas donnee.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:imports-modeles;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class ImportsEtModelesApiIntegrationTest {

    private static final String EN_TETE = ProtectionRequetesFilter.EN_TETE_ECRITURE;
    private static final Path DOSSIER = creerDossier();

    @DynamicPropertySource
    static void dossierImport(DynamicPropertyRegistry proprietes) {
        proprietes.add(DossierImportService.PROPRIETE_DOSSIER, DOSSIER::toString);
    }

    private static Path creerDossier() {
        try {
            Path dossier = Files.createTempDirectory("talent360-imports-test");
            Files.write(dossier.resolve("campagne.xlsx"), ClasseurDeTest.complet().octets());
            Files.writeString(dossier.resolve("lisez-moi.txt"), "ignore : pas un classeur");
            dossier.toFile().deleteOnExit();
            dossier.resolve("campagne.xlsx").toFile().deleteOnExit();
            dossier.resolve("lisez-moi.txt").toFile().deleteOnExit();
            return dossier;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrimestreRepository trimestreRepository;

    @Test
    @Order(1)
    void l_import_de_dossier_lit_les_classeurs_du_dossier_configure() throws Exception {
        mockMvc.perform(post("/api/imports/dossier").header(EN_TETE, "1")
                        .param("annee", "2094").param("numero", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succes").value(true))
                .andExpect(jsonPath("$.bilansParFichier.length()").value(1))
                .andExpect(jsonPath("$.bilansParFichier['campagne.xlsx'].statut").value("SUCCES"))
                .andExpect(jsonPath("$.erreurs").isEmpty());
        assertThat(trimestreRepository.findByNumeroAndAnnee(2, 2094)).isPresent();
    }

    @Test
    @Order(2)
    void l_historique_liste_l_import_du_dossier() throws Exception {
        mockMvc.perform(get("/api/imports/historique"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomFichier").value("campagne.xlsx"))
                .andExpect(jsonPath("$[0].statut").value("SUCCES"))
                .andExpect(jsonPath("$[0].trimestre").value("T2 2094"));
    }

    @Test
    @Order(3)
    void le_modele_collaborateur_est_un_classeur_telechargeable() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/api/templates/collaborateur/" + ClasseurDeTest.E1))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=template_collaborateur_" + ClasseurDeTest.E1 + ".xlsx"))
                .andReturn();
        assertThat(resultat.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        try (Workbook classeur = WorkbookFactory.create(
                new ByteArrayInputStream(resultat.getResponse().getContentAsByteArray()))) {
            assertThat(classeur.getNumberOfSheets()).isPositive();
        }
    }

    @Test
    @Order(4)
    void le_modele_manager_est_un_classeur_telechargeable() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/api/templates/manager/TSTC03"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=template_manager_TSTC03.xlsx"))
                .andReturn();
        try (Workbook classeur = WorkbookFactory.create(
                new ByteArrayInputStream(resultat.getResponse().getContentAsByteArray()))) {
            assertThat(classeur.getNumberOfSheets()).isPositive();
        }
    }

    @Test
    @Order(5)
    void l_import_par_l_api_prend_la_date_du_classeur_sans_date_donnee() throws Exception {
        mockMvc.perform(multipart("/api/imports").file(ClasseurDeTest.complet().autreFeuille("00_DASHBOARD")
                                .commentaire("00_DASHBOARD", "Tableau de bord - au 14/11/2094").fichier("api.xlsx"))
                        .header(EN_TETE, "1").param("annee", "2094").param("numero", "4").param("calcul", "false"))
                .andExpect(status().isOk());
        assertThat(trimestreRepository.findByNumeroAndAnnee(4, 2094).orElseThrow().getDateReference())
                .isEqualTo(LocalDate.of(2094, 11, 14));

        mockMvc.perform(multipart("/api/imports").file(ClasseurDeTest.complet().fichier("api.xlsx"))
                        .header(EN_TETE, "1").param("annee", "2095").param("numero", "1").param("calcul", "false")
                        .param("dateReference", "2095-02-20"))
                .andExpect(status().isOk());
        assertThat(trimestreRepository.findByNumeroAndAnnee(1, 2095).orElseThrow().getDateReference())
                .isEqualTo(LocalDate.of(2095, 2, 20));
    }

    @Test
    void sans_dossier_configure_l_import_de_dossier_le_dit_sans_chemin_par_defaut() throws Exception {
        DossierImportService service = new DossierImportService(mock(ImportService.class), "");

        DossierImportService.RapportDossierImport rapport = service.importerDossier(2094, 1, true);

        assertThat(rapport.succes()).isFalse();
        assertThat(rapport.erreurs()).containsExactly(DossierImportService.MESSAGE_NON_CONFIGURE);
        assertThat(DossierImportService.MESSAGE_NON_CONFIGURE).contains("talent360.import.folder",
                "TALENT360_IMPORT_FOLDER");
    }

    @Test
    void la_propriete_n_a_plus_de_chemin_windows_par_defaut() throws Exception {
        String proprietes = Files.readString(Path.of("src/main/resources/application.properties"));
        assertThat(proprietes).contains("talent360.import.folder=${TALENT360_IMPORT_FOLDER:}")
                .doesNotContain("C:/talent360/imports");
        assertThat(Files.readString(Path.of("src/main/java/com/talent360bank/talent360bank/service/"
                + "DossierImportService.java"))).doesNotContain("C:/talent360");
    }
}
