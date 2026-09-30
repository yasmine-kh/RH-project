package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatCreationTrimestre;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ouverture d'un trimestre : creation, reglages par defaut, rejouabilite. */
@DataJpaTest
@Import(TrimestreService.class)
class TrimestreServiceJpaTest {

    private static final int ANNEE = 2091;

    @Autowired
    private TrimestreService service;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;

    private long trimestresDeLAnnee() {
        return trimestreRepository.findAll().stream().filter(t -> t.getAnnee() == ANNEE).count();
    }

    @Test
    void cree_le_trimestre_et_ses_reglages_par_defaut() {
        ResultatCreationTrimestre resultat = service.creerSiAbsent(ANNEE, 2);

        assertThat(resultat.trimestreCree()).isTrue();
        assertThat(resultat.parametreCree()).isTrue();
        assertThat(resultat.trimestre().getIdTrimestre()).isNotNull();
        Parametre parametre = parametreRepository.findByTrimestre(resultat.trimestre()).orElseThrow();
        Parametre defauts = Parametre.parDefaut(resultat.trimestre());
        assertThat(parametre.getPoidsPerformance().getPoidsObjectifs())
                .isEqualByComparingTo(defauts.getPoidsPerformance().getPoidsObjectifs());
    }

    @Test
    void rejouer_rend_le_meme_trimestre_sans_rien_recreer() {
        Trimestre premier = service.creerSiAbsent(ANNEE, 2).trimestre();

        ResultatCreationTrimestre second = service.creerSiAbsent(ANNEE, 2);

        assertThat(second.trimestreCree()).isFalse();
        assertThat(second.parametreCree()).isFalse();
        assertThat(second.trimestre().getIdTrimestre()).isEqualTo(premier.getIdTrimestre());
        assertThat(trimestresDeLAnnee()).isEqualTo(1);
        assertThat(parametreRepository.findAll()).filteredOn(p -> p.getTrimestre().getAnnee() == ANNEE).hasSize(1);
    }

    @Test
    void des_reglages_modifies_ne_sont_pas_remplaces() {
        Trimestre trimestre = service.creerSiAbsent(ANNEE, 3).trimestre();
        Parametre parametre = parametreRepository.findByTrimestre(trimestre).orElseThrow();
        parametre.getSeuilsTalent().setSeuilPerformance(new BigDecimal("86"));
        parametreRepository.saveAndFlush(parametre);

        service.creerSiAbsent(ANNEE, 3);

        assertThat(parametreRepository.findByTrimestre(trimestre).orElseThrow().getSeuilsTalent()
                .getSeuilPerformance()).isEqualByComparingTo("86");
    }

    @Test
    void un_trimestre_existant_sans_reglages_les_recoit() {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(ANNEE);
        trimestre.setNumero(4);
        trimestreRepository.save(trimestre);

        ResultatCreationTrimestre resultat = service.creerSiAbsent(ANNEE, 4);

        assertThat(resultat.trimestreCree()).isFalse();
        assertThat(resultat.parametreCree()).isTrue();
        assertThat(parametreRepository.existsByTrimestre(trimestre)).isTrue();
    }

    @Test
    void un_nouveau_trimestre_reprend_les_reglages_du_precedent() {
        Trimestre t1 = service.creerSiAbsent(ANNEE, 1).trimestre();
        Parametre reglagesT1 = parametreRepository.findByTrimestre(t1).orElseThrow();
        reglagesT1.getSeuilsTalent().setSeuilPerformance(new BigDecimal("86"));
        reglagesT1.getPoidsPerformance().setPoidsObjectifs(new BigDecimal("50"));
        reglagesT1.getPoidsPerformance().setPoidsCompetences(new BigDecimal("10"));
        parametreRepository.saveAndFlush(reglagesT1);

        Trimestre t2 = service.creerSiAbsent(ANNEE, 2).trimestre();

        Parametre reglagesT2 = parametreRepository.findByTrimestre(t2).orElseThrow();
        assertThat(reglagesT2.getIdParametre()).isNotEqualTo(reglagesT1.getIdParametre());
        assertThat(reglagesT2.getLibelle()).isEqualTo("Repris de T1 " + ANNEE);
        assertThat(reglagesT2.getSeuilsTalent().getSeuilPerformance()).isEqualByComparingTo("86");
        assertThat(reglagesT2.getPoidsPerformance().getPoidsObjectifs()).isEqualByComparingTo("50");

        // Copie, pas partage : modifier T2 ne touche pas T1.
        reglagesT2.getSeuilsTalent().setSeuilPerformance(new BigDecimal("90"));
        parametreRepository.saveAndFlush(reglagesT2);
        assertThat(parametreRepository.findByTrimestre(t1).orElseThrow().getSeuilsTalent().getSeuilPerformance())
                .isEqualByComparingTo("86");
    }

    @Test
    void sans_trimestre_precedent_les_reglages_sont_ceux_par_defaut() {
        // T1 2000 : aucun trimestre ne peut le preceder.
        Trimestre premier = service.creerSiAbsent(TrimestreService.ANNEE_MIN, 1).trimestre();

        Parametre reglages = parametreRepository.findByTrimestre(premier).orElseThrow();
        assertThat(reglages.getLibelle()).isEqualTo(Parametre.parDefaut(premier).getLibelle());
        assertThat(reglages.getSeuilsTalent().getSeuilPerformance()).isEqualByComparingTo("85");
    }

    @Test
    void la_copie_reprend_chaque_bloc_du_modele() {
        Trimestre source = new Trimestre();
        Parametre defauts = Parametre.parDefaut(source);

        Parametre copie = defauts.copiePour(new Trimestre());

        // Tous les blocs, y compris ceux ajoutes plus tard au modele.
        assertThat(copie).usingRecursiveComparison().ignoringFields("trimestre").isEqualTo(defauts);
        assertThat(copie.getSeuilsVigilance()).isNotSameAs(defauts.getSeuilsVigilance());
    }

    @Test
    void un_numero_ou_une_annee_hors_bornes_est_refuse() {
        assertThatThrownBy(() -> service.creerSiAbsent(ANNEE, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 1 et 4");
        assertThatThrownBy(() -> service.creerSiAbsent(ANNEE, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 1 et 4");
        assertThatThrownBy(() -> service.creerSiAbsent(1999, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2000");
        assertThat(trimestresDeLAnnee()).isZero();
    }
}
