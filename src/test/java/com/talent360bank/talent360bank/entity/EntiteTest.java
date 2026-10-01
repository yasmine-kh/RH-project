package com.talent360bank.talent360bank.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntiteTest {

    private final Entite direction = new Entite("Réseau Retail", TypeEntite.DIRECTION, null);
    private final Entite departement = new Entite("Reseau Retail - Nord", TypeEntite.DEPARTEMENT, direction);
    private final Entite region = new Entite("Tanger-Tétouan", TypeEntite.REGION, departement);
    private final Entite agence = new Entite("Agence Tanger Nord", TypeEntite.AGENCE, region);

    @Test
    void le_code_est_le_chemin_sans_accents_ni_ponctuation() {
        assertThat(direction.getCode()).isEqualTo("DIR:RESEAU_RETAIL");
        assertThat(agence.getCode()).isEqualTo(
                "DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_NORD/REG:TANGER_TETOUAN/AGE:AGENCE_TANGER_NORD");
    }

    @Test
    void un_meme_libelle_sous_deux_parents_donne_deux_codes() {
        Entite autreDepartement = new Entite("PME/PMI", TypeEntite.DEPARTEMENT, direction);
        Entite memeRegion = new Entite("Tanger-Tétouan", TypeEntite.REGION, autreDepartement);

        assertThat(memeRegion.getCode()).isNotEqualTo(region.getCode());
        assertThat(memeRegion).isNotEqualTo(region);
    }

    @Test
    void les_niveaux_se_lisent_en_remontant_l_arbre() {
        assertThat(agence.libelleDe(TypeEntite.DIRECTION)).isEqualTo("Réseau Retail");
        assertThat(agence.libelleDe(TypeEntite.REGION)).isEqualTo("Tanger-Tétouan");
        assertThat(agence.ancetre(TypeEntite.AGENCE)).isSameAs(agence);
        // Un niveau plus fin que l'entite n'existe pas au-dessus d'elle.
        assertThat(departement.libelleDe(TypeEntite.AGENCE)).isNull();
    }

    @Test
    void le_collaborateur_expose_ses_niveaux_par_son_entite() {
        Collaborateur collaborateur = new Collaborateur();
        assertThat(collaborateur.getDirection()).isNull();

        collaborateur.setEntite(region);

        assertThat(collaborateur.getDirection()).isEqualTo("Réseau Retail");
        assertThat(collaborateur.getDepartement()).isEqualTo("Reseau Retail - Nord");
        assertThat(collaborateur.getRegion()).isEqualTo("Tanger-Tétouan");
        assertThat(collaborateur.getAgence()).isNull();
    }
}
