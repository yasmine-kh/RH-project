package com.talent360bank.talent360bank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Rend nullable au demarrage la colonne import_excel.id_utilisateur.
 *
 * <p>Elle a ete creee NOT NULL alors que l'application n'a pas de comptes :
 * aucun import ne pourrait etre journalise. ddl-auto=update n'assouplit jamais
 * une colonne existante, d'ou ce passage, sur le modele de
 * {@link ColonnesObsoletesInitializer}. La cle etrangere et les valeurs deja
 * en base sont conservees.
 *
 * <p>Sans effet sur une base neuve, ou la colonne est creee nullable, et sur
 * une base deja passee par ici.
 */
@Component
public class ImportExcelInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportExcelInitializer.class);

    static final String TABLE = "import_excel";
    static final String COLONNE = "id_utilisateur";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ImportExcelInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        boolean mysql;
        boolean aLiberer;
        try (Connection connexion = dataSource.getConnection()) {
            DatabaseMetaData meta = connexion.getMetaData();
            mysql = meta.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
            aLiberer = estNonNullable(meta, connexion.getCatalog());
        }
        if (!aLiberer) {
            return;
        }
        // Integer de UtilisateurRH.idUtilisateur : INT sous MySQL.
        jdbcTemplate.execute(mysql
                ? "ALTER TABLE " + TABLE + " MODIFY COLUMN " + COLONNE + " INT NULL"
                : "ALTER TABLE " + TABLE + " ALTER COLUMN " + COLONNE + " SET NULL");
        log.info("Journal des imports : colonne {}.{} rendue nullable", TABLE, COLONNE);
    }

    /** Colonne presente et NOT NULL, quelle que soit la casse du SGBD. */
    private boolean estNonNullable(DatabaseMetaData meta, String catalogue) throws SQLException {
        for (String table : List.of(TABLE, TABLE.toUpperCase(Locale.ROOT))) {
            try (ResultSet colonnes = meta.getColumns(catalogue, null, table, null)) {
                while (colonnes.next()) {
                    if (COLONNE.equalsIgnoreCase(colonnes.getString("COLUMN_NAME"))
                            && colonnes.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
