package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les deux filets sur une vraie base : la valeur par defaut portee par les
 * colonnes ajoutees apres coup, et le complement au demarrage d'une ligne
 * dont ces colonnes sont a NULL.
 */
@DataJpaTest
class ParametreInitializerJpaTest {

    private static final List<String> COLONNES_AJOUTEES = List.of(
            "exp_points_par_annee", "exp_plafond", "comp_points_par_niveau_manquant", "comp_niveau_par_defaut");

    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @SuppressWarnings("unchecked")
    void lesColonnesAjouteesPortentLeurValeurParDefautEnBase() {
        List<Object[]> lignes = entityManager.createNativeQuery(
                "SELECT LOWER(COLUMN_NAME), COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'PARAMETRE'").getResultList();
        Map<String, BigDecimal> defauts = new HashMap<>();
        for (Object[] ligne : lignes) {
            if (COLONNES_AJOUTEES.contains((String) ligne[0]) && ligne[1] != null) {
                defauts.put((String) ligne[0], new BigDecimal(ligne[1].toString().replace("'", "")));
            }
        }

        assertThat(defauts.get("exp_points_par_annee")).isEqualByComparingTo("8");
        assertThat(defauts.get("exp_plafond")).isEqualByComparingTo("100");
        assertThat(defauts.get("comp_points_par_niveau_manquant")).isEqualByComparingTo("20");
        assertThat(defauts.get("comp_niveau_par_defaut")).isEqualByComparingTo("3");
    }

    @Test
    void uneLigneAnterieureAuxDeuxBaremesEstCompletee() {
        Integer id = ligneExistante("exp_points_par_annee = NULL, exp_plafond = NULL, "
                + "comp_points_par_niveau_manquant = NULL, comp_niveau_par_defaut = NULL");

        // Toutes les colonnes d'un bloc a NULL : Hibernate rend le bloc a null.
        Parametre avant = relire(id);
        assertThat(avant.getBaremeExperience()).isNull();
        assertThat(avant.getBaremeCompetences()).isNull();

        new ParametreInitializer(parametreRepository).run(null);

        Parametre complete = relire(id);
        assertThat(complete.getBaremeExperience().getPointsParAnnee()).isEqualByComparingTo("8");
        assertThat(complete.getBaremeExperience().getPlafond()).isEqualByComparingTo("100");
        assertThat(complete.getBaremeCompetences().getPointsParNiveauManquant()).isEqualByComparingTo("20");
        assertThat(complete.getBaremeCompetences().getNiveauParDefaut()).isEqualTo(3);
    }

    @Test
    void uneLigneAnterieureAuSeulNiveauParDefautGardeSesAutresReglages() {
        Integer id = ligneExistante("comp_points_par_niveau_manquant = 25, comp_niveau_par_defaut = NULL");

        new ParametreInitializer(parametreRepository).run(null);

        Parametre complete = relire(id);
        assertThat(complete.getBaremeCompetences().getNiveauParDefaut()).isEqualTo(3);
        assertThat(complete.getBaremeCompetences().getPointsParNiveauManquant()).isEqualByComparingTo("25");
    }

    /**
     * Une ligne de parametre telle qu'une colonne nullable l'aurait laissee.
     * Le schema genere rend ces colonnes NOT NULL : on leve la contrainte pour
     * reproduire ce cas.
     */
    private Integer ligneExistante(String modification) {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        trimestreRepository.save(trimestre);
        Integer id = parametreRepository.save(Parametre.parDefaut(trimestre)).getIdParametre();
        entityManager.flush();

        for (String colonne : COLONNES_AJOUTEES) {
            entityManager.createNativeQuery("ALTER TABLE parametre ALTER COLUMN " + colonne + " SET NULL")
                    .executeUpdate();
        }
        entityManager.createNativeQuery("UPDATE parametre SET " + modification).executeUpdate();
        entityManager.clear();
        return id;
    }

    private Parametre relire(Integer id) {
        entityManager.flush();
        entityManager.clear();
        return parametreRepository.findById(id).orElseThrow();
    }
}
