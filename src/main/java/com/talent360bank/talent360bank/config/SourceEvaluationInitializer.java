package com.talent360bank.talent360bank.config;

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
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Passe les tables performance et potentiel a deux evaluations par
 * collaborateur et trimestre (auto-evaluation et manager).
 *
 * <p>ddl-auto=update ajoute la colonne source_evaluation (valeur par defaut
 * MANAGER, donc les lignes existantes sont des evaluations du manager) et la
 * nouvelle contrainte unique (collaborateur, trimestre, source), mais ne
 * supprime jamais l'ancienne (collaborateur, trimestre), qui refuserait toute
 * auto-evaluation a cote d'une evaluation du manager. Ce passage :
 * <ol>
 *   <li>ajoute la colonne si l'ALTER de Hibernate a echoue, et pose MANAGER
 *   sur toute ligne sans source ;</li>
 *   <li>cree la nouvelle contrainte si elle manque, avant de retirer
 *   l'ancienne : sous MySQL, la cle etrangere sur id_collaborateur exige un
 *   index qui commence par cette colonne ;</li>
 *   <li>supprime l'ancienne contrainte.</li>
 * </ol>
 *
 * <p>Sans effet sur une base neuve et sur une base deja passee par ici.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SourceEvaluationInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SourceEvaluationInitializer.class);

    static final List<String> TABLES = List.of("performance", "potentiel");
    static final String COLONNE = "source_evaluation";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public SourceEvaluationInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** Ancienne contrainte unique : une seule evaluation par collaborateur et trimestre. */
    static String ancienneContrainte(String table) {
        return "uk_" + table + "_collaborateur_trimestre";
    }

    /** Contrainte unique du modele : une evaluation par collaborateur, trimestre et source. */
    static String nouvelleContrainte(String table) {
        return ancienneContrainte(table) + "_source";
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        boolean mysql;
        String schema;
        try (Connection connexion = dataSource.getConnection()) {
            mysql = connexion.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
            schema = mysql ? connexion.getCatalog() : connexion.getSchema();
        }

        for (String table : TABLES) {
            if (!colonnePresente(schema, table)) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + COLONNE
                        + " VARCHAR(10) DEFAULT 'MANAGER' NOT NULL");
                log.info("Evaluations : colonne {}.{} ajoutee", table, COLONNE);
            }
            // '' : la valeur qu'un MySQL non strict met dans un ENUM ajoute sans defaut.
            int completees = jdbcTemplate.update("UPDATE " + table + " SET " + COLONNE + " = 'MANAGER' WHERE "
                    + COLONNE + " IS NULL" + (mysql ? " OR " + COLONNE + " = ''" : ""));
            if (completees > 0) {
                log.info("Evaluations : {} ligne(s) de {} marquee(s) MANAGER", completees, table);
            }

            Set<String> uniques = contraintesUniques(schema, table);
            if (!uniques.contains(nouvelleContrainte(table))) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + nouvelleContrainte(table)
                        + " UNIQUE (id_collaborateur, id_trimestre, " + COLONNE + ")");
                log.info("Evaluations : contrainte {} creee", nouvelleContrainte(table));
            }
            if (uniques.contains(ancienneContrainte(table))) {
                jdbcTemplate.execute("ALTER TABLE " + table + (mysql ? " DROP INDEX " : " DROP CONSTRAINT ")
                        + ancienneContrainte(table));
                log.info("Evaluations : contrainte {} supprimee (une evaluation par source desormais)",
                        ancienneContrainte(table));
            }
        }
    }

    private boolean colonnePresente(String schema, String table) {
        Integer nombre = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?",
                Integer.class, schema, table, COLONNE);
        return nombre != null && nombre > 0;
    }

    /** Noms des contraintes uniques de la table, en minuscules quelle que soit la casse du SGBD. */
    private Set<String> contraintesUniques(String schema, String table) {
        return new HashSet<>(jdbcTemplate.queryForList("SELECT LOWER(CONSTRAINT_NAME) "
                        + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = ? AND LOWER(TABLE_NAME) = ? AND CONSTRAINT_TYPE = 'UNIQUE'",
                String.class, schema, table));
    }
}
