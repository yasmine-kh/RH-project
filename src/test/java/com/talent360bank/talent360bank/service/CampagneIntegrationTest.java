package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.controller.ChargeurRessources;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.talent360bank.talent360bank.service.ClasseurDeTest.*;
import static com.talent360bank.talent360bank.service.ImportClasseurService.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le cycle de vie d'une campagne, bout en bout sur le vrai moteur : import et
 * calcul, fichier corrige pour le meme trimestre, puis campagne suivante.
 *
 * <p>L'ordre des methodes est significatif : chacune part de l'etat laisse
 * par la precedente, comme les imports successifs d'un RH. Base en memoire a
 * part pour ne pas croiser les autres tests.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:campagne;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CampagneIntegrationTest {

    private static final int ANNEE = 2026;

    @Autowired
    private CampagneService campagneService;
    @Autowired
    private ScoreRepository scoreRepository;
    @Autowired
    private AppartenanceVivierRepository appartenanceRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private ChargeurRessources chargeur;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private <T> T enTransaction(Supplier<T> lecture) {
        return new TransactionTemplate(transactionManager).execute(statut -> lecture.get());
    }

    private ResultatCampagne importer(ClasseurDeTest classeur, int numero) {
        return campagneService.importer(classeur.fichier(), ANNEE, numero, false, true);
    }

    private Trimestre trimestre(int numero) {
        return trimestreRepository.findByNumeroAndAnnee(numero, ANNEE).orElseThrow();
    }

    private Map<String, Score> scores(int numero) {
        return enTransaction(() -> scoreRepository.findByTrimestreAvecCollaborateur(trimestre(numero)).stream()
                .peek(score -> score.getDirection())
                .collect(Collectors.toMap(s -> s.getCollaborateur().getIdCollaborateur(), Function.identity())));
    }

    private int appartenances(int numero) {
        return enTransaction(() -> appartenanceRepository.findByTrimestre(trimestre(numero)).size());
    }

    @Test
    @Order(1)
    void l_import_lance_le_calcul_complet() {
        ResultatCampagne resultat = importer(ClasseurDeTest.complet(), 3);

        assertThat(resultat.importation().statut()).isEqualTo(StatutImport.SUCCES);
        assertThat(resultat.erreurCalcul()).isNull();
        assertThat(resultat.calcul()).isNotNull();
        // E1 et E3 sont scores ; E2 est Inactif dans le fichier.
        assertThat(resultat.calcul().scores().scoresEnregistres()).hasSize(2);
        assertThat(resultat.calcul().placements().scoresEnregistres()).hasSize(2);
        Map<String, Score> scores = scores(3);
        assertThat(scores).containsOnlyKeys(E1, E3);
        assertThat(scores.get(E1).getPositionBox()).isNotNull();
        // Siham est talent : elle entre au vivier de releve.
        assertThat(resultat.calcul().vivierReleve().crees())
                .extracting(a -> a.getCollaborateur().getIdCollaborateur()).contains(E1);
    }

    @Test
    @Order(2)
    void sans_calcul_l_import_n_est_pas_suivi_du_calcul() {
        ResultatCampagne resultat = campagneService.importer(ClasseurDeTest.complet().fichier(), ANNEE, 3,
                false, false);

        assertThat(resultat.importation().statut()).isEqualTo(StatutImport.SUCCES);
        assertThat(resultat.calcul()).isNull();
    }

    @Test
    @Order(3)
    void un_fichier_corrige_puis_recalcule_remplace_les_resultats_sans_les_doubler() {
        Map<String, Score> avant = scores(3);
        int appartenancesAvant = appartenances(3);
        long scoresEnBaseAvant = scoreRepository.count();

        // Notes de Siham corrigees a la baisse : elle n'est plus talent.
        ResultatCampagne resultat = importer(ClasseurDeTest.complet()
                .remplacer(FEUILLE_PERFORMANCE, 0, E1, "Siham Alaoui", "Reseau Retail", 50, 50, 50, 50, 50), 3);

        assertThat(resultat.importation().statut()).isEqualTo(StatutImport.SUCCES);
        Map<String, Score> apres = scores(3);
        // Memes lignes, nouvelles valeurs.
        assertThat(apres).containsOnlyKeys(avant.keySet());
        assertThat(apres.get(E1).getIdScore()).isEqualTo(avant.get(E1).getIdScore());
        assertThat(apres.get(E1).getScorePerformance()).isEqualByComparingTo("50");
        assertThat(apres.get(E1).getPositionBox()).isNotEqualTo(avant.get(E1).getPositionBox());
        assertThat(apres.get(E3).getScorePerformance()).isEqualByComparingTo(avant.get(E3).getScorePerformance());
        assertThat(scoreRepository.count()).isEqualTo(scoresEnBaseAvant);
        // Siham sort du vivier de releve, rien n'est double.
        int appartenancesApres = appartenances(3);
        assertThat(appartenancesApres).isLessThan(appartenancesAvant);
        assertThat(resultat.calcul().vivierReleve().crees())
                .extracting(a -> a.getCollaborateur().getIdCollaborateur()).doesNotContain(E1);

        // Recalculer le meme fichier une fois de plus ne change aucun compte.
        importer(ClasseurDeTest.complet()
                .remplacer(FEUILLE_PERFORMANCE, 0, E1, "Siham Alaoui", "Reseau Retail", 50, 50, 50, 50, 50), 3);
        assertThat(scoreRepository.count()).isEqualTo(scoresEnBaseAvant);
        assertThat(appartenances(3)).isEqualTo(appartenancesApres);
    }

    @Test
    @Order(4)
    void un_collaborateur_sorti_du_fichier_perd_son_score_du_trimestre() {
        ResultatCampagne resultat = importer(ClasseurDeTest.complet().sansCollaborateur(E1), 3);

        assertThat(resultat.importation().desactives()).containsExactly(E1);
        assertThat(collaborateurRepository.findById(E1).orElseThrow().getStatut())
                .isEqualTo(StatutCollaborateur.INACTIF);
        assertThat(scores(3)).containsOnlyKeys(E3);
        assertThat(enTransaction(() -> appartenanceRepository.findByTrimestre(trimestre(3)).stream()
                .map(a -> a.getCollaborateur().getIdCollaborateur()).toList())).doesNotContain(E1);

        // Il revient dans le fichier : reactive et de nouveau score.
        ResultatCampagne retour = importer(ClasseurDeTest.complet(), 3);
        assertThat(retour.importation().reactives()).containsExactly(E1);
        assertThat(scores(3)).containsOnlyKeys(E1, E3);
    }

    @Test
    @Order(5)
    void la_campagne_suivante_herite_des_reglages_et_garde_l_historique() {
        // Le RH a ajuste un seuil du T3.
        enTransaction(() -> {
            Parametre reglages = parametreRepository.findByTrimestre(trimestre(3)).orElseThrow();
            reglages.getSeuilsTalent().setSeuilPerformance(new BigDecimal("88"));
            return parametreRepository.save(reglages);
        });

        // T4 : Mounir (E3) change de direction.
        ResultatCampagne t4 = importer(ClasseurDeTest.complet()
                .cellule(FEUILLE_COLLABORATEURS, 2, 7, "Risques")
                .cellule(FEUILLE_COLLABORATEURS, 2, 8, "Risques - Credit"), 4);

        assertThat(t4.importation().statut()).isEqualTo(StatutImport.SUCCES);
        assertThat(enTransaction(() -> parametreRepository.findByTrimestre(trimestre(4)).orElseThrow()
                .getSeuilsTalent().getSeuilPerformance())).isEqualByComparingTo("88");

        // Le T3 garde la direction du moment ; le T4 porte la nouvelle.
        assertThat(scores(3).get(E3).getDirection()).isEqualTo("Reseau Retail");
        assertThat(scores(4).get(E3).getDirection()).isEqualTo("Risques");
        assertThat(enTransaction(() -> scores(3).get(E1).getManager().getIdCollaborateur())).isEqualTo(E3);
    }

    @Test
    @Order(6)
    void le_dernier_trimestre_est_le_plus_recent_pas_le_dernier_importe() {
        // Fichier corrige du T3 apres l'import du T4.
        importer(ClasseurDeTest.complet(), 3);

        Trimestre dernier = chargeur.exigerTrimestreOuDernier(null, null);
        assertThat(List.of(dernier.getAnnee(), dernier.getNumero())).containsExactly(ANNEE, 4);
    }
}
