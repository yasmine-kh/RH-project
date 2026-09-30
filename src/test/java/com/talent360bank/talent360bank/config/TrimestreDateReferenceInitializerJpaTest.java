package com.talent360bank.talent360bank.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une base creee avant la date de reference : trimestre.date_reference y
 * manque, ou y est nullable avec des lignes vides. Le passage au demarrage la
 * complete au dernier jour de chaque trimestre et la rend NOT NULL.
 *
 * <p>Hors transaction de test : sous H2, un ALTER TABLE valide la transaction
 * en cours, le retour arriere habituel ne vaudrait donc que pour une partie
 * des ecritures. Les lignes inserees sont supprimees et la colonne remise dans
 * sa forme normale apres chaque test.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrimestreDateReferenceInitializerJpaTest {

    private static final int ANNEE = 2093;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private String nullable() {
        return jdbc().queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'TRIMESTRE' AND COLUMN_NAME = 'DATE_REFERENCE'", String.class);
    }

    private List<LocalDate> datesDeLAnnee() {
        return jdbc().queryForList("SELECT date_reference FROM trimestre WHERE annee = ? ORDER BY numero",
                LocalDate.class, ANNEE);
    }

    @AfterEach
    void remettreEnEtat() throws Exception {
        jdbc().update("DELETE FROM trimestre WHERE annee = ?", ANNEE);
        new TrimestreDateReferenceInitializer(dataSource).run(null);
    }

    @Test
    void une_base_neuve_cree_la_colonne_not_null() {
        assertThat(nullable()).isEqualTo("NO");
    }

    @Test
    void une_colonne_absente_est_ajoutee_completee_et_rendue_not_null() throws Exception {
        jdbc().execute("ALTER TABLE trimestre DROP COLUMN date_reference");
        jdbc().update("INSERT INTO trimestre (annee, numero) VALUES (?, 1), (?, 3)", ANNEE, ANNEE);

        TrimestreDateReferenceInitializer initializer = new TrimestreDateReferenceInitializer(dataSource);
        initializer.run(null);
        initializer.run(null);

        assertThat(nullable()).isEqualTo("NO");
        assertThat(datesDeLAnnee()).containsExactly(LocalDate.of(ANNEE, 3, 31), LocalDate.of(ANNEE, 9, 30));
    }

    @Test
    void une_colonne_nullable_est_completee_sans_toucher_aux_dates_deja_posees() throws Exception {
        jdbc().execute("ALTER TABLE trimestre ALTER COLUMN date_reference SET NULL");
        jdbc().update("INSERT INTO trimestre (annee, numero, date_reference) VALUES (?, 2, NULL), (?, 4, ?)",
                ANNEE, ANNEE, LocalDate.of(ANNEE, 11, 15));

        new TrimestreDateReferenceInitializer(dataSource).run(null);

        assertThat(nullable()).isEqualTo("NO");
        assertThat(datesDeLAnnee()).containsExactly(LocalDate.of(ANNEE, 6, 30), LocalDate.of(ANNEE, 11, 15));
    }
}
