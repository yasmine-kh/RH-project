package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ClasseurDeTest;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Une colonne d'entite qui pointe nulle part (0 ecrit par une migration ddl-auto, comme
 * rattachement_vivier.entite_id sur la base MySQL de l'equipe) ne fait tomber aucune page : le
 * collaborateur est "Sans direction". Et un nouvel import n'ecrit jamais 0 ni une cle inexistante.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:entites-manquantes;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class EntitesManquantesIntegrationTest {

    private static final int ANNEE = 2090;
    private static final int NUMERO = 3;
    private static final String T = ANNEE + "-" + NUMERO;
    private static final String E1 = ClasseurDeTest.E1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ImportService importService;
    @Autowired
    private TrimestreService trimestreService;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private JdbcTemplate jdbc;

    private String manager;

    @BeforeAll
    void importerCalculerPuisCasserLesReferences() {
        assertThat(importService.importer(ClasseurDeTest.complet().fichier("classeur-test.xlsx"), ANNEE, NUMERO)
                .statut()).isEqualTo(StatutImport.SUCCES);
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE).orElseThrow();
        trimestreService.modifierDateReference(trimestre, LocalDate.of(ANNEE, 9, 15));
        calculTrimestreService.calculer(trimestre);
        // Matricule du manager de E1 (la Vue manager est adressee par matricule).
        manager = jdbc.queryForList("select m.id_collaborateur from manager m join collaborateur c "
                + "on c.id_manager = m.id_manager where c.id_collaborateur = ?", String.class, E1)
                .stream().findFirst().orElse(null);

        // Comme sur la base MySQL : des cles vers une entite qui n'existe pas.
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        // Une ligne a 0 comme sur MySQL ; les autres vers des cles absentes (index unique sur entite_id en H2).
        jdbc.update("update rattachement_vivier set entite_id = 900000 + id_rattachement");
        jdbc.update("update rattachement_vivier set entite_id = 0 "
                + "where id_rattachement = (select min(id_rattachement) from rattachement_vivier)");
        jdbc.update("update collaborateur set id_entite = 0 where id_collaborateur = ?", E1);
        jdbc.update("update score set id_entite = 0 where id_collaborateur = ?", E1);
        jdbc.update("update poste set id_entite = 999999");
        jdbc.update("update manager set id_entite = 999998");
        jdbc.update("update entite set id_entite_parent = 999997 where id_entite_parent is not null "
                + "and id_entite = (select min(id_entite) from entite where id_entite_parent is not null)");
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url)).andReturn();
    }

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    void aucune_page_ne_tombe() throws Exception {
        String api = "?annee=" + ANNEE + "&numero=" + NUMERO;
        String trimestre = "/api/trimestres/" + ANNEE + "/" + NUMERO;
        List<String> urls = new ArrayList<>(List.of(
                "/?trimestre=" + T, "/collaborateurs?trimestre=" + T, "/collaborateurs?trimestre=" + T + "&q=" + E1,
                "/9box?trimestre=" + T, "/viviers?trimestre=" + T, "/postes-critiques?trimestre=" + T,
                "/competences?trimestre=" + T, "/comite-talent?trimestre=" + T, "/alertes?trimestre=" + T,
                "/notifications", "/campagne?trimestre=" + T, "/managers", "/entites",
                "/fiche-collaborateur?matricule=" + E1 + "&trimestre=" + T,
                trimestre + "/collaborateurs", trimestre + "/talents", trimestre + "/vigilance",
                trimestre + "/competences", trimestre + "/postes-cibles", trimestre + "/campagne",
                "/api/viviers-thematiques" + api, "/api/viviers/synthese" + api, "/api/postes-critiques" + api,
                "/api/comite-talent" + api, "/api/dashboard/synthese" + api, "/api/notifications",
                "/api/collaborateurs/" + E1));
        if (manager != null) {
            urls.add("/managers/" + manager);
        }
        List<String> enEchec = new ArrayList<>();
        for (String url : urls) {
            int statut = page(url).getResponse().getStatus();
            if (statut != 200) {
                enEchec.add(url + " -> " + statut);
            }
        }
        assertThat(enEchec).isEmpty();
    }

    @Test
    @Order(2)
    void le_collaborateur_sans_entite_est_sans_direction() throws Exception {
        assertThat(html(page("/collaborateurs?trimestre=" + T + "&q=" + E1))).contains(E1, "Sans direction");
        assertThat(html(page("/fiche-collaborateur?matricule=" + E1 + "&trimestre=" + T))).contains("Sans direction");
        assertThat(html(page("/?trimestre=" + T))).contains("Sans direction");
        assertThat(html(page("/entites"))).contains("Sans direction", "collaborateur(s) sans entité connue");
        assertThat(html(page("/viviers?trimestre=" + T))).doesNotContain("Une erreur est survenue");
    }

    @Test
    @Order(3)
    void le_recalcul_puis_un_nouvel_import_n_ecrivent_ni_zero_ni_cle_inexistante() throws Exception {
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE).orElseThrow();
        // Recalcul : la cle cassee du collaborateur E1 (0) n'est pas recopiee sur son score.
        jdbc.update("update score set id_entite = null where id_collaborateur = ?", E1);
        calculTrimestreService.calculer(trimestre);
        assertThat(jdbc.queryForObject("select count(*) from score where id_entite = 0", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select id_entite from score where id_collaborateur = ?", Integer.class, E1))
                .isNull();

        int cassesAvant = rattachementsCasses();
        assertThat(importService.importer(ClasseurDeTest.complet().fichier("classeur-test.xlsx"), ANNEE, NUMERO)
                .statut()).isEqualTo(StatutImport.SUCCES);

        // L'import a recree chaque rattachement de la feuille sur une vraie direction, sans toucher aux
        // lignes cassees (elles relevent de la requete de reparation) ni en ecrire de nouvelles.
        assertThat(rattachementsCasses()).isEqualTo(cassesAvant);
        assertThat(jdbc.queryForObject("select count(*) from rattachement_vivier rv join entite e "
                + "on e.id_entite = rv.entite_id and e.type = 'DIRECTION'", Integer.class)).isEqualTo(2);
        // Le collaborateur retrouve son entite ; aucune autre cle a 0 ou vers une entite absente.
        for (String colonne : List.of("collaborateur.id_entite", "score.id_entite", "manager.id_entite",
                "poste.id_entite")) {
            String table = colonne.substring(0, colonne.indexOf('.'));
            assertThat(jdbc.queryForObject("select count(*) from " + table + " t where t."
                    + colonne.substring(colonne.indexOf('.') + 1) + " = 0", Integer.class)).as(colonne).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from collaborateur c left join entite e "
                + "on e.id_entite = c.id_entite where c.id_entite is not null and e.id_entite is null",
                Integer.class)).isZero();
        assertThat(page("/viviers?trimestre=" + T).getResponse().getStatus()).isEqualTo(200);
    }

    private int rattachementsCasses() {
        return jdbc.queryForObject("select count(*) from rattachement_vivier rv left join entite e "
                + "on e.id_entite = rv.entite_id where e.id_entite is null", Integer.class);
    }
}
