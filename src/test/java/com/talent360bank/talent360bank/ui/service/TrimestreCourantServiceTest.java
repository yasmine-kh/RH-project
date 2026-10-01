package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Trimestre affiche par les ecrans : le demande, sinon le plus recent qui a
 * des scores (AUDIT_REPORT B5 : un trimestre ouvert mais vide ne vide plus
 * les ecrans).
 */
@ExtendWith(MockitoExtension.class)
class TrimestreCourantServiceTest {

    @Mock
    private TrimestreRepository trimestreRepository;
    @Mock
    private ScoreRepository scoreRepository;

    private TrimestreCourantService service;
    private Trimestre t4;
    private Trimestre t3;
    private Trimestre t2;

    @BeforeEach
    void init() {
        service = new TrimestreCourantService(trimestreRepository, scoreRepository);
        t4 = trimestre(4, 4);
        t3 = trimestre(3, 3);
        t2 = trimestre(2, 2);
    }

    private static Trimestre trimestre(int id, int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setIdTrimestre(id);
        trimestre.setAnnee(2026);
        trimestre.setNumero(numero);
        return trimestre;
    }

    /** T4 ouvert (import pas encore fait), T3 et T2 calcules. */
    private void t4VideT3EtT2Calcules() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t4, t3, t2));
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of(2, 3));
    }

    @Test
    void sans_choix_c_est_le_plus_recent_qui_a_des_scores_pas_le_plus_recent_cree() {
        t4VideT3EtT2Calcules();

        assertThat(service.resoudre(null)).containsSame(t3);
        assertThat(service.resoudre("  ")).containsSame(t3);
    }

    @Test
    void le_trimestre_demande_est_affiche_meme_sans_scores() {
        t4VideT3EtT2Calcules();

        assertThat(service.resoudre("2026-2")).containsSame(t2);
        assertThat(service.resoudre("2026-4")).containsSame(t4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-1", "2026-9", "T3 2026", "2026-03-31", "abc"})
    void un_trimestre_inconnu_ou_mal_ecrit_rend_404(String demande) {
        t4VideT3EtT2Calcules();

        assertThatThrownBy(() -> service.resoudre(demande))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining(demande);
    }

    @Test
    void sans_aucun_trimestre_calcule_c_est_le_plus_recent_cree() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t4, t3));
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of());

        assertThat(service.resoudre(null)).containsSame(t4);
    }

    @Test
    void sans_aucun_trimestre_il_n_y_a_rien_a_afficher() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of());
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of());

        assertThat(service.resoudre(null)).isEmpty();
        assertThat(service.lister()).isEmpty();
    }

    @Test
    void la_liste_du_selecteur_dit_quels_trimestres_ont_des_donnees() {
        t4VideT3EtT2Calcules();

        assertThat(service.lister()).containsExactly(
                new OptionTrimestre("2026-4", "T4 2026", 2026, 4, false),
                new OptionTrimestre("2026-3", "T3 2026", 2026, 3, true),
                new OptionTrimestre("2026-2", "T2 2026", 2026, 2, true));
    }

    @Test
    void la_selection_pose_les_attributs_trimestre_et_trimestres() {
        t4VideT3EtT2Calcules();
        ExtendedModelMap model = new ExtendedModelMap();

        service.selectionner(null).exposer(model);

        assertThat(model.getAttribute("trimestre")).isEqualTo(new OptionTrimestre("2026-3", "T3 2026", 2026, 3, true));
        assertThat(model.getAttribute("trimestres")).asList().hasSize(3);
    }

    @Test
    void sans_trimestre_l_attribut_trimestre_est_null() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of());
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        service.selectionner(null).exposer(model);

        assertThat(model.containsAttribute("trimestre")).isTrue();
        assertThat(model.getAttribute("trimestre")).isNull();
        assertThat(model.getAttribute("trimestres")).asList().isEmpty();
    }
}
