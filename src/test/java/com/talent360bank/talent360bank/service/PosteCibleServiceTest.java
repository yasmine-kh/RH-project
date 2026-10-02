package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Choix du poste cible parmi des postes deja charges (sans base). */
class PosteCibleServiceTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 15);

    private PosteCibleService service;
    private Parametre parametre;
    private Competence credit;
    private Competence leadership;
    private Collaborateur candidat;

    @BeforeEach
    void init() {
        CalculService calculService = new CalculService(mock(ParametreRepository.class),
                mock(PerformanceRepository.class), mock(PotentielRepository.class));
        SuccessionService successionService = new SuccessionService(mock(PosteRepository.class),
                mock(ScoreRepository.class), mock(PotentielRepository.class),
                mock(CompetenceCollaborateurRepository.class), calculService);
        service = new PosteCibleService(mock(PosteRepository.class), mock(ScoreRepository.class),
                mock(PotentielRepository.class), mock(CompetenceCollaborateurRepository.class), successionService,
                calculService, posteId -> List.of());

        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(3);
        trimestre.setDateReference(REFERENCE);
        parametre = Parametre.parDefaut(trimestre);

        credit = competence("C01", "Credit");
        leadership = competence("C10", "Leadership");
        candidat = new Collaborateur();
        candidat.setIdCollaborateur("BP001");
        candidat.setNom("Bouzid");
        candidat.setPrenom("Hind");
        candidat.setDateEntree(REFERENCE.minusYears(5));
        candidat.setStatut(StatutCollaborateur.ACTIF);
    }

    private static Competence competence(String id, String nom) {
        Competence competence = new Competence();
        competence.setCompetenceId(id);
        competence.setNom(nom);
        return competence;
    }

    private Poste poste(String id, Competence competence, int niveau, String critique, String titulaire) {
        Poste poste = new Poste();
        poste.setPosteId(id);
        poste.setNomPoste("Poste " + id);
        poste.setCompetenceRequise1(competence);
        poste.setNiveau1(niveau);
        poste.setPosteCritique(critique);
        poste.setTitulaireId(titulaire);
        return poste;
    }

    private Score score(String performance, String potentiel) {
        Score score = new Score();
        score.setCollaborateur(candidat);
        score.setScorePerformance(performance == null ? null : new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal(potentiel));
        return score;
    }

    private Potentiel potentiel() {
        Potentiel potentiel = new Potentiel();
        potentiel.setCollaborateur(candidat);
        potentiel.setNoteLeadership(new BigDecimal("70"));
        potentiel.setNoteMobilite(new BigDecimal("60"));
        return potentiel;
    }

    private List<CompetenceCollaborateur> niveaux(int credit, int leadership) {
        CompetenceCollaborateur a = new CompetenceCollaborateur();
        a.setCollaborateur(candidat);
        a.setCompetence(this.credit);
        a.setNiveauActuel(credit);
        CompetenceCollaborateur b = new CompetenceCollaborateur();
        b.setCollaborateur(candidat);
        b.setCompetence(this.leadership);
        b.setNiveauActuel(leadership);
        return List.of(a, b);
    }

    private Optional<ResultatPosteCible> cible(Score score, List<Poste> postes, Set<String> identifies) {
        return service.meilleurPoste(candidat, score, potentiel(), niveaux(4, 2), postes, identifies, parametre,
                REFERENCE);
    }

    @Test
    void le_poste_cible_est_le_poste_critique_au_meilleur_matching() {
        // Credit 4 sur 4 exige : competences 100 ; Leadership 2 sur 5 exige : 40.
        Optional<ResultatPosteCible> cible = cible(score("80.00", "75.00"), List.of(
                poste("PST02", leadership, 5, "Oui", null),
                poste("PST01", credit, 4, "Oui", null)), Set.of());

        assertThat(cible).isPresent();
        assertThat(cible.get().poste().getPosteId()).isEqualTo("PST01");
        assertThat(cible.get().successeurIdentifie()).isFalse();
        assertThat(cible.get().matching().plusGrandGap().ecart()).isZero();
    }

    @Test
    void a_matching_egal_le_plus_petit_poste_id_l_emporte() {
        Optional<ResultatPosteCible> cible = cible(score("80.00", "75.00"), List.of(
                poste("PST09", credit, 4, "Oui", null),
                poste("PST03", credit, 4, "Oui", null)), Set.of("PST09"));

        assertThat(cible.orElseThrow().poste().getPosteId()).isEqualTo("PST03");
        assertThat(cible.orElseThrow().successeurIdentifie()).isFalse();
    }

    @Test
    void le_titulaire_et_les_postes_non_critiques_sont_ecartes() {
        Optional<ResultatPosteCible> cible = cible(score("80.00", "75.00"), List.of(
                poste("PST01", credit, 4, "Oui", "BP001"),
                poste("PST02", credit, 4, "Non", null),
                poste("PST03", leadership, 5, "Oui", null)), Set.of("PST03"));

        assertThat(cible.orElseThrow().poste().getPosteId()).isEqualTo("PST03");
        assertThat(cible.orElseThrow().successeurIdentifie()).isTrue();
        assertThat(cible.orElseThrow().matching().plusGrandGap().competence()).isEqualTo("Leadership");
        assertThat(cible.orElseThrow().matching().plusGrandGap().ecart()).isEqualTo(3);
    }

    @Test
    void sans_score_complet_ou_hors_perimetre_il_n_y_a_pas_de_poste_cible() {
        List<Poste> postes = List.of(poste("PST01", credit, 4, "Oui", null));

        assertThat(cible(score(null, "75.00"), postes, Set.of())).isEmpty();
        candidat.setStatut(StatutCollaborateur.INACTIF);
        assertThat(cible(score("80.00", "75.00"), postes, Set.of())).isEmpty();
    }

    @Test
    void sans_poste_critique_disponible_il_n_y_a_pas_de_poste_cible() {
        assertThat(cible(score("80.00", "75.00"), List.of(poste("PST01", credit, 4, "Oui", "BP001")), Set.of()))
                .isEmpty();
    }
}
