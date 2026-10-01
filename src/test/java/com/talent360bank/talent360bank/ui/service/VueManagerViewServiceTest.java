package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.DeclarationVigilanceRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Compte;
import com.talent360bank.talent360bank.ui.model.VueManager.CompteCase;
import com.talent360bank.talent360bank.ui.model.VueManager.Membre;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vue manager sur une vraie base : valeurs identiques a celles du moteur et de
 * la fiche collaborateur, equipe vide, membre sans donnees, 404, et un nombre
 * de requetes qui ne grandit pas avec l'equipe.
 *
 * <p>Sans transaction de test, comme FicheCollaborateurViewServiceTest : la vue
 * valide sa propre transaction.
 *
 * <p>Equipes : M100 manage V101 (talent), V102 (vigilance elevee), V103 (aucune
 * donnee) et V104 (archive, hors equipe) ; M200 manage 1 membre complet ; M300
 * en manage 6 ; M400 n'a personne. Chaque membre est dans sa propre agence,
 * pour que des chargements d'entite par membre se voient dans le compte des
 * requetes.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vue-manager;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VueManagerViewServiceTest {

    @Autowired
    private VueManagerViewService service;
    @Autowired
    private FicheCollaborateurViewService ficheService;
    @Autowired
    private VigilanceService vigilanceService;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private EntiteRepository entiteRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PotentielRepository potentielRepository;
    @Autowired
    private ScoreRepository scoreRepository;
    @Autowired
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private DeclarationVigilanceRepository declarationRepository;

    private Trimestre precedent;
    private Trimestre courant;
    private Entite departement;
    private int agences;

    @BeforeAll
    void poserLesDonnees() {
        precedent = trimestre(2025, 4);
        courant = trimestre(2026, 1);
        parametreRepository.save(Parametre.parDefaut(precedent));
        parametreRepository.save(Parametre.parDefaut(courant));

        Entite direction = entiteRepository.save(new Entite("Reseau Retail", TypeEntite.DIRECTION, null));
        departement = entiteRepository.save(new Entite("Nord", TypeEntite.DEPARTEMENT, direction));

        Manager m100 = manager("M100", "Idrissi", "Omar");
        membre("V101", "Alaoui", "Sara", m100, "92", "91", "80.00", "90.00", false);
        membre("V102", "Bennani", "Karim", m100, "70", "72", "40.00", "85.00", true);
        collaborateur("V103", "Chraibi", "Nadia", m100, StatutCollaborateur.ACTIF);
        collaborateur("V104", "Dahbi", "Yassine", m100, StatutCollaborateur.ARCHIVE);

        Manager m200 = manager("M200", "Fassi", "Hind");
        membre("V201", "Amrani", "Nabil", m200, "80", "80", "70.00", "82.00", true);

        Manager m300 = manager("M300", "Ghali", "Rim");
        for (int i = 1; i <= 6; i++) {
            membre("V30" + i, "Nom" + i, "Prenom" + i, m300, "8" + i, "7" + i, "65.00", "80.00", true);
        }

        manager("M400", "Hajji", "Mouna");

        // M500 : un membre sans aucune donnee de vigilance, un avec une declaration seule,
        // un avec un questionnaire seul.
        Manager m500 = manager("M500", "Idrissi", "Salma");
        collaborateur("V501", "Aziz", "Rachid", m500, StatutCollaborateur.ACTIF);
        Collaborateur declarationSeule = collaborateur("V502", "Berrada", "Laila", m500, StatutCollaborateur.ACTIF);
        DeclarationVigilance declaration = new DeclarationVigilance(declarationSeule, courant);
        declaration.setSansMobilite4Ans(true);
        declarationRepository.save(declaration);
        Collaborateur questionnaireSeul = collaborateur("V503", "Cherif", "Amine", m500, StatutCollaborateur.ACTIF);
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(questionnaireSeul);
        reponse.setTrimestre(courant);
        reponse.setScoreEngagement(new BigDecimal("75.00"));
        questionnaireRepository.save(reponse);

        // Scores, 9-box et vivier du trimestre, par le vrai moteur ; le manager M100 est note aussi.
        notes(collaborateurRepository.findById("M100").orElseThrow(), "88", "86");
        calculTrimestreService.calculer(courant);
    }

    // ------------------------------------------------------------ equipe complete

    @Test
    void la_vue_reprend_les_valeurs_du_moteur_et_de_la_fiche() {
        VueManager vue = service.construire("M100", 2026, 1);

        assertThat(vue.trimestre().libelle()).isEqualTo("T1 2026");
        assertThat(vue.manager().matricule()).isEqualTo("M100");
        assertThat(vue.manager().nom()).isEqualTo("Idrissi");
        assertThat(vue.manager().entite().chemin()).containsExactly("Reseau Retail", "Nord", "Agence 1");
        assertThat(vue.manager().neufBox()).isEqualTo(ficheService.construire("M100", 2026, 1).neufBox());

        // Equipe directe hors archive, triee par nom.
        assertThat(vue.membres()).extracting(Membre::matricule).containsExactly("V101", "V102", "V103");

        for (String matricule : List.of("V101", "V102")) {
            Membre membre = membre(vue, matricule);
            FicheCollaborateur fiche = ficheService.construire(matricule, 2026, 1);
            Score score = scoreRepository.findByCollaborateurIdCollaborateurAndTrimestre(matricule, courant)
                    .orElseThrow();
            assertThat(membre.scorePerformance()).isEqualByComparingTo(score.getScorePerformance());
            assertThat(membre.categoriePerformance()).isEqualTo(score.getCategoriePerformance().name());
            assertThat(membre.scorePotentiel()).isEqualByComparingTo(score.getScorePotentiel());
            assertThat(membre.categoriePotentiel()).isEqualTo(score.getCategoriePotentiel().name());
            // Meme case, meme statut talent, meme vigilance que la fiche et le moteur.
            assertThat(membre.neufBox()).isEqualTo(fiche.neufBox());
            assertThat(membre.estTalent()).isEqualTo(fiche.talent().estTalent());
            assertThat(membre.estHautPotentiel()).isEqualTo(fiche.talent().estHautPotentiel());
            assertThat(membre.estVivierSuccession()).isEqualTo(fiche.talent().estVivierSuccession());
            assertThat(membre.engagement()).isEqualByComparingTo(fiche.engagement());
            ResultatVigilance vigilance = transaction.execute(statut ->
                    vigilanceService.evaluer(collaborateurRepository.findById(matricule).orElseThrow(), courant));
            assertThat(membre.indiceVigilance()).isEqualByComparingTo(vigilance.indice());
            assertThat(membre.niveauVigilance()).isEqualTo(vigilance.niveau().name());
            assertThat(membre.aDesDonnees()).isTrue();
        }

        Membre talent = membre(vue, "V101");
        assertThat(talent.neufBox().numero()).isEqualTo(9);
        assertThat(talent.estTalent()).isTrue();
        // V102 : engagement 40 (< 60), baisse vs T4 2025, deux faits declares : 25 + 10 + 20 + 15 = 70.
        Membre fragile = membre(vue, "V102");
        assertThat(fragile.niveauVigilance()).isEqualTo("ELEVEE");
        assertThat(fragile.indiceVigilance()).isEqualByComparingTo("70.00");

        assertThat(vue.autoVsManager()).isNull();
        // V103 n'a aucune donnee de vigilance : nomme une fois, rien d'autre ne manque.
        assertThat(vue.donneesManquantes())
                .containsExactly("Aucune donnée de vigilance pour ce trimestre : Nadia Chraibi (V103)");
    }

    @Test
    void la_synthese_compte_et_moyenne_l_equipe() {
        VueManager vue = service.construire("M100", 2026, 1);
        VueManager.Synthese synthese = vue.synthese();
        Membre talent = membre(vue, "V101");
        Membre fragile = membre(vue, "V102");

        assertThat(synthese.effectif()).isEqualTo(3);
        assertThat(synthese.nbAvecScore()).isEqualTo(2);
        assertThat(synthese.moyennePerformance()).isEqualByComparingTo(
                talent.scorePerformance().add(fragile.scorePerformance()).divide(BigDecimal.valueOf(2)));
        assertThat(synthese.moyenneEngagement()).isEqualByComparingTo("60.00");
        assertThat(synthese.nbTalents()).isEqualTo(1);
        assertThat(synthese.nbVivierSuccession()).isEqualTo(1);

        assertThat(synthese.categoriesPerformance()).extracting(Compte::code)
                .containsExactly("EXCEPTIONNELLE", "ELEVEE", "SOLIDE", "A_RENFORCER", "INSUFFISANTE");
        assertThat(synthese.categoriesPerformance().stream().mapToInt(Compte::nombre).sum()).isEqualTo(2);
        assertThat(synthese.categoriesPotentiel()).extracting(Compte::code).containsExactly("ELEVE", "MOYEN", "FAIBLE");
        assertThat(synthese.neufBox()).extracting(CompteCase::numero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(synthese.neufBox().get(8).nombre()).isEqualTo(1);
        assertThat(synthese.neufBox().get(8).libelle()).isEqualTo("Talent clé");
        assertThat(synthese.neufBox().stream().mapToInt(CompteCase::nombre).sum()).isEqualTo(2);
        assertThat(synthese.niveauxVigilance()).extracting(Compte::code).containsExactly("FAIBLE", "MODEREE", "ELEVEE");
        assertThat(synthese.niveauxVigilance().get(2).nombre()).isEqualTo(1);
        // V103 (aucune donnee de vigilance) n'est compte dans aucun niveau.
        assertThat(synthese.niveauxVigilance().stream().mapToInt(Compte::nombre).sum()).isEqualTo(2);
        assertThat(synthese.nbSansVigilance()).isEqualTo(1);
    }

    @Test
    void un_membre_sans_donnees_reste_dans_l_equipe_avec_une_alerte() {
        VueManager vue = service.construire("M100", 2026, 1);

        Membre sansDonnees = membre(vue, "V103");
        assertThat(sansDonnees.aDesDonnees()).isFalse();
        assertThat(sansDonnees.scorePerformance()).isNull();
        assertThat(sansDonnees.neufBox()).isNull();
        assertThat(sansDonnees.estTalent()).isNull();
        // Aucune donnee de vigilance : ni indice ni niveau (pas un 0 / FAIBLE trompeur).
        assertThat(sansDonnees.indiceVigilance()).isNull();
        assertThat(sansDonnees.niveauVigilance()).isNull();

        assertThat(vue.alertes()).extracting(VueManager.Alerte::matricule, VueManager.Alerte::type)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("V102", "VIGILANCE_ELEVEE"),
                        org.assertj.core.groups.Tuple.tuple("V103", "EVALUATION_MANQUANTE"));
        assertThat(vue.alertes().get(1).message())
                .isEqualTo("Pas d'évaluation de performance ni de potentiel pour ce trimestre");
    }

    @Test
    void la_vigilance_est_absente_sans_aucune_donnee_et_calculee_des_qu_une_donnee_existe() {
        VueManager vue = service.construire("M500", 2026, 1);

        Membre rien = membre(vue, "V501");
        assertThat(rien.indiceVigilance()).isNull();
        assertThat(rien.niveauVigilance()).isNull();
        // Declaration seule : "aucun mouvement depuis 4 ans" = 20 points, FAIBLE.
        Membre declaration = membre(vue, "V502");
        assertThat(declaration.indiceVigilance()).isEqualByComparingTo("20.00");
        assertThat(declaration.niveauVigilance()).isEqualTo("FAIBLE");
        // Questionnaire seul (75, au-dessus du seuil) : aucun signal, mais une vraie donnee : 0, FAIBLE.
        Membre questionnaire = membre(vue, "V503");
        assertThat(questionnaire.indiceVigilance()).isEqualByComparingTo("0");
        assertThat(questionnaire.niveauVigilance()).isEqualTo("FAIBLE");

        assertThat(vue.synthese().nbSansVigilance()).isEqualTo(1);
        assertThat(vue.synthese().niveauxVigilance().get(0).nombre()).isEqualTo(2);
        assertThat(vue.donneesManquantes())
                .contains("Aucune donnée de vigilance pour ce trimestre : Rachid Aziz (V501)");
        // Memes valeurs que la fiche de chacun.
        assertThat(ficheService.construire("V501", 2026, 1).vigilance()).isNull();
        assertThat(ficheService.construire("V502", 2026, 1).vigilance().indice())
                .isEqualByComparingTo(declaration.indiceVigilance());
    }

    // ------------------------------------------------------------ cas limites

    @Test
    void une_equipe_vide_donne_une_synthese_vide_sans_erreur() {
        VueManager vue = service.construire("M400", 2026, 1);

        assertThat(vue.membres()).isEmpty();
        assertThat(vue.alertes()).isEmpty();
        assertThat(vue.synthese().effectif()).isZero();
        assertThat(vue.synthese().moyennePerformance()).isNull();
        assertThat(vue.synthese().moyenneEngagement()).isNull();
        assertThat(vue.synthese().neufBox()).hasSize(9).allSatisfy(c -> assertThat(c.nombre()).isZero());
        // M400 n'est pas note : sa propre case manque.
        assertThat(vue.manager().neufBox()).isNull();
        assertThat(vue.donneesManquantes()).containsExactly("Pas de case 9-box pour le manager sur ce trimestre");
    }

    @Test
    void un_non_manager_un_matricule_ou_un_trimestre_inconnu_est_introuvable() {
        assertThatThrownBy(() -> service.construire("V101", 2026, 1))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessage("Le collaborateur V101 n'est pas manager");
        assertThatThrownBy(() -> service.construire("INCONNU", 2026, 1))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessage("Aucun collaborateur INCONNU");
        assertThatThrownBy(() -> service.construire("M100", 2031, 2))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessage("Aucun trimestre T2 2031");
        assertThatThrownBy(() -> service.listerManagers(2031, 2))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void sans_reglages_la_vue_s_affiche_sans_calculs() {
        Trimestre sansReglages = trimestre(2025, 2);

        VueManager vue = service.construire("M100", 2025, 2);

        assertThat(vue.membres()).hasSize(3).allSatisfy(membre -> {
            assertThat(membre.neufBox()).isNull();
            assertThat(membre.indiceVigilance()).isNull();
        });
        // Sans reglages, personne n'a de vigilance.
        assertThat(vue.synthese().nbSansVigilance()).isEqualTo(3);
        assertThat(vue.donneesManquantes()).anyMatch(ligne -> ligne.startsWith("Aucun réglage pour T2 2025"));
        assertThat(sansReglages.getIdTrimestre()).isNotNull();
    }

    // ------------------------------------------------------------ liste et requetes

    @Test
    void la_liste_des_managers_donne_la_taille_de_chaque_equipe() {
        List<VueManager.ManagerResume> managers = service.listerManagers(2026, 1);

        assertThat(managers).extracting(VueManager.ManagerResume::matricule)
                .containsExactly("M200", "M300", "M400", "M100", "M500")
                .isEqualTo(managers.stream()
                        .sorted(java.util.Comparator.comparing(VueManager.ManagerResume::nom))
                        .map(VueManager.ManagerResume::matricule).toList());
        assertThat(managers).filteredOn(m -> m.matricule().equals("M100")).singleElement()
                .satisfies(m -> {
                    assertThat(m.tailleEquipe()).isEqualTo(3);   // l'archive ne compte pas
                    assertThat(m.entite().libelle()).isEqualTo("Agence 1");
                });
        assertThat(managers).filteredOn(m -> m.matricule().equals("M300")).singleElement()
                .extracting(VueManager.ManagerResume::tailleEquipe).isEqualTo(6);
        assertThat(managers).filteredOn(m -> m.matricule().equals("M400")).singleElement()
                .extracting(VueManager.ManagerResume::tailleEquipe).isEqualTo(0);
    }

    @Test
    void le_nombre_de_requetes_ne_grandit_pas_avec_l_equipe() {
        long petiteEquipe = requetes(() -> service.construire("M200", 2026, 1));   // 1 membre
        long grandeEquipe = requetes(() -> service.construire("M300", 2026, 1));   // 6 membres

        assertThat(service.construire("M300", 2026, 1).membres()).hasSize(6);
        assertThat(grandeEquipe).isEqualTo(petiteEquipe);
        assertThat(petiteEquipe).isLessThanOrEqualTo(15);

        long listeAvant = requetes(() -> service.listerManagers(2026, 1));
        assertThat(listeAvant).isLessThanOrEqualTo(3);
    }

    // ------------------------------------------------------------ outils

    private long requetes(Runnable appel) {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        appel.run();
        return statistiques.getPrepareStatementCount();
    }

    private static Membre membre(VueManager vue, String matricule) {
        return vue.membres().stream().filter(m -> m.matricule().equals(matricule)).findFirst().orElseThrow();
    }

    private Trimestre trimestre(int annee, int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(annee);
        trimestre.setNumero(numero);
        return trimestreRepository.save(trimestre);
    }

    /** Une agence par collaborateur : un chargement d'entite par membre se verrait dans le compte. */
    private Entite nouvelleAgence() {
        agences++;
        return entiteRepository.save(new Entite("Agence " + agences, TypeEntite.AGENCE, departement));
    }

    private Manager manager(String id, String nom, String prenom) {
        return managerRepository.save(new Manager(collaborateur(id, nom, prenom, null, StatutCollaborateur.ACTIF)));
    }

    private Collaborateur collaborateur(String id, String nom, String prenom, Manager manager,
                                        StatutCollaborateur statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom(nom);
        collaborateur.setPrenom(prenom);
        collaborateur.setFonction("Conseiller");
        collaborateur.setEntite(nouvelleAgence());
        collaborateur.setManager(manager);
        collaborateur.setDateEntree(LocalDate.of(2018, 1, 1));
        collaborateur.setStatut(statut);
        return collaborateurRepository.save(collaborateur);
    }

    /**
     * Membre avec toutes ses donnees : notes du trimestre, score precedent (pour la
     * baisse de performance), questionnaire, et, si demande, deux faits de vigilance.
     */
    private void membre(String id, String nom, String prenom, Manager manager, String performance,
                        String potentiel, String engagement, String performancePrecedente, boolean faits) {
        Collaborateur collaborateur = collaborateur(id, nom, prenom, manager, StatutCollaborateur.ACTIF);
        notes(collaborateur, performance, potentiel);

        Score ancien = new Score();
        ancien.setCollaborateur(collaborateur);
        ancien.setTrimestre(precedent);
        ancien.setScorePerformance(new BigDecimal(performancePrecedente));
        ancien.setScorePotentiel(new BigDecimal("80.00"));
        ancien.setPositionBox("Confirmé");
        scoreRepository.save(ancien);

        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(collaborateur);
        reponse.setTrimestre(courant);
        reponse.setScoreEngagement(new BigDecimal(engagement));
        questionnaireRepository.save(reponse);

        if (faits) {
            DeclarationVigilance declaration = new DeclarationVigilance(collaborateur, courant);
            declaration.setSansMobilite4Ans(true);
            declaration.setMobiliteNonTraitee(true);
            declarationRepository.save(declaration);
        }
    }

    private void notes(Collaborateur collaborateur, String performance, String potentiel) {
        BigDecimal p = new BigDecimal(performance);
        BigDecimal q = new BigDecimal(potentiel);
        performanceRepository.save(new Performance(collaborateur, courant, p, p, p, p, p));
        potentielRepository.save(new Potentiel(collaborateur, courant, q, q, q, q, q, q, q));
    }
}
