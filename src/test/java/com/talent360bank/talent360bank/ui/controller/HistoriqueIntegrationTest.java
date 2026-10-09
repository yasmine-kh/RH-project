package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEvenement;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.JournalEvenementRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ClasseurDeTest;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.JournalRattrapage;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.ui.model.Historique;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historique : un evenement par import, changement de reglages et de collaborateur (avec l'auteur),
 * rattrapage sans doublon depuis import_excel et les decisions datees, filtres de /historique, et la
 * section Historique du Talent Passport. Base en memoire, classeur de test (sans le vrai classeur).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:historique;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(username = HistoriqueIntegrationTest.LOGIN, roles = "RH")
class HistoriqueIntegrationTest {

    static final String LOGIN = "rh.historique";
    private static final int ANNEE = 2090;
    private static final int NUMERO = 3;
    private static final String T = ANNEE + "-" + NUMERO;
    private static final String E1 = ClasseurDeTest.E1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private JournalEvenementRepository journalRepository;
    @Autowired
    private ImportExcelRepository importExcelRepository;
    @Autowired
    private ValidationComiteRepository validationComiteRepository;
    @Autowired
    private JournalRattrapage journalRattrapage;

    @BeforeAll
    void compte() {
        utilisateurRepository.save(new Utilisateur(LOGIN, "non-utilise", Role.RH));
    }

    private List<JournalEvenement> evenements(TypeEvenement type) {
        return journalRepository.rechercher(type, null, null, null, null,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
    }

    private Historique historique(String url) throws Exception {
        return (Historique) page(url).getModelAndView().getModel().get("vue");
    }

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ ecriture au moment de l'action

    @Test
    @Order(1)
    void un_import_ecrit_un_evenement_avec_son_statut_et_son_auteur() {
        assertThat(importService.importer(ClasseurDeTest.complet().fichier("classeur-test.xlsx"), ANNEE, NUMERO)
                .statut()).isEqualTo(StatutImport.SUCCES);
        calculTrimestreService.calculer(trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE).orElseThrow());

        List<JournalEvenement> imports = evenements(TypeEvenement.IMPORT);
        assertThat(imports).singleElement().satisfies(e -> {
            assertThat(e.getDescription()).startsWith("Import du classeur « classeur-test.xlsx » (T3 2090) : Réussi — ");
            assertThat(e.getUtilisateur().getLogin()).isEqualTo(LOGIN);
            assertThat(e.getTrimestre().getAnnee()).isEqualTo(ANNEE);
            assertThat(e.getLien()).isEqualTo("/import");
            assertThat(e.getReference()).isEqualTo("import_excel:"
                    + importExcelRepository.findAllRecentsDabord().get(0).getIdImport());
            assertThat(e.getDateEvenement().toLocalDate()).isEqualTo(LocalDate.now());
        });
        // Un import refuse (fichier illisible) est journalise aussi, avec le statut Echec.
        importService.importer(new org.springframework.mock.web.MockMultipartFile("fichier", "casse.xlsx", null,
                new byte[]{1, 2, 3}), ANNEE, NUMERO);
        assertThat(evenements(TypeEvenement.IMPORT)).hasSize(2).first().satisfies(e ->
                assertThat(e.getDescription()).contains("« casse.xlsx »", ": Échec", "Fichier illisible"));
    }

    @Test
    @Order(2)
    void un_changement_de_reglage_ecrit_un_evenement() throws Exception {
        mockMvc.perform(put("/api/trimestres/" + ANNEE + "/" + NUMERO)
                        .header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateReference\":\"2090-09-15\"}"))
                .andExpect(status().isOk());
        assertThat(evenements(TypeEvenement.PARAMETRES)).singleElement().satisfies(e -> {
            assertThat(e.getDescription()).isEqualTo("Date de référence de T3 2090 : 15/09/2090.");
            assertThat(e.getUtilisateur().getLogin()).isEqualTo(LOGIN);
            assertThat(e.getLien()).isEqualTo("/parametres?trimestre=" + T);
        });
        // Questionnaire : enregistrer les dimensions est aussi un changement de reglage.
        mockMvc.perform(post("/parametres/questionnaire").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("trimestre", T))
                .andExpect(status().is3xxRedirection());
        assertThat(evenements(TypeEvenement.PARAMETRES)).hasSize(2).first().satisfies(e ->
                assertThat(e.getDescription()).startsWith("Questionnaire d'engagement : 0 question(s)"));
    }

    @Test
    @Order(3)
    void ajouter_puis_supprimer_un_collaborateur_ecrit_deux_evenements() throws Exception {
        mockMvc.perform(post("/api/collaborateurs").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCollaborateur\":\"TSTNEW\",\"nom\":\"Nouveau\",\"prenom\":\"Test\","
                                + "\"dateEntree\":\"2024-01-01\",\"statut\":\"ACTIF\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/collaborateurs/TSTNEW").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
                .andExpect(status().isNoContent());
        assertThat(evenements(TypeEvenement.COLLABORATEUR)).extracting(JournalEvenement::getDescription)
                .containsExactly("Collaborateur TSTNEW (Test Nouveau) supprimé par l'API : passé INACTIF, "
                                + "ses données sont conservées.",
                        "Collaborateur TSTNEW (Test Nouveau) ajouté par l'API.");
        assertThat(evenements(TypeEvenement.COLLABORATEUR)).allSatisfy(e -> {
            assertThat(e.getMatricule()).isEqualTo("TSTNEW");
            assertThat(e.getLien()).isEqualTo("/fiche-collaborateur?matricule=TSTNEW");
        });
    }

    // ------------------------------------------------------------ rattrapage

    @Test
    @Order(4)
    void le_rattrapage_reprend_les_imports_et_les_decisions_datees_sans_doublon() {
        // Une decision du Comite saisie avant l'historique (datee, sans evenement).
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE).orElseThrow();
        ValidationComite decision = validationComiteRepository.findDecision(E1, trimestre).orElseThrow();
        decision.decider(StatutValidationComite.OUI, LocalDateTime.of(2090, 10, 1, 9, 0),
                utilisateurRepository.findByLogin(LOGIN).orElseThrow(), "revue de rentrée");
        validationComiteRepository.save(decision);
        // Les imports aussi, comme sur une base d'avant l'historique.
        journalRepository.deleteAll(evenements(TypeEvenement.IMPORT));

        assertThat(journalRattrapage.rattraper()).isEqualTo(3);
        assertThat(evenements(TypeEvenement.IMPORT)).hasSize(2)
                .allSatisfy(e -> assertThat(e.getDateEvenement()).isEqualTo(LocalDate.now().atStartOfDay()));
        assertThat(evenements(TypeEvenement.DECISION_TALENT)).singleElement().satisfies(e -> {
            assertThat(e.getDateEvenement()).isEqualTo(LocalDateTime.of(2090, 10, 1, 9, 0));
            assertThat(e.getMatricule()).isEqualTo(E1);
            assertThat(e.getUtilisateur().getLogin()).isEqualTo(LOGIN);
            assertThat(e.getDescription()).contains("Comité Talent : ", "(" + E1 + ") — talent validé (Oui)",
                    "« revue de rentrée »");
            assertThat(e.getLien()).isEqualTo("/fiche-collaborateur?matricule=" + E1 + "&trimestre=" + T);
        });
        // Une seconde fois : rien de plus.
        assertThat(journalRattrapage.rattraper()).isZero();
    }

    // ------------------------------------------------------------ ecran /historique

    @Test
    @Order(5)
    void la_page_liste_du_plus_recent_au_plus_ancien_et_filtre() throws Exception {
        Historique tout = historique("/historique");
        assertThat(tout.total()).isEqualTo(7);
        assertThat(tout.lignes()).extracting(Historique.Ligne::date).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        String page = html(page("/historique"));
        assertThat(page).contains("id=\"historique-table\"", "Import du classeur « classeur-test.xlsx »", LOGIN,
                        "href=\"/fiche-collaborateur?matricule=TSTNEW\"")
                .doesNotContain("en cours de développement");

        assertThat(historique("/historique?type=IMPORT").lignes()).hasSize(2)
                .allSatisfy(l -> assertThat(l.type()).isEqualTo("IMPORT"));
        assertThat(historique("/historique?type=DECISION_TALENT").lignes()).singleElement()
                .satisfies(l -> assertThat(l.matricule()).isEqualTo(E1));
        // Trimestre : les evenements sans trimestre (collaborateurs, questionnaire, fichier illisible refuse
        // avant de connaitre son trimestre) en sont exclus.
        assertThat(historique("/historique?trimestre=" + T).lignes()).hasSize(3)
                .allSatisfy(l -> assertThat(l.trimestre()).isEqualTo("T3 2090"));
        assertThat(historique("/historique?q=tsTnew").lignes()).hasSize(2);
        assertThat(historique("/historique?q=casse.xlsx").lignes()).singleElement();
        // Periode : la decision reprise est datee du 01/10/2090.
        assertThat(historique("/historique?du=2090-10-01&au=2090-10-01").lignes()).singleElement()
                .satisfies(l -> assertThat(l.type()).isEqualTo("DECISION_TALENT"));
        assertThat(historique("/historique?du=" + LocalDate.now() + "&type=COLLABORATEUR").total()).isEqualTo(2);
        assertThat(html(page("/historique?du=2000-01-01&au=2000-01-02"))).contains("Aucun événement pour ces filtres.");
        // Filtres illisibles : message, pas d'erreur.
        assertThat(historique("/historique?du=01-13-2090").erreur()).startsWith("Date illisible");
        assertThat(historique("/historique?du=2090-10-02&au=2090-10-01").erreur())
                .isEqualTo("La période est inversée : la date de fin précède la date de début.");
        assertThat(historique("/historique?type=INCONNU").total()).isEqualTo(7);
        mockMvc.perform(get("/historique?trimestre=2031-1")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ Talent Passport

    @Test
    @Order(6)
    void la_fiche_montre_les_scores_de_chaque_trimestre_et_ses_evenements() throws Exception {
        String page = html(page("/fiche-collaborateur?matricule=" + E1 + "&trimestre=" + T));
        String section = page.substring(page.indexOf("id=\"historique\""));
        section = section.substring(0, section.indexOf("id=\"questionnaire\""));
        assertThat(section).contains("Un seul trimestre importé", "id=\"historique-scores\"", "data-trimestre=\"" + T + "\"",
                "T3 2090", "talent validé (Oui)", "revue de rentrée", "/historique?q=" + E1);
        com.talent360bank.talent360bank.ui.model.HistoriqueCollaborateur h =
                (com.talent360bank.talent360bank.ui.model.HistoriqueCollaborateur) page("/fiche-collaborateur?matricule="
                        + E1 + "&trimestre=" + T).getModelAndView().getModel().get("historiqueCollaborateur");
        assertThat(h.trimestres()).singleElement().satisfies(t -> {
            assertThat(t.performance()).isNotNull();
            assertThat(t.potentiel()).isNotNull();
            assertThat(t.courant()).isTrue();
        });
        assertThat(h.unSeulTrimestre()).isTrue();
    }
}
