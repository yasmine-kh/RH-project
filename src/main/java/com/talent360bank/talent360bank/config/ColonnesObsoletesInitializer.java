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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rend nullables au demarrage les colonnes de reglages retirees du modele.
 *
 * <p>ddl-auto=update ajoute les colonnes mais n'en supprime ni n'en modifie
 * aucune. Les poids des sources (src_*) ont ete crees NOT NULL sans valeur par
 * defaut : une fois le champ retire de Parametre, Hibernate ne les renseigne
 * plus et MySQL en mode strict refuserait tout nouveau jeu de reglages. Les
 * rendre nullables suffit ; les valeurs deja en base sont conservees, rien
 * n'est supprime.
 *
 * <p>Sans effet sur une base neuve, ou les colonnes n'existent pas, et sur une
 * base deja passee par ici.
 *
 * <p>A supprimer (avec son test) une fois toutes les bases recreees : voir la
 * checklist de livraison (docs/guide-developpeur.md, section 12, a faire de Jas).
 */
@Component
public class ColonnesObsoletesInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ColonnesObsoletesInitializer.class);

    static final String TABLE = "parametre";

    /** Poids des sources d'evaluation, retires : aucune formule du classeur ne les utilise. */
    static final List<String> COLONNES = List.of(
            "src_auto_evaluation", "src_manager", "src_competences", "src_engagement");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ColonnesObsoletesInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        List<String> aLiberer;
        boolean mysql;
        try (Connection connexion = dataSource.getConnection()) {
            DatabaseMetaData meta = connexion.getMetaData();
            mysql = meta.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
            aLiberer = colonnesNonNullables(meta, connexion.getCatalog());
        }

        for (String colonne : aLiberer) {
            jdbcTemplate.execute(mysql
                    ? "ALTER TABLE " + TABLE + " MODIFY COLUMN " + colonne + " DECIMAL(5,2) NULL"
                    : "ALTER TABLE " + TABLE + " ALTER COLUMN " + colonne + " SET NULL");
        }
        if (!aLiberer.isEmpty()) {
            log.info("Parametres : colonne(s) retiree(s) du modele rendue(s) nullable(s) : {}", aLiberer);
        }
    }

    /** Colonnes obsoletes encore presentes et NOT NULL, quelle que soit la casse du SGBD. */
    private List<String> colonnesNonNullables(DatabaseMetaData meta, String catalogue) throws SQLException {
        List<String> trouvees = new ArrayList<>();
        for (String table : List.of(TABLE, TABLE.toUpperCase(Locale.ROOT))) {
            try (ResultSet colonnes = meta.getColumns(catalogue, null, table, null)) {
                while (colonnes.next()) {
                    String nom = colonnes.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                    if (COLONNES.contains(nom) && colonnes.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                            && !trouvees.contains(nom)) {
                        trouvees.add(nom);
                    }
                }
            }
        }
        return trouvees;
    }
}
