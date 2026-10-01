package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaitsVigilanceTest {

    @ParameterizedTest
    @EnumSource(SignalVigilance.class)
    void aucun_fait_ne_declare_aucun_signal(SignalVigilance signal) {
        assertThat(FaitsVigilance.AUCUN.declare(signal)).isNull();
        assertThat(FaitsVigilance.AUCUN.estDeclare(signal)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SignalVigilance.class, names = "ENGAGEMENT_FAIBLE", mode = EnumSource.Mode.EXCLUDE)
    void declarer_par_code_ne_touche_que_son_signal(SignalVigilance signal) {
        FaitsVigilance faits = FaitsVigilance.builder().declarer(signal, true).build();

        for (SignalVigilance autre : SignalVigilance.values()) {
            assertThat(faits.estDeclare(autre)).as(autre.name()).isEqualTo(autre == signal);
        }
    }

    @Test
    void les_setters_nommes_alimentent_les_bons_drapeaux() {
        FaitsVigilance faits = FaitsVigilance.builder()
                .sansMobilite4Ans(true).mobiliteNonTraitee(false).sansDeveloppementRecent(null)
                .baissePerformance(true).faibleReconnaissance(false).formationNonFaite(true)
                .build();

        assertThat(faits.getSansMobilite4Ans()).isTrue();
        assertThat(faits.declare(SignalVigilance.MOBILITE_NON_TRAITEE)).isFalse();
        assertThat(faits.getSansDeveloppementRecent()).isNull();
        assertThat(faits.declare(SignalVigilance.BAISSE_PERFORMANCE)).isTrue();
        assertThat(faits.getFaibleReconnaissance()).isFalse();
        assertThat(faits.declare(SignalVigilance.FORMATION_NON_FAITE)).isTrue();
    }

    @Test
    void l_engagement_ne_se_declare_pas_dans_les_faits() {
        assertThatThrownBy(() -> FaitsVigilance.builder().declarer(SignalVigilance.ENGAGEMENT_FAIBLE, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_source_rend_aucun_fait_pour_un_collaborateur_absent_ou_une_map_nulle() {
        FaitsVigilance declares = FaitsVigilance.builder().formationNonFaite(true).build();
        FaitsVigilanceSource source = trimestre -> Map.of("E001", declares);
        FaitsVigilanceSource muette = trimestre -> null;

        assertThat(source.faits("E001", new Trimestre())).isSameAs(declares);
        assertThat(source.faits("E002", new Trimestre())).isSameAs(FaitsVigilance.AUCUN);
        assertThat(muette.faits("E001", new Trimestre())).isSameAs(FaitsVigilance.AUCUN);
    }
}
