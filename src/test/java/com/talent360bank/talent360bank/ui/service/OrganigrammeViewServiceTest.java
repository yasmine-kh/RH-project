package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.Organigramme.Branche;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrganigrammeViewServiceTest {

    private static NoeudEntite noeud(String code, String libelle, int effectif, NoeudEntite... enfants) {
        return new NoeudEntite(code, libelle, null, effectif, List.of(enfants));
    }

    @Test
    void un_enfant_unique_de_meme_effectif_est_fusionne_sur_une_ligne() {
        NoeudEntite siege = noeud("DIR:S/DEP:C/AG:S", "Siege", 12);
        Branche b = OrganigrammeViewService.fusionner(
                noeud("DIR:S", "Support", 12, noeud("DIR:S/DEP:C", "Siege - Casablanca", 12, siege)));
        assertThat(b.libelle()).isEqualTo("Support / Siege - Casablanca / Siege");
        assertThat(b.code()).isEqualTo("DIR:S/DEP:C/AG:S");
        assertThat(b.effectif()).isEqualTo(12);
        assertThat(b.enfants()).isEmpty();
    }

    @Test
    void plusieurs_enfants_ou_un_effectif_different_restent_des_niveaux() {
        Branche b = OrganigrammeViewService.fusionner(noeud("DIR:R", "Retail", 10,
                noeud("DIR:R/DEP:N", "Nord", 6, noeud("DIR:R/DEP:N/AG:1", "Agence 1", 6)),
                noeud("DIR:R/DEP:S", "Sud", 4)));
        assertThat(b.libelle()).isEqualTo("Retail");
        assertThat(b.enfants()).extracting(Branche::libelle).containsExactly("Nord / Agence 1", "Sud");
        assertThat(b.enfants()).extracting(Branche::effectif).containsExactly(6, 4);

        // Un enfant unique d'effectif plus petit (collaborateurs rattaches au parent) n'est pas fusionne.
        Branche partiel = OrganigrammeViewService.fusionner(
                noeud("DIR:A", "A", 9, noeud("DIR:A/DEP:B", "B", 5)));
        assertThat(partiel.libelle()).isEqualTo("A");
        assertThat(partiel.enfants()).extracting(Branche::libelle).containsExactly("B");
    }
}
