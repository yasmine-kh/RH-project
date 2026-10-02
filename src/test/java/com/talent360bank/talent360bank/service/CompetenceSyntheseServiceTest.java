package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.Criteres;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences.LigneCompetence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Moyennes, comptes et tri par competence, sur des gaps deja evalues (sans base). */
class CompetenceSyntheseServiceTest {

    private static Competence competence(String id, String nom) {
        Competence competence = new Competence();
        competence.setCompetenceId(id);
        competence.setNom(nom);
        return competence;
    }

    private static GapCompetence gap(Competence competence, int actuel, int cible, StatutGapCompetence statut) {
        CompetenceCollaborateur skill = new CompetenceCollaborateur();
        skill.setCompetence(competence);
        skill.setNiveauActuel(actuel);
        skill.setNiveauCible(cible);
        return new GapCompetence(skill, cible - actuel, statut);
    }

    @Test
    void chaque_competence_a_ses_moyennes_ses_comptes_et_sa_repartition() {
        Competence credit = competence("C01", "Credit");
        Competence audit = competence("C05", "Audit");
        Competence jamais = competence("C09", "Jamais evaluee");
        List<GapCompetence> gaps = List.of(
                gap(credit, 3, 4, StatutGapCompetence.A_DEVELOPPER),
                gap(credit, 2, 4, StatutGapCompetence.PRIORITAIRE),
                gap(credit, 5, 5, StatutGapCompetence.MAITRISE),
                gap(audit, 4, 4, StatutGapCompetence.MAITRISE));

        List<LigneCompetence> lignes = CompetenceSyntheseService.lignes(List.of(audit, jamais, credit), gaps, false);

        assertThat(lignes).extracting(LigneCompetence::competenceId).containsExactly("C01", "C05", "C09");
        LigneCompetence ligne = lignes.get(0);
        assertThat(ligne.nbEvalues()).isEqualTo(3);
        assertThat(ligne.niveauActuelMoyen()).isEqualByComparingTo("3.33");
        assertThat(ligne.niveauCibleMoyen()).isEqualByComparingTo("4.33");
        assertThat(ligne.gapMoyen()).isEqualByComparingTo("1.00");
        assertThat(ligne.nbAvecGap()).isEqualTo(2);
        assertThat(ligne.pourcentageAvecGap()).isEqualByComparingTo("66.7");
        assertThat(ligne.nbPrioritaires()).isEqualTo(1);
        assertThat(ligne.repartition()).extracting(c -> c.nombre()).containsExactly(0, 1, 1, 0, 1);

        // Sans evalue : present, valeurs nulles, en fin de liste dans les deux ordres.
        LigneCompetence vide = lignes.get(2);
        assertThat(vide.nbEvalues()).isZero();
        assertThat(vide.gapMoyen()).isNull();
        assertThat(vide.pourcentageAvecGap()).isNull();
        assertThat(CompetenceSyntheseService.lignes(List.of(audit, jamais, credit), gaps, true))
                .extracting(LigneCompetence::competenceId).containsExactly("C05", "C01", "C09");
    }

    @Test
    void un_vivier_inconnu_ou_un_top_hors_bornes_est_refuse() {
        assertThatThrownBy(() -> CompetenceSyntheseService.verifier(new Criteres(null, "AUTRE", null, false, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RELEVE");
        assertThatThrownBy(() -> CompetenceSyntheseService.verifier(new Criteres(null, null, null, false, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        CompetenceSyntheseService.verifier(new Criteres(null, "RELEVE", null, false, 50));
    }
}
