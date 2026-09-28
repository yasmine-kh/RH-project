package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une base anterieure au retrait des poids des sources : les colonnes src_*
 * existent, NOT NULL et sans valeur par defaut. Sans le passage au demarrage,
 * aucun nouveau jeu de reglages ne peut etre enregistre.
 *
 * <p>Sous H2, un ALTER TABLE valide la transaction : les colonnes ajoutees ici
 * sont retirees apres chaque test pour ne pas gener les autres classes qui
 * partagent la meme base en memoire.
 */
@DataJpaTest
class ColonnesObsoletesInitializerJpaTest {

    /** Annees distinctes : le rollback n'efface pas les lignes apres un ALTER. */
    private static final AtomicInteger ANNEES = new AtomicInteger(2100);

    @Autowired
    private DataSource dataSource;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private EntityManager entityManager;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private void ajouterLesColonnesObsoletes() {
        for (String colonne : ColonnesObsoletesInitializer.COLONNES) {
            jdbc().execute("ALTER TABLE parametre ADD COLUMN IF NOT EXISTS " + colonne + " DECIMAL(5,2) NOT NULL");
        }
    }

    @AfterEach
    void retirerLesColonnesObsoletes() {
        for (String colonne : ColonnesObsoletesInitializer.COLONNES) {
            jdbc().execute("ALTER TABLE parametre DROP COLUMN IF EXISTS " + colonne);
        }
    }

    private Parametre nouveauxReglages() {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(ANNEES.getAndIncrement());
        trimestreRepository.save(trimestre);
        return Parametre.parDefaut(trimestre);
    }

    private String nullable(String colonne) {
        return jdbc().queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'PARAMETRE' AND COLUMN_NAME = ?", String.class, colonne.toUpperCase());
    }

    @Test
    void sans_le_passage_de_nouveaux_reglages_sont_refuses() {
        ajouterLesColonnesObsoletes();

        assertThatThrownBy(() -> {
            parametreRepository.save(nouveauxReglages());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void apres_le_passage_les_colonnes_sont_nullables_et_les_reglages_s_enregistrent() throws Exception {
        ajouterLesColonnesObsoletes();

        new ColonnesObsoletesInitializer(dataSource).run(null);

        for (String colonne : ColonnesObsoletesInitializer.COLONNES) {
            assertThat(nullable(colonne)).as(colonne).isEqualTo("YES");
        }
        Parametre enregistre = parametreRepository.save(nouveauxReglages());
        entityManager.flush();
        assertThat(enregistre.getIdParametre()).isNotNull();
    }

    @Test
    void le_passage_est_sans_effet_sur_une_base_neuve_ou_deja_traitee() throws Exception {
        ColonnesObsoletesInitializer initializer = new ColonnesObsoletesInitializer(dataSource);

        initializer.run(null);   // base neuve : les colonnes n'existent pas
        ajouterLesColonnesObsoletes();
        initializer.run(null);
        initializer.run(null);   // deja traitee

        assertThat(nullable("src_manager")).isEqualTo("YES");
    }
}
