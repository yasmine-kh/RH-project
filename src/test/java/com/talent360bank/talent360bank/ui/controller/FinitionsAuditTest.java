package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Finitions de l'audit (round 1) : pages retirees, libelles et messages en francais accentue,
 * commentaires de template non envoyes au navigateur. Base vide ; la meme verification sur les vraies
 * donnees est dans ModulesPagesDatasetTest.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:finitions-audit;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@WithMockUser(roles = "RH")
class FinitionsAuditTest {

    @Autowired
    private MockMvc mockMvc;

    private String page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ pages retirees

    @Test
    void carriere_et_mobilite_renvoie_a_l_accueil_et_n_est_plus_dans_aucun_menu() throws Exception {
        mockMvc.perform(get("/carriere-mobilite")).andExpect(redirectedUrl("/"));
        assertThat(page("/")).doesNotContain("Carrière &amp; Mobilité", "Carrière & Mobilité", "/carriere-mobilite");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mon-engagement", "/auto-evaluation", "/evaluation-manager",
            "/api/dashboard/dg?annee=2026&numero=3"})
    void les_pages_et_l_api_retirees_n_existent_plus(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void les_pages_restant_a_construire_le_disent() throws Exception {
        assertThat(page("/engagement")).contains("En cours de développement");
        assertThat(page("/historique")).contains("En cours de développement");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/collaborateurs", "/9box", "/viviers", "/postes-critiques", "/competences",
            "/alertes", "/notifications", "/campagne", "/managers", "/entites", "/import", "/parametres",
            "/comite-talent"})
    void aucune_autre_page_n_est_en_cours_de_developpement(String url) throws Exception {
        assertThat(page(url).toLowerCase()).doesNotContain("en cours de développement", "en cours de developpement");
    }

    @Test
    void les_notifications_n_ont_plus_d_interrupteurs() throws Exception {
        assertThat(page("/notifications")).doesNotContain("type=\"checkbox\"", "class=\"switch");
    }

    // ------------------------------------------------------------ francais

    @Test
    void les_libelles_du_moteur_sont_accentues() {
        assertThat(NiveauVigilance.MODEREE.getLibelle()).isEqualTo("Modérée");
        assertThat(NiveauVigilance.ELEVEE.getLibelle()).isEqualTo("Élevée");
        assertThat(SeveriteAlerte.ELEVEE.getLibelle()).isEqualTo("Élevée");
        assertThat(com.talent360bank.talent360bank.entity.CategoriePerformance.A_RENFORCER.getLibelle())
                .isEqualTo("À renforcer");
        assertThat(com.talent360bank.talent360bank.entity.CategoriePotentiel.ELEVE.getLibelle()).isEqualTo("Élevé");
        assertThat(com.talent360bank.talent360bank.service.enums.StatutGapCompetence.MAITRISE.getLibelle())
                .isEqualTo("Maîtrisé");
        assertThat(com.talent360bank.talent360bank.service.enums.StatutGapCompetence.A_DEVELOPPER.getLibelle())
                .isEqualTo("À développer");
    }

    @Test
    void les_statuts_d_import_sont_en_clair() {
        assertThat(StatutImport.libelle("SUCCES")).isEqualTo("Réussi");
        assertThat(StatutImport.libelle("PARTIEL")).isEqualTo("Partiel");
        assertThat(StatutImport.libelle("ECHEC")).isEqualTo("Échec");
        assertThat(StatutImport.libelle(null)).isEqualTo("—");
        assertThat(ImportService.MESSAGE_ECHEC).isEqualTo("L'import a échoué. Vérifiez le fichier et réessayez.");
    }

    @Test
    void la_page_403_est_accentuee() throws Exception {
        assertThat(page("/erreur/403")).contains("Accès refusé", "Se déconnecter").doesNotContain("Acces refuse");
    }

    @Test
    void la_carte_ready_now_dit_ce_qu_elle_compte() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/talent360bank/talent360bank/ui/service/"
                + "TableauDeBordInteractifService.java"));
        assertThat(source).contains("\"🟢 Successions Ready Now\"").doesNotContain("\"🟢 Ready Now\"");
    }

    // ------------------------------------------------------------ templates

    /** Commentaire HTML (envoye au navigateur) qui n'est pas un commentaire Thymeleaf. */
    private static final Pattern COMMENTAIRE_HTML = Pattern.compile("<!--(?!/\\*)");

    @Test
    void les_commentaires_des_templates_ne_partent_pas_au_navigateur() throws Exception {
        List<String> fautifs;
        try (Stream<Path> fichiers = Files.walk(Path.of("src/main/resources/templates"))) {
            fautifs = fichiers.filter(f -> f.toString().endsWith(".html")).filter(f -> {
                try {
                    Matcher m = COMMENTAIRE_HTML.matcher(Files.readString(f));
                    return m.find();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).map(Path::toString).toList();
        }
        assertThat(fautifs).isEmpty();
        assertThat(page("/")).doesNotContain("<!--");
    }

    @Test
    void les_talents_sont_toujours_proposes_ou_valides() throws IOException {
        try (Stream<Path> fichiers = Files.walk(Path.of("src/main/resources/templates"))) {
            fichiers.filter(f -> f.toString().endsWith(".html")).forEach(f -> {
                try {
                    assertThat(Files.readString(f)).as(f.toString())
                            .doesNotContain(">⭐ Talents<", "<span>Talents</span>", "> talents</span>", ">Talent</span>");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
