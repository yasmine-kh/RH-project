package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private ChargeurRessources chargeur;
    private Trimestre t3;

    @BeforeEach
    void init() {
        chargeur = new ChargeurRessources(trimestreRepository, collaborateurRepository);
        t3 = new Trimestre();
        t3.setNumero(3);
        t3.setAnnee(2026);
    }

    @Test
    void sans_annee_ni_numero_le_trimestre_le_plus_recent_est_rendu() {
        when(trimestreRepository.findTopByOrderByAnneeDescNumeroDesc()).thenReturn(Optional.of(t3));

        assertThat(chargeur.exigerTrimestreOuDernier(null, null)).isSameAs(t3);
    }

    @Test
    void sans_aucun_trimestre_en_base_la_ressource_est_introuvable() {
        when(trimestreRepository.findTopByOrderByAnneeDescNumeroDesc()).thenReturn(Optional.empty());

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
