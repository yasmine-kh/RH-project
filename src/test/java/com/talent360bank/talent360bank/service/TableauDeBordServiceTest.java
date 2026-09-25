package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableauDeBordServiceTest {

    @Mock
    private TalentService talentService;
    @Mock
    private VigilanceService vigilanceService;
    @Mock
    private PosteCritiqueService posteCritiqueService;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private Matrice9BoxRepository matrice9BoxRepository;

    private TableauDeBordService service;
    private Trimestre trimestre;

    @BeforeEach
    void init() {
        service = new TableauDeBordService(talentService, vigilanceService, posteCritiqueService,
                scoreRepository, matrice9BoxRepository);
        trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
    }

    @Test
    void la_synthese_assemble_les_chiffres_du_moteur() {
        // Vivier : un talent qui est aussi HP, un talent seul, un HP seul.
        when(talentService.getVivierReleve(trimestre)).thenReturn(List.of(
                new MembreVivierReleve(score("BP001", null), true, true),
                new MembreVivierReleve(score("BP002", null), true, false),
                new MembreVivierReleve(score("BP003", null), false, true)));
        when(vigilanceService.evaluerTrimestre(trimestre)).thenReturn(List.of(
                vigilance("BP001", NiveauVigilance.ELEVEE),
                vigilance("BP002", NiveauVigilance.MODEREE),
                vigilance("BP003", NiveauVigilance.MODEREE)));
        List<CouverturePoste> couvertures = List.of(
                couverture("PST01", NiveauCouverture.READY_NOW), couverture("PST13", NiveauCouverture.ALERTE));
        when(posteCritiqueService.listerPostesCritiques(trimestre)).thenReturn(couvertures);
        when(posteCritiqueService.tauxCouverture(couvertures)).thenReturn(new BigDecimal("50.00"));
        when(matrice9BoxRepository.findAll()).thenReturn(List.of(
                case9Box(1, 1, "A surveiller"), case9Box(3, 3, "Talent cle"), case9Box(3, 2, "Performant")));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(List.of(
                score("BP001", "Talent cle"), score("BP002", "Talent cle"),
                score("BP003", "A surveiller"), score("BP004", null)));

        SyntheseTableauDeBord synthese = service.synthese(trimestre);

        assertThat(synthese.nbTalents()).isEqualTo(2);
        assertThat(synthese.nbHautsPotentiels()).isEqualTo(2);

        // Chaque niveau est present, meme a zero, dans l'ordre de l'enum.
        assertThat(synthese.vigilanceParNiveau()).containsExactly(
                entry(NiveauVigilance.FAIBLE, 0), entry(NiveauVigilance.MODEREE, 2), entry(NiveauVigilance.ELEVEE, 1));
        assertThat(synthese.nbARisque()).isEqualTo(3);

        assertThat(synthese.nbPostesCritiques()).isEqualTo(2);
        assertThat(synthese.nbAlertesPostesCritiques()).isEqualTo(1);
        assertThat(synthese.alertesPostesCritiques()).extracting(c -> c.poste().getPosteId())
                .containsExactly("PST13");
        assertThat(synthese.tauxCouverture()).isEqualByComparingTo("50.00");

        // Cases vides comprises, performance puis potentiel decroissants.
        assertThat(synthese.repartition9Box()).containsExactly(
                entry("Talent cle", 2), entry("Performant", 0), entry("A surveiller", 1));
        assertThat(synthese.nbPlaces9Box()).isEqualTo(3);
        assertThat(synthese.nbNonPlaces9Box()).isEqualTo(1);
    }

    @Test
    void un_trimestre_vide_rend_des_zeros_et_pas_d_erreur() {
        when(talentService.getVivierReleve(trimestre)).thenReturn(List.of());
        when(vigilanceService.evaluerTrimestre(trimestre)).thenReturn(List.of());
        when(posteCritiqueService.listerPostesCritiques(trimestre)).thenReturn(List.of());
        when(matrice9BoxRepository.findAll()).thenReturn(List.of(case9Box(3, 3, "Talent cle")));
        when(scoreRepository.findByTrimestreAvecEmploye(trimestre)).thenReturn(List.of());

        SyntheseTableauDeBord synthese = service.synthese(trimestre);

        assertThat(synthese.nbTalents()).isZero();
        assertThat(synthese.nbARisque()).isZero();
        assertThat(synthese.vigilanceParNiveau()).hasSize(3).allSatisfy((niveau, nb) -> assertThat(nb).isZero());
        assertThat(synthese.tauxCouverture()).isNull();
        assertThat(synthese.repartition9Box()).containsExactly(entry("Talent cle", 0));
    }

    @Test
    void sans_reglages_du_trimestre_la_synthese_echoue() {
        when(talentService.getVivierReleve(trimestre))
                .thenThrow(new RessourceIntrouvableException("Aucun parametre configure pour T3 2026"));

        assertThatThrownBy(() -> service.synthese(trimestre)).isInstanceOf(RessourceIntrouvableException.class);
    }

    // --- outils --------------------------------------------------------------

    private static <K, V> java.util.Map.Entry<K, V> entry(K cle, V valeur) {
        return java.util.Map.entry(cle, valeur);
    }

    private Employe employe(String employeeId) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
        employe.setStatut(StatutEmploye.ACTIF);
        return employe;
    }

    private Score score(String employeeId, String positionBox) {
        Score score = new Score();
        score.setEmploye(employe(employeeId));
        score.setTrimestre(trimestre);
        score.setPositionBox(positionBox);
        return score;
    }

    private ResultatVigilance vigilance(String employeeId, NiveauVigilance niveau) {
        return new ResultatVigilance(employe(employeeId), new BigDecimal("40"), niveau, Set.of());
    }

    private CouverturePoste couverture(String posteId, NiveauCouverture niveau) {
        Poste poste = new Poste();
        poste.setPosteId(posteId);
        poste.setPosteCritique("Oui");
        return new CouverturePoste(poste, niveau == NiveauCouverture.ALERTE ? 0 : 1, List.of(), List.of(), niveau);
    }

    private Matrice9Box case9Box(int performance, int potentiel, String categorie) {
        Matrice9Box case9Box = new Matrice9Box();
        case9Box.setNiveauPerformance(performance);
        case9Box.setNiveauPotentiel(potentiel);
        case9Box.setCategorie(categorie);
        return case9Box;
    }
}
