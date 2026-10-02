package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.ImportPageView;
import com.talent360bank.talent360bank.ui.model.ImportPageView.Rapport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le vrai classeur importe par la page /import, comme le ferait le RH : T3
 * 2026, date de reference 15/09/2026, calcul coche. Puis le tableau de bord
 * du trimestre, par le lien du bilan.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:import-page-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@EnabledIf("classeurPresent")
@WithMockUser(roles = "RH")
class ImportPageDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrimestreRepository trimestreRepository;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @Test
    void le_classeur_s_importe_et_se_calcule_depuis_la_page() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(FICHIER));

        ImportPageView vue = (ImportPageView) mockMvc.perform(multipart("/import").file(fichier)
                        .param("annee", "2026").param("numero", "3").param("calcul", "true")
                        .param("dateReference", "2026-09-15").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("vue");

        assertThat(vue.erreur()).isNull();
        Rapport rapport = vue.rapport();
        assertThat(rapport.statut()).isEqualTo("SUCCES");
        assertThat(rapport.nbErreurs()).isZero();
        assertThat(rapport.nbLignes()).isPositive();
        assertThat(rapport.feuilles()).hasSize(10).allSatisfy(feuille -> assertThat(feuille.presente()).isTrue());
        assertThat(rapport.desactives()).isEmpty();
        assertThat(rapport.erreurCalcul()).isNull();
        assertThat(rapport.calcul().scores()).isEqualTo(100);
        assertThat(rapport.calcul().placements()).isEqualTo(100);
        assertThat(rapport.lienTableauDeBord()).isEqualTo("/?trimestre=2026-3");
        assertThat(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow().getDateReference())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(vue.journal()).hasSize(1);
        assertThat(vue.journal().get(0).nbLignes()).isEqualTo(rapport.nbLignes());

        mockMvc.perform(get(rapport.lienTableauDeBord()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("T3 2026")));
    }
}
