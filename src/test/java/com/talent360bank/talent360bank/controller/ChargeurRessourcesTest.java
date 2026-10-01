package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargeurRessourcesTest {

    @Mock
    private TrimestreRepository trimestreRepository;
    @Mock
    private CollaborateurRepository collaborateurRepository;
    @Mock
    private ScoreRepository scoreRepository;

    private ChargeurRessources chargeur;
    private Trimestre t4;
    private Trimestre t3;

    @BeforeEach
    void init() {
        chargeur = new ChargeurRessources(trimestreRepository, collaborateurRepository,
                new TrimestreCourantService(trimestreRepository, scoreRepository));
        t4 = trimestre(4, 4);
        t3 = trimestre(3, 3);
    }

    private static Trimestre trimestre(int id, int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setIdTrimestre(id);
        trimestre.setNumero(numero);
        trimestre.setAnnee(2026);
        return trimestre;
    }

    /**
     * T4 ouvert mais pas encore importe : l'API rend T3, comme les ecrans
     * (TrimestreCourantService), pas le plus recent cree.
     */
    @Test
    void sans_annee_ni_numero_c_est_le_plus_recent_qui_a_des_scores() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t4, t3));
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of(3));

        assertThat(chargeur.exigerTrimestreOuDernier(null, null)).isSameAs(t3);
    }

    @Test
    void sans_aucun_trimestre_calcule_c_est_le_plus_recent_cree() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t4, t3));
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of());

        assertThat(chargeur.exigerTrimestreOuDernier(null, null)).isSameAs(t4);
    }

    @Test
    void sans_aucun_trimestre_en_base_la_ressource_est_introuvable() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of());
        when(scoreRepository.findIdsTrimestresAvecScores()).thenReturn(List.of());

        assertThatThrownBy(() -> chargeur.exigerTrimestreOuDernier(null, null))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void le_trimestre_demande_est_rendu() {
        when(trimestreRepository.findByNumeroAndAnnee(3, 2026)).thenReturn(Optional.of(t3));

        assertThat(chargeur.exigerTrimestreOuDernier(2026, 3)).isSameAs(t3);
    }

    @Test
    void un_seul_des_deux_est_refuse() {
        assertThatThrownBy(() -> chargeur.exigerTrimestreOuDernier(2026, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chargeur.exigerTrimestreOuDernier(null, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
