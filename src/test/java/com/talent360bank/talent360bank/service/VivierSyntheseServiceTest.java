package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier.GapFrequent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Comptes et moyennes des viviers sur des donnees deja chargees (sans base). */
class VivierSyntheseServiceTest {

    private final VivierSyntheseService service = new VivierSyntheseService(mock(VivierThematiqueService.class),
            mock(TalentService.class), mock(PosteCritiqueService.class));

    private static Score score(String id, String performance, String potentiel) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(potentiel == null ? null : new BigDecimal(potentiel));
        return score;
    }

    private static ResultatMatching succession(Score score, NiveauReadiness readiness, String competence, int ecart) {
        return new ResultatMatching(score.getCollaborateur(), new BigDecimal("80.00"), readiness, null,
                new PlusGrandGap("ID-" + competence, competence, 4, 4 - ecart, ecart));
    }

    private static CouverturePoste poste(String id, ResultatMatching... successeurs) {
        Poste poste = new Poste();
        poste.setPosteId(id);
        return new CouverturePoste(poste, successeurs.length, List.of(successeurs), List.of(),
                NiveauCouverture.PARTIELLE);
    }

    @Test
    void un_vivier_compte_ses_membres_leurs_successions_et_leurs_gaps() {
        Score a = score("A", "90.00", "86.00");
        Score b = score("B", "70.00", null);
        Score dehors = score("Z", "50.00", "50.00");
        Map<VivierThematique, List<MembreVivierThematique>> membres = new EnumMap<>(VivierThematique.class);
        membres.put(VivierThematique.DIGITAL, List.of(new MembreVivierThematique(a, true, true),
                new MembreVivierThematique(b, false, false)));

        List<CouverturePoste> couvertures = List.of(
                // A est Ready Now sur deux postes : compte une fois.
                poste("PST01", succession(a, NiveauReadiness.READY_NOW, "Audit", 2)),
                poste("PST02", succession(a, NiveauReadiness.READY_NOW, "Audit", 1),
                        succession(b, NiveauReadiness.PLUS_2_ANS, "Finance", 1)),
                // Sans ecart reel : pas un gap identifie.
                poste("PST03", succession(b, NiveauReadiness.MOINS_1_AN, "Credit", 0)),
                // Poste d'un non-membre : ni couvert ni compte.
                poste("PST04", succession(dehors, NiveauReadiness.READY_NOW, "Audit", 3)));

        List<SyntheseVivier> syntheses = service.resumerThematiques(
                new ResultatViviersThematiques(membres, List.of()), couvertures);

        assertThat(syntheses).extracting(SyntheseVivier::code)
                .containsExactly("COMMERCIAL", "DIGITAL", "EXPERTISE", "MANAGEMENT", "RISQUES");
        SyntheseVivier digital = syntheses.get(1);
        assertThat(digital.releve()).isFalse();
        assertThat(digital.effectif()).isEqualTo(2);
        assertThat(digital.nbTalents()).isEqualTo(1);
        assertThat(digital.nbHautsPotentiels()).isEqualTo(1);
        assertThat(digital.nbReadyNow()).isEqualTo(1);
        assertThat(digital.performanceMoyenne()).isEqualByComparingTo("80.00");
        // Potentiel inconnu de B : moyenne sur les valeurs connues.
        assertThat(digital.potentielMoyen()).isEqualByComparingTo("86.00");
        assertThat(digital.nbPostesCouverts()).isEqualTo(3);
        assertThat(digital.gapsIdentifies()).containsExactly(
                new GapFrequent("ID-Audit", "Audit", 2), new GapFrequent("ID-Finance", "Finance", 1));

        SyntheseVivier vide = syntheses.get(0);
        assertThat(vide.effectif()).isZero();
        assertThat(vide.performanceMoyenne()).isNull();
        assertThat(vide.gapsIdentifies()).isEmpty();
    }

    @Test
    void le_vivier_de_releve_a_son_code_et_ses_membres() {
        Score a = score("A", "90.00", "86.00");
        SyntheseVivier releve = service.resumerReleve(List.of(new MembreVivierReleve(a, false, true)),
                List.of(poste("PST01", succession(a, NiveauReadiness.MOINS_1_AN, "Audit", 1))));

        assertThat(releve.code()).isEqualTo(VivierReleveService.CODE_VIVIER_RELEVE);
        assertThat(releve.libelle()).isEqualTo(VivierReleveService.NOM_VIVIER_RELEVE);
        assertThat(releve.releve()).isTrue();
        assertThat(releve.effectif()).isEqualTo(1);
        assertThat(releve.nbTalents()).isZero();
        assertThat(releve.nbHautsPotentiels()).isEqualTo(1);
        assertThat(releve.nbReadyNow()).isZero();
        assertThat(releve.nbPostesCouverts()).isEqualTo(1);
        assertThat(releve.gapsIdentifies()).containsExactly(new GapFrequent("ID-Audit", "Audit", 1));
    }
}
