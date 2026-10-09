package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Libelles repris a l'identique du classeur : readiness (09_SUCCESSION!L, 00_PARAMETRES), couverture
 * (formule de 08_POSTES_CRITIQUES!I) et meilleur matching a 0 sans successeur (08!H, IFERROR(MAXIFS, 0)).
 */
class LibellesClasseurTest {

    @Test
    void la_readiness_porte_les_libelles_du_classeur() {
        assertThat(Arrays.stream(NiveauReadiness.values()).map(NiveauReadiness::getLibelle))
                .containsExactly("Ready Now", "Ready < 1 an", "Ready 1-2 ans", "Ready > 2 ans");
    }

    @Test
    void la_couverture_porte_les_libelles_de_la_formule_08_I() {
        assertThat(NiveauCouverture.ALERTE.getLibelle()).isEqualTo("Aucun successeur - ALERTE");
        assertThat(NiveauCouverture.READY_NOW.getLibelle()).isEqualTo("Couverte - Ready Now");
        assertThat(NiveauCouverture.MOINS_1_AN.getLibelle()).isEqualTo("Couverte - <1 an");
    }

    @Test
    void un_poste_sans_successeur_a_un_meilleur_matching_de_0_comme_PST13() {
        CouverturePoste sans = new CouverturePoste(new Poste(), 0, List.of(), List.of(), NiveauCouverture.ALERTE);

        assertThat(sans.meilleurMatching()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sans.libelleCouverture()).isEqualTo("Aucun successeur - ALERTE");
    }

    @Test
    void des_successeurs_sous_le_minimum_gardent_un_libelle_distinct() {
        CouverturePoste insuffisant = new CouverturePoste(new Poste(), 1, List.of(), List.of(),
                NiveauCouverture.ALERTE);

        assertThat(insuffisant.libelleCouverture()).isEqualTo(CouverturePoste.LIBELLE_SUCCESSEURS_INSUFFISANTS);
    }
}
