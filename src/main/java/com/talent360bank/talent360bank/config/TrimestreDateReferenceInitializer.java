package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Trimestre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Donne une date de reference aux trimestres crees avant qu'elle n'existe
 * (colonne trimestre.date_reference, NOT NULL).
 *
 * <p>Sur une base MySQL qui a deja des trimestres, ddl-auto=update ne peut pas
 * ajouter une colonne DATE NOT NULL sans valeur : l'ALTER echoue (il est
 * journalise, pas bloquant). Ce passage ajoute alors la colonne nullable,
 * pose le dernier jour du trimestre sur chaque ligne sans date, puis la rend
 * NOT NULL. Sur le modele de {@link ImportExcelInitializer}.
 *
 * <p>Passe avant les autres initialisations : ParametreInitializer charge
 * les reglages, donc leur trimestre, qui lit cette colonne.
 *
 * <p>Sans effet sur une base neuve (colonne creee NOT NULL, aucune ligne) et
 * sur une base deja passee par ici.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrimestreDateReferenceInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TrimestreDateReferenceInitializer.class);

    static final String TABLE = "trimestre";
    static final String COLONNE = "date_reference";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public TrimestreDateReferenceInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        boolean mysql;
        Boolean nullable;
        try (Connection connexion = dataSource.getConnection()) {
            DatabaseMetaData meta = connexion.getMetaData();
            mysql = meta.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
            nullable = colonneNullable(meta, connexion.getCatalog());
        }

        if (nullable == null) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + COLONNE + " DATE NULL");
            nullable = true;
            log.info("Trimestres : colonne {}.{} ajoutee", TABLE, COLONNE);
        }

        int completes = completerDates();
        if (completes > 0) {
            log.info("Trimestres : date de reference posee au dernier jour du trimestre sur {} ligne(s)", completes);
        }

        if (nullable) {
            jdbcTemplate.execute(mysql
                    ? "ALTER TABLE " + TABLE + " MODIFY COLUMN " + COLONNE + " DATE NOT NULL"
                    : "ALTER TABLE " + TABLE + " ALTER COLUMN " + COLONNE + " SET NOT NULL");
            log.info("Trimestres : colonne {}.{} rendue NOT NULL", TABLE, COLONNE);
        }
    }

    /**
     * Pose le dernier jour du trimestre la ou la date manque. "&lt; 1000-01-01"
     * attrape la date zero qu'un MySQL non strict aurait mise a l'ajout de la
     * colonne.
     */
    private int completerDates() {
        List<Map<String, Object>> sansDate = jdbcTemplate.queryForList(
                "SELECT id_trimestre, annee, numero FROM " + TABLE
                        + " WHERE " + COLONNE + " IS NULL OR " + COLONNE + " < '1000-01-01'");
        for (Map<String, Object> ligne : sansDate) {
            int annee = ((Number) ligne.get("annee")).intValue();
            int numero = ((Number) ligne.get("numero")).intValue();
            jdbcTemplate.update("UPDATE " + TABLE + " SET " + COLONNE + " = ? WHERE id_trimestre = ?",
                    Date.valueOf(Trimestre.dernierJour(annee, numero)), ligne.get("id_trimestre"));
        }
        return sansDate.size();
    }

    /** null si la colonne n'existe pas, sinon vrai si elle accepte NULL ; quelle que soit la casse du SGBD. */
    private Boolean colonneNullable(DatabaseMetaData meta, String catalogue) throws SQLException {
        for (String table : List.of(TABLE, TABLE.toUpperCase(Locale.ROOT))) {
            try (ResultSet colonnes = meta.getColumns(catalogue, null, table, null)) {
                while (colonnes.next()) {
                    if (COLONNE.equalsIgnoreCase(colonnes.getString("COLUMN_NAME"))) {
                        return colonnes.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                    }
                }
            }
        }
        return null;
    }
}
