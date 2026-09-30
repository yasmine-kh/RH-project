package com.talent360bank.talent360bank.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une base creee avant que l'utilisateur du journal ne devienne facultatif :
 * import_excel.id_utilisateur y est NOT NULL, aucun import ne pourrait etre
 * journalise. Le passage au demarrage la rend nullable.
 *
 * <p>Sous H2, un ALTER TABLE valide la transaction : la colonne est remise
 * nullable apres chaque test pour ne pas gener les autres classes.
 */
@DataJpaTest
class ImportExcelInitializerJpaTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private String nullable() {
        return jdbc().queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'IMPORT_EXCEL' AND COLUMN_NAME = 'ID_UTILISATEUR'", String.class);
    }

    @AfterEach
    void remettreNullable() {
        jdbc().execute("ALTER TABLE import_excel ALTER COLUMN id_utilisateur SET NULL");
    }

    @Test
    void une_base_neuve_cree_la_colonne_nullable() {
        assertThat(nullable()).isEqualTo("YES");
    }

    @Test
    void une_colonne_not_null_existante_est_liberee_et_le_passage_est_rejouable() throws Exception {
        jdbc().execute("ALTER TABLE import_excel ALTER COLUMN id_utilisateur SET NOT NULL");
        assertThat(nullable()).isEqualTo("NO");

        ImportExcelInitializer initializer = new ImportExcelInitializer(dataSource);
        initializer.run(null);
        initializer.run(null);

        assertThat(nullable()).isEqualTo("YES");
    }
}
