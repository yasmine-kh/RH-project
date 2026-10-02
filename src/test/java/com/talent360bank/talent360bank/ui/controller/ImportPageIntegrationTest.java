package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ClasseurDeTest;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import com.talent360bank.talent360bank.ui.model.ImportPageView;
import com.talent360bank.talent360bank.ui.model.ImportPageView.LigneJournal;
import com.talent360bank.talent360bank.ui.model.ImportPageView.Rapport;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.talent360bank.talent360bank.service.ImportClasseurService.FEUILLE_PERFORMANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Page d'import (/import) sur H2, avec le petit classeur de test
 * (ClasseurDeTest : 25 lignes, trimestre T3 2026). Le vrai classeur est
 * couvert par ImportPageDatasetTest, le vrai serveur (Tomcat, jeton CSRF dans
 * un formulaire multipart, limite de taille) par ImportPageHttpTest.
 *
 * <p>Limite de taille abaissee a 200 Ko pour tester le refus sans fabriquer
 * un fichier de 10 Mo.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:import-page;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.servlet.multipart.max-file-size=200KB", "spring.servlet.multipart.max-request-size=200KB"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class ImportPageIntegrationTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ImportExcelRepository importExcelRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private VerrouCalculTrimestre verrou;

    // ------------------------------------------------------------ formulaire

    @Test
    @Order(1)
    void la_page_propose_le_trimestre_suivant_le_dernier_existant() throws Exception {
        trimestre(2026, 2);

        ImportPageView vue = vue(mockMvc.perform(get("/import")).andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\""))));

        assertThat(vue.formulaire()).isEqualTo(new ImportPageView.Formulaire("2026", "3", false, true, ""));
        assertThat(vue.rapport()).isNull();
        assertThat(vue.tailleMaxLibelle()).isEqualTo("200 Ko");
        assertThat(vue.journal()).isEmpty();
    }

    // ------------------------------------------------------------ import reussi

    @Test
    @Order(2)
    void import_et_calcul_rendent_le_bilan_et_le_lien_vers_le_tableau_de_bord() throws Exception {
        ImportPageView vue = vue(mockMvc.perform(envoi(ClasseurDeTest.complet().fichier("classeur-test.xlsx"),
                        "2026", "3").param("calcul", "true").param("dateReference", "2026-09-15").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Voir le tableau de bord T3 2026")))
                .andExpect(content().string(containsString("href=\"/?trimestre=2026-3\""))));

        Rapport rapport = vue.rapport();
        assertThat(vue.erreur()).isNull();
        assertThat(rapport.statut()).isEqualTo("SUCCES");
        assertThat(rapport.simulation()).isFalse();
        assertThat(rapport.nbLignes()).isEqualTo(25);
        assertThat(rapport.feuilles()).isNotEmpty().allSatisfy(feuille -> assertThat(feuille.presente()).isTrue());
        assertThat(rapport.calcul().scores()).isPositive();
        assertThat(rapport.calcul().placements()).isEqualTo(rapport.calcul().scores());
        assertThat(rapport.erreurCalcul()).isNull();
        assertThat(rapport.lienTableauDeBord()).isEqualTo("/?trimestre=2026-3");
        assertThat(rapport.dateReference()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow().getDateReference())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        // Le formulaire garde les valeurs envoyees.
        assertThat(vue.formulaire().annee()).isEqualTo("2026");
        assertThat(vue.formulaire().calcul()).isTrue();
    }

    @Test
    @Order(3)
    void le_journal_liste_l_import_le_plus_recent_en_premier() throws Exception {
        ImportPageView vue = vue(mockMvc.perform(get("/import")).andExpect(status().isOk())
                .andExpect(content().string(containsString("classeur-test.xlsx"))));

        assertThat(vue.journal()).hasSize(1);
        LigneJournal ligne = vue.journal().get(0);
        assertThat(ligne.nomFichier()).isEqualTo("classeur-test.xlsx");
        assertThat(ligne.trimestre()).isEqualTo("T3 2026");
        assertThat(ligne.statut()).isEqualTo("SUCCES");
        assertThat(ligne.nbLignes()).isEqualTo(25);
        assertThat(ligne.dateImport()).isEqualTo(LocalDate.now());
        // Le trimestre suivant le plus recent est maintenant T4 2026.
        assertThat(vue.formulaire().numero()).isEqualTo("4");
    }

    @Test
    @Order(4)
    void sans_calcul_coche_l_import_n_est_pas_calcule() throws Exception {
        Rapport rapport = vue(mockMvc.perform(envoi(ClasseurDeTest.complet().fichier("classeur-test.xlsx"),
                "2026", "3").with(csrf())).andExpect(status().isOk())).rapport();

        assertThat(rapport.statut()).isEqualTo("SUCCES");
        assertThat(rapport.calcul()).isNull();
        assertThat(rapport.lienTableauDeBord()).isNull();
    }

    // ------------------------------------------------------------ simulation

    @Test
    @Order(5)
    void la_simulation_n_enregistre_rien() throws Exception {
        long imports = importExcelRepository.count();

        Rapport rapport = vue(mockMvc.perform(envoi(ClasseurDeTest.complet().fichier("classeur-test.xlsx"),
                        "2027", "1").param("simulation", "true").param("calcul", "true").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Simulation : rien n'a été enregistré")))).rapport();

        assertThat(rapport.simulation()).isTrue();
        assertThat(rapport.statut()).isEqualTo("SUCCES");
        assertThat(rapport.nbLignes()).isEqualTo(25);
        assertThat(rapport.calcul()).isNull();
        assertThat(rapport.lienTableauDeBord()).isNull();
        assertThat(importExcelRepository.count()).isEqualTo(imports);
        assertThat(trimestreRepository.findByNumeroAndAnnee(1, 2027)).isEmpty();
    }

    // ------------------------------------------------------------ erreurs d'import

    @Test
    @Order(6)
    void une_ligne_en_erreur_est_listee_avec_sa_feuille_sa_ligne_excel_et_son_motif() throws Exception {
        MockMultipartFile fichier = ClasseurDeTest.complet()
                .cellule(FEUILLE_PERFORMANCE, 0, 3, 150)
                .cellule(FEUILLE_PERFORMANCE, 1, 4, "<b>abc</b>")
                .fichier("classeur-test.xlsx");

        Rapport rapport = vue(mockMvc.perform(envoi(fichier, "2026", "3").param("calcul", "true").with(csrf()))
                .andExpect(status().isOk())
                // Texte de cellule echappe (th:text), jamais interprete.
                .andExpect(content().string(not(containsString("<b>abc</b>"))))
                .andExpect(content().string(containsString("Lignes et feuilles écartées")))).rapport();

        assertThat(rapport.statut()).isEqualTo("PARTIEL");
        assertThat(rapport.erreurs()).extracting(ErreurImport::feuille, ErreurImport::ligne)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(FEUILLE_PERFORMANCE,
                                ClasseurDeTest.ligneExcel(FEUILLE_PERFORMANCE, 0)),
                        org.assertj.core.groups.Tuple.tuple(FEUILLE_PERFORMANCE,
                                ClasseurDeTest.ligneExcel(FEUILLE_PERFORMANCE, 1)));
        assertThat(rapport.erreurs().get(0).message()).contains("100");
        assertThat(rapport.calcul()).isNotNull();
    }

    @Test
    @Order(7)
    void un_fichier_illisible_rend_un_echec_sans_erreur_500() throws Exception {
        MockMultipartFile corrompu = new MockMultipartFile("fichier", "classeur-test.xlsx", XLSX,
                "pas un classeur".getBytes());

        Rapport rapport = vue(mockMvc.perform(envoi(corrompu, "2026", "3").with(csrf()))
                .andExpect(status().isOk())).rapport();

        assertThat(rapport.statut()).isEqualTo("ECHEC");
        assertThat(rapport.message()).startsWith("Fichier illisible");
        assertThat(rapport.calcul()).isNull();
        assertThat(rapport.trimestreVide()).isFalse();
    }

    @Test
    @Order(8)
    void un_import_sans_aucune_ligne_signale_le_trimestre_ouvert_mais_vide() throws Exception {
        Rapport rapport = vue(mockMvc.perform(envoi(ClasseurDeTest.vide().fichier("classeur-test.xlsx"),
                        "2027", "2").param("calcul", "true").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ne contient aucune donnée")))).rapport();

        // AUDIT_REPORT B5 : l'import ouvre le trimestre avant d'ecrire les donnees.
        assertThat(rapport.statut()).isEqualTo("ECHEC");
        assertThat(rapport.trimestreVide()).isTrue();
        assertThat(trimestreRepository.findByNumeroAndAnnee(2, 2027)).isPresent();
    }

    // ------------------------------------------------------------ refus avant l'import

    @Test
    @Order(9)
    void seuls_les_xlsx_sont_acceptes() throws Exception {
        long imports = importExcelRepository.count();
        MockMultipartFile csv = new MockMultipartFile("fichier", "notes.csv", "text/csv", "a;b".getBytes());

        ImportPageView vue = vue(mockMvc.perform(envoi(csv, "2026", "3").with(csrf())).andExpect(status().isOk()));

        assertThat(vue.erreur()).contains("Seuls les classeurs Excel .xlsx", "notes.csv");
        assertThat(vue.rapport()).isNull();
        assertThat(importExcelRepository.count()).isEqualTo(imports);
    }

    @Test
    @Order(10)
    void trimestre_date_et_fichier_manquant_sont_refuses_avec_un_message() throws Exception {
        MockMultipartFile classeur = ClasseurDeTest.complet().fichier("classeur-test.xlsx");
        assertThat(vue(mockMvc.perform(envoi(classeur, "1999", "3").with(csrf()))).erreur())
                .startsWith("Trimestre invalide");
        assertThat(vue(mockMvc.perform(envoi(classeur, "2026", "abc").with(csrf()))).erreur())
                .startsWith("Trimestre invalide");
        assertThat(vue(mockMvc.perform(envoi(classeur, "2026", "3").param("dateReference", "15/09")
                .with(csrf()))).erreur()).startsWith("Date de référence invalide");
        assertThat(vue(mockMvc.perform(multipart("/import").param("annee", "2026").param("numero", "3")
                .with(csrf()))).erreur()).startsWith("Choisir le classeur");
    }

    @Test
    @Order(11)
    void un_fichier_trop_volumineux_est_refuse_avec_un_message() throws Exception {
        MockMultipartFile gros = new MockMultipartFile("fichier", "gros.xlsx", XLSX, new byte[300 * 1024]);

        assertThat(vue(mockMvc.perform(envoi(gros, "2026", "3").with(csrf()))).erreur())
                .isEqualTo("Fichier trop volumineux : 200 Ko au maximum. Rien n'a été importé.");
        // Retour du conseil quand Tomcat refuse le fichier avant le controleur (voir ImportPageHttpTest).
        mockMvc.perform(get("/import").param("erreur", "taille"))
                .andExpect(content().string(containsString("Fichier trop volumineux : 200 Ko au maximum.")));
    }

    @Test
    @Order(12)
    void le_conseil_de_taille_ne_traite_que_la_page_d_import() {
        TailleImportAdvice conseil = new TailleImportAdvice();
        MaxUploadSizeExceededException depassement = new MaxUploadSizeExceededException(1024);

        assertThat(conseil.fichierTropVolumineux(depassement, new MockHttpServletRequest("POST", "/import")))
                .isEqualTo("redirect:/import?erreur=taille");
        assertThatThrownBy(() -> conseil.fichierTropVolumineux(depassement,
                new MockHttpServletRequest("POST", "/api/imports"))).isSameAs(depassement);
    }

    @Test
    @Order(13)
    void sans_jeton_csrf_l_envoi_est_refuse() throws Exception {
        long imports = importExcelRepository.count();

        mockMvc.perform(envoi(ClasseurDeTest.complet().fichier("classeur-test.xlsx"), "2026", "3"))
                .andExpect(status().isForbidden());

        assertThat(importExcelRepository.count()).isEqualTo(imports);
    }

    @Test
    @Order(14)
    void un_calcul_en_cours_sur_le_trimestre_bloque_l_import_avec_un_message() throws Exception {
        long imports = importExcelRepository.count();
        Trimestre t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        // Le verrou est reentrant : il doit etre tenu par un autre fil que celui de la requete.
        Thread calcul = new Thread(() -> verrou.executer(t3, () -> {
            verrouPris.countDown();
            try {
                return liberer.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }));
        calcul.start();
        try {
            assertThat(verrouPris.await(10, TimeUnit.SECONDS)).isTrue();

            ImportPageView vue = vue(mockMvc.perform(envoi(ClasseurDeTest.complet().fichier("classeur-test.xlsx"),
                    "2026", "3").param("calcul", "true").with(csrf())).andExpect(status().isOk())
                    .andExpect(content().string(containsString("Un calcul est déjà en cours pour T3 2026"))));

            assertThat(vue.rapport()).isNull();
            assertThat(importExcelRepository.count()).isEqualTo(imports);
        } finally {
            liberer.countDown();
            calcul.join(10_000);
        }
    }

    // ------------------------------------------------------------ outils

    private static MockMultipartHttpServletRequestBuilder envoi(MockMultipartFile fichier, String annee,
                                                                 String numero) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/import").file(fichier)
                .param("annee", annee).param("numero", numero);
    }

    private static ImportPageView vue(org.springframework.test.web.servlet.ResultActions resultat) {
        return (ImportPageView) resultat.andReturn().getModelAndView().getModel().get("vue");
    }

    private void trimestre(int annee, int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(annee);
        trimestre.setNumero(numero);
        trimestre.setDateReference(Trimestre.dernierJour(annee, numero));
        trimestreRepository.save(trimestre);
    }
}
