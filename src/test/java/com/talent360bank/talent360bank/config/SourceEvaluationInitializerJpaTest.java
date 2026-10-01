package com.talent360bank.talent360bank.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passage d'une base d'avant l'auto-evaluation (une evaluation par
 * collaborateur et trimestre, sans colonne source) au modele a deux sources.
 * Base en memoire a part : les tests modifient le schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:source-evaluation-init;DB_CLOSE_DELAY=-1;MODE=MySQL")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SourceEvaluationInitializerJpaTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private List<String> contraintesUniques(String table) {
        return jdbc().queryForList("SELECT LOWER(CONSTRAINT_NAME) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE LOWER(TABLE_NAME) = ? AND CONSTRAINT_TYPE = 'UNIQUE'", String.class, table);
    }

    @Test
    void une_base_neuve_n_a_que_la_contrainte_par_source() throws Exception {
        new SourceEvaluationInitializer(dataSource).run(null);

        for (String table : SourceEvaluationInitializer.TABLES) {
            assertThat(contraintesUniques(table))
                    .contains(SourceEvaluationInitializer.nouvelleContrainte(table))
                    .doesNotContain(SourceEvaluationInitializer.ancienneContrainte(table));
        }
    }

    @Test
    void une_base_d_avant_l_auto_evaluation_passe_a_deux_sources_sans_perdre_ses_lignes() throws Exception {
        JdbcTemplate jdbc = jdbc();
        // Schema d'avant : contrainte (collaborateur, trimestre), source absente des lignes.
        jdbc.execute("ALTER TABLE performance DROP CONSTRAINT "
                + SourceEvaluationInitializer.nouvelleContrainte("performance"));
        jdbc.execute("ALTER TABLE performance ALTER COLUMN source_evaluation SET NULL");
        jdbc.execute("ALTER TABLE performance ADD CONSTRAINT "
                + SourceEvaluationInitializer.ancienneContrainte("performance")
                + " UNIQUE (id_collaborateur, id_trimestre)");
        jdbc.update("INSERT INTO trimestre (annee, numero, date_reference) VALUES (2091, 1, '2091-03-31')");
        Integer trimestre = jdbc.queryForObject("SELECT id_trimestre FROM trimestre WHERE annee = 2091",
                Integer.class);
        jdbc.update("INSERT INTO collaborateur (id_collaborateur, nom, prenom, date_entree, statut) "
                + "VALUES ('M001', 'Ancien', 'Schema', '2015-01-01', 'ACTIF')");
        jdbc.update("INSERT INTO performance (id_collaborateur, id_trimestre, source_evaluation, note_objectifs, "
                + "note_competences, note_comportement, note_contribution, note_developpement) "
                + "VALUES ('M001', ?, NULL, 70, 70, 70, 70, 70)", trimestre);

        SourceEvaluationInitializer initializer = new SourceEvaluationInitializer(dataSource);
        initializer.run(null);
        initializer.run(null);

        assertThat(jdbc.queryForList("SELECT source_evaluation FROM performance WHERE id_collaborateur = 'M001'",
                String.class)).containsExactly("MANAGER");
        assertThat(contraintesUniques("performance"))
                .contains(SourceEvaluationInitializer.nouvelleContrainte("performance"))
                .doesNotContain(SourceEvaluationInitializer.ancienneContrainte("performance"));
        // L'auto-evaluation du meme trimestre est desormais acceptee a cote.
        jdbc.update("INSERT INTO performance (id_collaborateur, id_trimestre, source_evaluation, note_objectifs, "
                + "note_competences, note_comportement, note_contribution, note_developpement) "
                + "VALUES ('M001', ?, 'AUTO', 90, 90, 90, 90, 90)", trimestre);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance WHERE id_collaborateur = 'M001'",
                Integer.class)).isEqualTo(2);
    }
}
