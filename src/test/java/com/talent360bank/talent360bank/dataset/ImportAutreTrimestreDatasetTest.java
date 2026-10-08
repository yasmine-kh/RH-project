package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.ImportPageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le vrai classeur importe sur T4 2026 depuis la page /import, comme le RH : refuse en entier.
 * Le classeur annonce sa periode en 00_DASHBOARD A2 ("... au 15/09/2026" = T3 2026) et l'import
 * refuse un fichier qui annonce un autre trimestre que celui choisi (PeriodeClasseur, ImportService).
 * Comportement voulu a ce jour : ce test le documente, il ne le change pas.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:import-autre-trimestre;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@EnabledIf("classeurPresent")
@WithMockUser(roles = "RH")
class ImportAutreTrimestreDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final String MESSAGE = "Le classeur porte une autre période que T4 2026, rien n'a été importé : "
            + "T3 2026 (00_DASHBOARD A2 : \"15/09/2026\"). Vérifier le fichier ou le trimestre choisi.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    private MvcResult envoyer(String numero, boolean simulation) throws Exception {
        return mockMvc.perform(multipart("/import")
                        .file(new MockMultipartFile("fichier", FICHIER.getFileName().toString(),
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                Files.readAllBytes(FICHIER)))
                        .param("annee", "2026").param("numero", numero).param("calcul", "true")
                        .param("simulation", String.valueOf(simulation)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    void le_classeur_date_du_15_09_2026_est_refuse_sur_t4_2026() throws Exception {
        // Simulation : meme refus, rien d'ecrit.
        ImportPageView simulation = (ImportPageView) envoyer("4", true).getModelAndView().getModel().get("vue");
        assertThat(simulation.rapport().statut()).isEqualTo("ECHEC");
        assertThat(simulation.rapport().message()).isEqualTo(MESSAGE);

        MvcResult resultat = envoyer("4", false);
        ImportPageView vue = (ImportPageView) resultat.getModelAndView().getModel().get("vue");
        // Pas une erreur du formulaire : le bilan de l'import, en echec complet, avec la raison.
        assertThat(vue.erreur()).isNull();
        assertThat(vue.rapport().statut()).isEqualTo("ECHEC");
        assertThat(vue.rapport().message()).isEqualTo(MESSAGE);
        assertThat(vue.rapport().nbLignes()).isZero();
        assertThat(vue.rapport().calcul()).isNull();
        assertThat(resultat.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("autre période que T4 2026", "00_DASHBOARD A2");
        // Refuse avant toute ecriture : ni trimestre T4, ni collaborateur ; une ligne ECHEC au journal.
        assertThat(trimestreRepository.findByNumeroAndAnnee(4, 2026)).isEmpty();
        assertThat(collaborateurRepository.count()).isZero();
        assertThat(vue.journal()).singleElement().satisfies(ligne -> {
            assertThat(ligne.statut()).isEqualTo("ECHEC");
            assertThat(ligne.trimestre()).isNull();
            assertThat(ligne.message()).isEqualTo(MESSAGE);
        });

        // Le meme fichier passe sur T3 2026, le trimestre qu'il annonce.
        ImportPageView t3 = (ImportPageView) envoyer("3", false).getModelAndView().getModel().get("vue");
        assertThat(t3.rapport().statut()).isEqualTo("SUCCES");
    }
}
