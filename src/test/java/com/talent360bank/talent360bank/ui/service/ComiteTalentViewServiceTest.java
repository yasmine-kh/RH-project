package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.CategoriePerformance;
import com.talent360bank.talent360bank.entity.CategoriePotentiel;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.ui.model.ComiteTalentRow;
import com.talent360bank.talent360bank.ui.model.ComiteTalentView;
import com.talent360bank.talent360bank.ui.model.OptionFiltre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComiteTalentViewServiceTest {

    @Mock
    private ValidationComiteService validationComiteService;
    @Mock
    private TrimestreRepository trimestreRepository;

    private ComiteTalentViewService service;
    private Trimestre t3;
    private Trimestre t2;

    @BeforeEach
    void init() {
        service = new ComiteTalentViewService(validationComiteService, trimestreRepository);
        t3 = trimestre(3);
        t2 = trimestre(2);
    }

    private Trimestre trimestre(int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(numero);
        trimestre.setAnnee(2026);
        return trimestre;
    }

    private DecisionComite decision(String prenom, StatutValidationComite statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("ID-" + prenom);
        collaborateur.setPrenom(prenom);
        collaborateur.setNom("Nom");
        collaborateur.setEntite(new Entite("Risques", TypeEntite.DIRECTION, null));
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.figerOrganisation();
        score.setScorePerformance(new BigDecimal("91.20"));
        score.setCategoriePerformance(CategoriePerformance.EXCEPTIONNELLE);
        score.setScorePotentiel(new BigDecimal("95.95"));
        score.setCategoriePotentiel(CategoriePotentiel.ELEVE);
        score.setPositionBox("Talent cle");
        return new DecisionComite(score, statut);
    }

    private void troisDecisionsSurT3() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t3, t2));
        when(validationComiteService.getDecisionsComite(t3)).thenReturn(List.of(
                decision("Rachid", StatutValidationComite.OUI),
                decision("Nadia", StatutValidationComite.OUI),
                decision("Ghita", StatutValidationComite.EN_ATTENTE)));
    }

    @Test
    void sans_choix_le_trimestre_le_plus_recent_est_affiche() {
        troisDecisionsSurT3();

        ComiteTalentView vue = service.build(null, null);

        assertThat(vue.getTrimestreLibelle()).isEqualTo("T3 2026");
        assertThat(vue.getTrimestres()).extracting(OptionFiltre::getValeur).containsExactly("2026-3", "2026-2");
        assertThat(vue.getTrimestres()).extracting(OptionFiltre::isSelectionnee).containsExactly(true, false);
        assertThat(vue.getRows()).hasSize(3);
        assertThat(vue.getNbProposes()).isEqualTo(3);
        assertThat(vue.getErreur()).isNull();
    }

    @Test
    void le_kpi_compte_les_talents_valides_par_le_comite() {
        troisDecisionsSurT3();

        ComiteTalentView vue = service.build(null, null);

        assertThat(vue.getKpis()).singleElement().satisfies(kpi -> {
            assertThat(kpi.getLabel()).isEqualTo("Talents valides (Comite)");
            assertThat(kpi.getValue()).isEqualTo("2");
            assertThat(kpi.getColorClass()).isEqualTo("kpi-green");
        });
    }

    @Test
    void chaque_ligne_porte_scores_categories_case_et_statut() {
        troisDecisionsSurT3();

        ComiteTalentRow ligne = service.build(null, null).getRows().get(0);

        assertThat(ligne.getNomComplet()).isEqualTo("Rachid Nom");
        assertThat(ligne.getDirection()).isEqualTo("Risques");
        assertThat(ligne.getPerformance()).isEqualByComparingTo("91.20");
        assertThat(ligne.getCategoriePerformance()).isEqualTo("Exceptionnelle");
        assertThat(ligne.getPotentiel()).isEqualByComparingTo("95.95");
        assertThat(ligne.getCategoriePotentiel()).isEqualTo("Eleve");
        assertThat(ligne.getPositionBox()).isEqualTo("Talent cle");
        assertThat(ligne.getStatut()).isEqualTo("OUI");
        assertThat(ligne.getStatutLibelle()).isEqualTo("Oui");
        assertThat(ligne.getBadgeClass()).isEqualTo("bg-success");
    }

    @Test
    void des_categories_pas_encore_posees_restent_vides() {
        DecisionComite sansCategorie = decision("Rachid", StatutValidationComite.OUI);
        sansCategorie.score().setCategoriePerformance(null);
        sansCategorie.score().setCategoriePotentiel(null);
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t3));
        when(validationComiteService.getDecisionsComite(t3)).thenReturn(List.of(sansCategorie));

        ComiteTalentRow ligne = service.build(null, null).getRows().get(0);

        assertThat(ligne.getCategoriePerformance()).isNull();
        assertThat(ligne.getCategoriePotentiel()).isNull();
    }

    @Test
    void un_trimestre_choisi_est_affiche() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t3, t2));
        when(validationComiteService.getDecisionsComite(t2)).thenReturn(List.of());

        ComiteTalentView vue = service.build("2026-2", null);

        assertThat(vue.getTrimestreLibelle()).isEqualTo("T2 2026");
        assertThat(vue.getTrimestres()).extracting(OptionFiltre::isSelectionnee).containsExactly(false, true);
        verify(validationComiteService, never()).getDecisionsComite(t3);
    }

    @Test
    void un_trimestre_inconnu_retombe_sur_le_plus_recent() {
        troisDecisionsSurT3();

        assertThat(service.build("1999-9", null).getTrimestreLibelle()).isEqualTo("T3 2026");
    }

    @Test
    void le_filtre_de_statut_ne_garde_que_les_lignes_du_statut_mais_pas_le_kpi() {
        troisDecisionsSurT3();

        ComiteTalentView vue = service.build(null, "EN_ATTENTE");

        assertThat(vue.getRows()).extracting(ComiteTalentRow::getNomComplet).containsExactly("Ghita Nom");
        assertThat(vue.getKpis().get(0).getValue()).isEqualTo("2");
        assertThat(vue.getNbProposes()).isEqualTo(3);
        assertThat(vue.getStatuts()).filteredOn(OptionFiltre::isSelectionnee)
                .extracting(OptionFiltre::getValeur).containsExactly("EN_ATTENTE");
    }

    @Test
    void les_options_de_statut_portent_leur_effectif() {
        troisDecisionsSurT3();

        assertThat(service.build(null, null).getStatuts()).extracting(OptionFiltre::getLibelle)
                .containsExactly("Tous (3)", "Oui (2)", "Non (0)", "En attente (1)");
    }

    @Test
    void un_statut_inconnu_affiche_tous_les_talents() {
        troisDecisionsSurT3();

        ComiteTalentView vue = service.build(null, "PEUT_ETRE");

        assertThat(vue.getRows()).hasSize(3);
        assertThat(vue.getStatuts().get(0).isSelectionnee()).isTrue();
    }

    @Test
    void sans_aucun_trimestre_l_ecran_est_vide_sans_appel_au_moteur() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of());

        ComiteTalentView vue = service.build(null, null);

        assertThat(vue.getTrimestreLibelle()).isNull();
        assertThat(vue.getRows()).isEmpty();
        assertThat(vue.getKpis().get(0).getValue()).isEqualTo("0");
        verify(validationComiteService, never()).getDecisionsComite(any());
    }

    @Test
    void des_reglages_absents_sont_signales_au_lieu_de_faire_echouer_la_page() {
        when(trimestreRepository.findAllByOrderByAnneeDescNumeroDesc()).thenReturn(List.of(t3));
        when(validationComiteService.getDecisionsComite(t3))
                .thenThrow(new RessourceIntrouvableException("Aucun parametre configure pour T3 2026"));

        ComiteTalentView vue = service.build(null, null);

        assertThat(vue.getErreur()).isEqualTo("Aucun parametre configure pour T3 2026");
        assertThat(vue.getRows()).isEmpty();
        assertThat(vue.getKpis().get(0).getValue()).isEqualTo("0");
    }

    @ParameterizedTest
    @CsvSource({
            "OUI, bg-success",
            "EN_ATTENTE, bg-warning text-dark",
            "NON, bg-danger"
    })
    void chaque_statut_a_sa_couleur_de_badge(StatutValidationComite statut, String attendue) {
        assertThat(ComiteTalentViewService.badgeClass(statut)).isEqualTo(attendue);
    }
}
