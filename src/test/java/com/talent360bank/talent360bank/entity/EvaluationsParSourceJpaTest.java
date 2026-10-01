package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deux evaluations par collaborateur et trimestre, une par source
 * (auto-evaluation et manager) : contrainte unique (collaborateur, trimestre,
 * source) et lectures des depots par source.
 */
@DataJpaTest
class EvaluationsParSourceJpaTest {

    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PotentielRepository potentielRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DataSource dataSource;

    private Collaborateur collaborateur;
    private Trimestre trimestre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(2);
        trimestre.setDateReference(LocalDate.of(2026, 6, 30));
        trimestre = trimestreRepository.save(trimestre);

        collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("S001");
        collaborateur.setNom("Source");
        collaborateur.setPrenom("Double");
        collaborateur.setDateEntree(LocalDate.of(2020, 1, 1));
        collaborateur = collaborateurRepository.save(collaborateur);
    }

    private Performance performance(SourceEvaluation source, String note) {
        BigDecimal n = new BigDecimal(note);
        Performance performance = new Performance(collaborateur, trimestre, n, n, n, n, n);
        performance.setSource(source);
        return performance;
    }

    private Potentiel potentiel(SourceEvaluation source, String note) {
        BigDecimal n = new BigDecimal(note);
        Potentiel potentiel = new Potentiel(collaborateur, trimestre, n, n, n, n, n, n, n);
        potentiel.setSource(source);
        return potentiel;
    }

    @Test
    void une_auto_evaluation_et_une_evaluation_du_manager_coexistent_pour_le_meme_trimestre() {
        performanceRepository.saveAndFlush(performance(SourceEvaluation.MANAGER, "70"));
        performanceRepository.saveAndFlush(performance(SourceEvaluation.AUTO, "90"));
        potentielRepository.saveAndFlush(potentiel(SourceEvaluation.MANAGER, "60"));
        potentielRepository.saveAndFlush(potentiel(SourceEvaluation.AUTO, "85"));
        entityManager.clear();

        assertThat(performanceRepository.findByCollaborateurAndTrimestreOrderBySource(collaborateur, trimestre))
                .extracting(Performance::getSource, Performance::getNoteObjectifs)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(SourceEvaluation.AUTO, new BigDecimal("90.00")),
                        org.assertj.core.api.Assertions.tuple(SourceEvaluation.MANAGER, new BigDecimal("70.00")));
        assertThat(performanceRepository.findByCollaborateurAndTrimestreAndSource(collaborateur, trimestre,
                SourceEvaluation.MANAGER)).get().extracting(Performance::getNoteObjectifs)
                .isEqualTo(new BigDecimal("70.00"));
        assertThat(potentielRepository.findByCollaborateurIdCollaborateurAndTrimestreAndSource("S001", trimestre,
                SourceEvaluation.AUTO)).get().extracting(Potentiel::getNoteLeadership)
                .isEqualTo(new BigDecimal("85.00"));

        // Les lectures d'une source ne voient pas l'autre ; "toutes sources" voit les deux.
        assertThat(performanceRepository.findByTrimestreAvecCollaborateur(trimestre, SourceEvaluation.MANAGER))
                .singleElement().extracting(Performance::getSource).isEqualTo(SourceEvaluation.MANAGER);
        assertThat(potentielRepository.findMatriculesEvaluesDuTrimestre(trimestre, SourceEvaluation.AUTO))
                .containsExactly("S001");
        assertThat(performanceRepository.findByTrimestreToutesSources(trimestre)).hasSize(2);
        assertThat(potentielRepository.findByTrimestreEtCollaborateurs(trimestre, List.of("S001"))).hasSize(2);
    }

    @Test
    void une_seconde_evaluation_de_la_meme_source_est_refusee() {
        performanceRepository.saveAndFlush(performance(SourceEvaluation.MANAGER, "70"));
        performanceRepository.saveAndFlush(performance(SourceEvaluation.AUTO, "90"));

        assertThatThrownBy(() -> performanceRepository.saveAndFlush(performance(SourceEvaluation.AUTO, "50")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void un_second_potentiel_du_manager_est_refuse() {
        potentielRepository.saveAndFlush(potentiel(SourceEvaluation.MANAGER, "60"));

        assertThatThrownBy(() -> potentielRepository.saveAndFlush(potentiel(SourceEvaluation.MANAGER, "65")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Une ligne ecrite sans source (anterieure a la colonne, ou par SQL) est une evaluation du manager. */
    @Test
    void sans_source_une_evaluation_est_celle_du_manager() {
        assertThat(new Performance().getSource()).isEqualTo(SourceEvaluation.MANAGER);
        assertThat(new Potentiel().getSource()).isEqualTo(SourceEvaluation.MANAGER);

        entityManager.flush();
        new JdbcTemplate(dataSource).update("INSERT INTO performance (id_collaborateur, id_trimestre, note_objectifs, "
                        + "note_competences, note_comportement, note_contribution, note_developpement) "
                        + "VALUES (?, ?, 50, 50, 50, 50, 50)", "S001", trimestre.getIdTrimestre());

        assertThat(performanceRepository.findByCollaborateurAndTrimestreAndSource(collaborateur, trimestre,
                SourceEvaluation.MANAGER)).isPresent();
    }
}
