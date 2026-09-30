package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.RattachementVivier;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.SuccesseurIdentifie;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.SuccessionService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.GapCompetence;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fiche collaborateur sur une vraie base : les valeurs sont celles des services
 * du moteur, les manques ne levent rien, l'historique couvre les trimestres
 * precedents.
 *
 * <p>Volontairement sans transaction de test : la fiche ouvre et valide la
 * sienne, comme en production. Un bloc qui marquerait la transaction pour
 * annulation ferait donc echouer ces tests.
 *
 * <p>Organigramme : Reseau Retail (direction) > Nord (departement) > Tanger
 * (agence). F001 (manager) manage F002 (fiche complete) et F003 (sans potentiel).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fiche;DB_CLOSE_DELAY=-1;MODE=MySQL")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FicheCollaborateurViewServiceTest {

    @Autowired
    private FicheCollaborateurViewService service;

    @Autowired
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private SuccessionService successionService;
    @Autowired
    private VigilanceService vigilanceService;
    @Autowired
    private TalentService talentService;
    @Autowired
    private CompetenceCollaborateurService competenceCollaborateurService;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

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
    private CompetenceRepository competenceRepository;
    @Autowired
    private CompetenceCollaborateurRepository competenceCollaborateurRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private SuccesseurIdentifieRepository successeurIdentifieRepository;
    @Autowired
    private ValidationComiteRepository validationComiteRepository;
    @Autowired
    private RattachementVivierRepository rattachementVivierRepository;
    @Autowired
    private AppartenanceVivierRepository appartenanceVivierRepository;
    @Autowired
    private Matrice9BoxRepository matrice9BoxRepository;

    private Trimestre t1SansReglages;
    private Trimestre t3;
    private Trimestre t4;
    private Trimestre courant;
    private Collaborateur complet;

    @BeforeAll
    void poserLesDonnees() {
        t1SansReglages = trimestre(2025, 1);
        t3 = trimestre(2025, 3);
        t4 = trimestre(2025, 4);
        courant = trimestre(2026, 1);
        parametreRepository.save(Parametre.parDefaut(t3));
        parametreRepository.save(Parametre.parDefaut(t4));
        parametreRepository.save(Parametre.parDefaut(courant));

        Entite direction = entiteRepository.save(new Entite("Reseau Retail", TypeEntite.DIRECTION, null));
        Entite departement = entiteRepository.save(new Entite("Nord", TypeEntite.DEPARTEMENT, direction));
        Entite agence = entiteRepository.save(new Entite("Tanger", TypeEntite.AGENCE, departement));
        rattachementVivierRepository.save(new RattachementVivier("Reseau Retail", VivierThematique.COMMERCIAL));

        Collaborateur chef = collaborateur("F001", "Idrissi", "Omar", agence, null, LocalDate.of(2010, 1, 1));
        Manager manager = managerRepository.save(new Manager(chef));
        complet = collaborateur("F002", "Bennani", "Sara", agence, manager, LocalDate.of(2016, 4, 1));
        Collaborateur sansPotentiel = collaborateur("F003", "Alaoui", "Karim", agence, manager,
                LocalDate.of(2022, 9, 1));

        // Notes differentes par critere : la ponderation compte. Talent et haut potentiel.
        performanceRepository.save(new Performance(complet, courant, dec("92"), dec("88"), dec("90"),
                dec("86"), dec("94")));
        potentielRepository.save(new Potentiel(complet, courant, dec("91"), dec("89"), dec("90"),
                dec("88"), dec("70"), dec("92"), dec("93")));
        performanceRepository.save(new Performance(sansPotentiel, courant, dec("70"), dec("70"), dec("70"),
                dec("70"), dec("70")));

        // Trimestres precedents ; T4 2025 plus haut : baisse de performance au trimestre courant.
        // T1 2025 est place mais sans reglages : sa case ne peut pas etre deduite.
        scoreAncien(complet, t1SansReglages, "80.00", "78.00", "Confirmé");
        scoreAncien(complet, t3, "80.00", "78.00", "Confirmé");
        scoreAncien(complet, t4, "95.00", "92.00", "Talent clé");

        questionnaire(complet, "50.00");   // sous le seuil de 60 : engagement faible

        Competence credit = competence("C001", "Credit", "Metier");
        Competence management = competence("C002", "Management", "Leadership");
        competenceCollaborateurRepository.save(skill(complet, credit, 3, 5));
        competenceCollaborateurRepository.save(skill(complet, management, 4, 4));

        Poste critique = poste("P001", "Directeur d'agence", "Oui", direction, credit, management);
        Poste nonCritique = poste("P002", "Charge d'affaires", "Non", direction, credit, management);
        successeurIdentifieRepository.save(new SuccesseurIdentifie(critique, complet));
        successeurIdentifieRepository.save(new SuccesseurIdentifie(nonCritique, complet));

        validationComiteRepository.save(new ValidationComite(complet, courant, StatutValidationComite.OUI));

        // Scores, 9-box et vivier de releve du trimestre, par le vrai moteur.
        calculTrimestreService.calculer(courant);
    }

    // ------------------------------------------------------------ fiche complete

    @Test
    void la_fiche_complete_reprend_les_valeurs_du_moteur() {
        FicheCollaborateur fiche = service.construire("F002", 2026, 1);
        Score score = scoreRepository.findByCollaborateurIdCollaborateurAndTrimestre("F002", courant).orElseThrow();

        assertThat(fiche.trimestre().libelle()).isEqualTo("T1 2026");
        assertThat(fiche.trimestre().dateReference()).isEqualTo(LocalDate.of(2026, 3, 31));

        // Identite
        FicheCollaborateur.Identite identite = fiche.identite();
        assertThat(identite.matricule()).isEqualTo("F002");
        assertThat(identite.nom()).isEqualTo("Bennani");
        assertThat(identite.prenom()).isEqualTo("Sara");
        assertThat(identite.fonction()).isEqualTo("Analyste");
        assertThat(identite.statut()).isEqualTo("ACTIF");
        assertThat(identite.entite().libelle()).isEqualTo("Tanger");
        assertThat(identite.entite().type()).isEqualTo("AGENCE");
        assertThat(identite.entite().chemin()).containsExactly("Reseau Retail", "Nord", "Tanger");
        assertThat(identite.manager().matricule()).isEqualTo("F001");
        assertThat(identite.manager().nom()).isEqualTo("Omar Idrissi");
        // 01/04/2016 -> 31/03/2026 : 3651 jours / 365.25 = 9.996 -> 10.0, a la date de reference.
        assertThat(identite.anciennete()).isEqualByComparingTo("10.0");
        assertThat(identite.anciennete()).isEqualByComparingTo(
                successionService.ancienneteEnAnnees(collaborateur("F002"), LocalDate.of(2026, 3, 31)));

        // Performance et potentiel : scores et categories enregistres, notes et poids du trimestre.
        assertThat(fiche.performance().score()).isEqualByComparingTo(score.getScorePerformance());
        assertThat(fiche.performance().categorie()).isEqualTo(score.getCategoriePerformance().name());
        assertThat(fiche.performance().criteres()).hasSize(5);
        assertThat(fiche.performance().criteres().get(0).code()).isEqualTo("OBJECTIFS");
        assertThat(fiche.performance().criteres().get(0).note()).isEqualByComparingTo("92");
        assertThat(fiche.performance().criteres().get(0).poids()).isEqualByComparingTo(
                Parametre.parDefaut(courant).getPoidsPerformance().getPoidsObjectifs());
        assertThat(fiche.performance().criteres()).extracting(FicheCollaborateur.Critere::poids)
                .allSatisfy(poids -> assertThat(poids).isNotNull());
        assertThat(fiche.potentiel().score()).isEqualByComparingTo(score.getScorePotentiel());
        assertThat(fiche.potentiel().categorie()).isEqualTo(score.getCategoriePotentiel().name());
        assertThat(fiche.potentiel().criteres()).hasSize(7);

        // 9-box : performance et potentiel eleves -> case 9.
        assertThat(fiche.neufBox().numero()).isEqualTo(9);
        assertThat(fiche.neufBox().libelle()).isEqualTo(score.getPositionBox()).isEqualTo("Talent clé");

        // Talent
        Parametre parametre = parametreRepository.findByTrimestre(courant).orElseThrow();
        assertThat(fiche.talent().estTalent()).isEqualTo(talentService.estTalent(score, parametre)).isTrue();
        assertThat(fiche.talent().estHautPotentiel()).isEqualTo(talentService.estHautPotentiel(score, parametre))
                .isTrue();
        assertThat(fiche.talent().estVivierSuccession()).isTrue();
        assertThat(fiche.talent().decisionComite()).isEqualTo("OUI");
        assertThat(fiche.talent().decisionComiteLibelle()).isEqualTo("Oui");
        List<AppartenanceVivier> enregistres = appartenanceVivierRepository.findByTrimestre(courant);
        assertThat(enregistres).isNotEmpty();
        assertThat(fiche.talent().viviers()).extracting(FicheCollaborateur.VivierFiche::code)
                .containsExactly(enregistres.get(0).getVivier().getCode(), "COMMERCIAL");
        assertThat(fiche.talent().viviers().get(1).origine()).isEqualTo("THEMATIQUE");

        // Competences : meme gap et meme statut que le service.
        List<GapCompetence> attendues = transaction.execute(statut ->
                competenceCollaborateurService.competences(collaborateur("F002"), courant));
        assertThat(fiche.competences()).hasSize(2);
        for (int i = 0; i < attendues.size(); i++) {
            FicheCollaborateur.Competence ligne = fiche.competences().get(i);
            assertThat(ligne.competenceId()).isEqualTo(attendues.get(i).competence().getCompetence().getCompetenceId());
            assertThat(ligne.gap()).isEqualTo(attendues.get(i).gap());
            assertThat(ligne.statut()).isEqualTo(attendues.get(i).statut().name());
        }
        assertThat(fiche.competences().get(0).niveauRequis()).isEqualTo(5);
        assertThat(fiche.competences().get(0).niveauActuel()).isEqualTo(3);

        assertThat(fiche.engagement()).isEqualByComparingTo("50.00");

        // Vigilance : meme indice que le service ; engagement faible et baisse de performance.
        ResultatVigilance vigilance = transaction.execute(statut ->
                vigilanceService.evaluer(collaborateur("F002"), courant));
        assertThat(fiche.vigilance().indice()).isEqualByComparingTo(vigilance.indice());
        assertThat(fiche.vigilance().niveau()).isEqualTo(vigilance.niveau().name());
        assertThat(fiche.vigilance().raisons()).extracting(FicheCollaborateur.RaisonVigilance::code)
                .containsExactly("ENGAGEMENT_FAIBLE", "BAISSE_PERFORMANCE");
        assertThat(fiche.vigilance().raisons()).extracting(FicheCollaborateur.RaisonVigilance::points)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(parametre.getPointsVigilance().getPointEngagementFaible(),
                        parametre.getPointsVigilance().getPointBaissePerformance());
        assertThat(fiche.vigilance().raisons().stream().map(FicheCollaborateur.RaisonVigilance::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(fiche.vigilance().indice());

        // Succession : le seul poste critique, avec le matching du service.
        ResultatMatching matching = transaction.execute(statut ->
                successionService.evaluer(collaborateur("F002"), "P001", courant));
        assertThat(fiche.successions()).hasSize(1);
        assertThat(fiche.successions().get(0).posteId()).isEqualTo("P001");
        assertThat(fiche.successions().get(0).direction()).isEqualTo("Reseau Retail");
        assertThat(fiche.successions().get(0).scoreMatching()).isEqualByComparingTo(matching.scoreMatching());
        assertThat(fiche.successions().get(0).readiness()).isEqualTo(matching.readiness().name());

        assertThat(fiche.autoEvaluation()).isNull();
        assertThat(fiche.donneesManquantes()).isEmpty();
    }

    @Test
    void l_historique_couvre_les_trimestres_precedents_du_plus_recent_au_plus_ancien() {
        FicheCollaborateur fiche = service.construire("F002", 2026, 1);

        assertThat(fiche.historique()).extracting(FicheCollaborateur.HistoriqueTrimestre::libelle)
                .containsExactly("T4 2025", "T3 2025", "T1 2025");
        FicheCollaborateur.HistoriqueTrimestre t4 = fiche.historique().get(0);
        assertThat(t4.scorePerformance()).isEqualByComparingTo("95.00");
        assertThat(t4.scorePotentiel()).isEqualByComparingTo("92.00");
        assertThat(t4.neufBox().numero()).isEqualTo(9);
        assertThat(t4.neufBox().libelle()).isEqualTo("Talent clé");
        assertThat(fiche.historique().get(1).neufBox().numero()).isEqualTo(5);
        assertThat(fiche.historique().get(1).neufBox().libelle()).isEqualTo("Confirmé");
        // Sans reglages, les seuils manquent : case inconnue, scores tout de meme affiches.
        assertThat(fiche.historique().get(2).neufBox()).isNull();
        assertThat(fiche.historique().get(2).scorePerformance()).isEqualByComparingTo("80.00");

        // Vue depuis T4 2025 : T3 et T1 2025 le precedent.
        assertThat(service.construire("F002", 2025, 4).historique())
                .extracting(FicheCollaborateur.HistoriqueTrimestre::libelle).containsExactly("T3 2025", "T1 2025");
    }

    @Test
    void renommer_une_case_apres_le_placement_ne_casse_ni_la_fiche_ni_l_historique() {
        Matrice9Box talentCle = matrice9BoxRepository.findByNiveauPerformanceAndNiveauPotentiel(3, 3).orElseThrow();
        String ancienLibelle = talentCle.getCategorie();
        talentCle.setCategorie("Talent strategique");
        matrice9BoxRepository.save(talentCle);
        try {
            FicheCollaborateur fiche = service.construire("F002", 2026, 1);

            // Les scores gardent l'ancien libelle ; la case se deduit des niveaux.
            assertThat(scoreRepository.findByCollaborateurIdCollaborateurAndTrimestre("F002", courant)
                    .orElseThrow().getPositionBox()).isEqualTo(ancienLibelle);
            assertThat(fiche.neufBox().numero()).isEqualTo(9);
            assertThat(fiche.neufBox().libelle()).isEqualTo("Talent strategique");
            FicheCollaborateur.HistoriqueTrimestre t4 = fiche.historique().get(0);
            assertThat(t4.libelle()).isEqualTo("T4 2025");
            assertThat(t4.neufBox().numero()).isEqualTo(9);
            assertThat(t4.neufBox().libelle()).isEqualTo("Talent strategique");
            assertThat(fiche.donneesManquantes()).isEmpty();
        } finally {
            talentCle.setCategorie(ancienLibelle);
            matrice9BoxRepository.save(talentCle);
        }
    }

    // ------------------------------------------------------------ donnees manquantes

    @Test
    void sans_potentiel_la_fiche_est_partielle_et_dit_ce_qui_manque() {
        FicheCollaborateur fiche = service.construire("F003", 2026, 1);

        assertThat(fiche.identite().matricule()).isEqualTo("F003");
        // Notes de performance presentes, mais pas de score : le moteur exige les deux evaluations.
        assertThat(fiche.performance().criteres()).hasSize(5);
        assertThat(fiche.performance().score()).isNull();
        assertThat(fiche.potentiel()).isNull();
        assertThat(fiche.neufBox()).isNull();
        assertThat(fiche.talent().estTalent()).isNull();
        assertThat(fiche.talent().decisionComite()).isNull();
        assertThat(fiche.competences()).isEmpty();
        assertThat(fiche.engagement()).isNull();
        assertThat(fiche.historique()).isEmpty();
        assertThat(fiche.successions()).isEmpty();
        assertThat(fiche.donneesManquantes()).contains(
                "Pas d'évaluation de potentiel pour ce trimestre",
                "Aucune compétence renseignée",
                "Pas de questionnaire d'engagement pour ce trimestre");
    }

    @Test
    void sans_reglages_le_trimestre_donne_une_fiche_sans_calculs_mais_sans_erreur() {
        // T1 2025 n'a pas de Parametre : case 9-box, vigilance, competences non calculables.
        FicheCollaborateur fiche = service.construire("F002", 2025, 1);

        assertThat(fiche.identite().matricule()).isEqualTo("F002");
        assertThat(fiche.neufBox()).isNull();
        assertThat(fiche.vigilance()).isNull();
        assertThat(fiche.competences()).extracting(FicheCollaborateur.Competence::statut).containsOnlyNulls();
        assertThat(fiche.donneesManquantes()).anyMatch(ligne -> ligne.startsWith("Aucun réglage pour T1 2025"));
    }

    @Test
    void un_reglage_incomplet_vide_son_bloc_sans_faire_echouer_la_fiche() {
        // Reglages ecrits hors application : points du signal "engagement faible" absents
        // (la colonne est NOT NULL : on la relache, dans la base propre a cette classe).
        // VigilanceService leve DonneesIncompletesException ; la fiche la rattrape pour ce bloc
        // seul, et sa transaction se valide (pas de marquage pour annulation).
        jdbc.execute("ALTER TABLE parametre ALTER COLUMN vig_engagement_faible SET NULL");
        Trimestre t2 = trimestre(2026, 2);
        Parametre parametre = parametreRepository.save(Parametre.parDefaut(t2));
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(complet);
        reponse.setTrimestre(t2);
        reponse.setScoreEngagement(dec("20.00"));
        questionnaireRepository.save(reponse);
        jdbc.update("UPDATE parametre SET vig_engagement_faible = NULL WHERE id_parametre = ?",
                parametre.getIdParametre());

        FicheCollaborateur fiche = service.construire("F002", 2026, 2);

        assertThat(fiche.identite().matricule()).isEqualTo("F002");
        assertThat(fiche.engagement()).isEqualByComparingTo("20.00");
        assertThat(fiche.vigilance()).isNull();
        // Les autres blocs restent calcules.
        assertThat(fiche.competences()).hasSize(2)
                .extracting(FicheCollaborateur.Competence::statut).doesNotContainNull();
        assertThat(fiche.donneesManquantes()).anyMatch(ligne -> ligne.startsWith("Vigilance : "));
    }

    @Test
    void un_matricule_ou_un_trimestre_inconnu_est_introuvable() {
        assertThatThrownBy(() -> service.construire("INCONNU", 2026, 1))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("INCONNU");
        assertThatThrownBy(() -> service.construire("F002", 2031, 2))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("T2 2031");
    }

    // ------------------------------------------------------------ outils

    private static BigDecimal dec(String valeur) {
        return new BigDecimal(valeur);
    }

    private Collaborateur collaborateur(String id) {
        return collaborateurRepository.findById(id).orElseThrow();
    }

    private Trimestre trimestre(int annee, int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(annee);
        trimestre.setNumero(numero);
        return trimestreRepository.save(trimestre);
    }

    private Collaborateur collaborateur(String id, String nom, String prenom, Entite entite, Manager manager,
                                        LocalDate entree) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom(nom);
        collaborateur.setPrenom(prenom);
        collaborateur.setFonction("Analyste");
        collaborateur.setGrade("C2");
        collaborateur.setEntite(entite);
        collaborateur.setManager(manager);
        collaborateur.setDateEntree(entree);
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        return collaborateurRepository.save(collaborateur);
    }

    private void scoreAncien(Collaborateur collaborateur, Trimestre trimestre, String performance, String potentiel,
                             String case9Box) {
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setTrimestre(trimestre);
        score.setScorePerformance(dec(performance));
        score.setScorePotentiel(dec(potentiel));
        score.setPositionBox(case9Box);
        scoreRepository.save(score);
    }

    private void questionnaire(Collaborateur collaborateur, String score) {
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(collaborateur);
        reponse.setTrimestre(courant);
        reponse.setScoreEngagement(dec(score));
        reponse.setDateReponse(LocalDate.of(2026, 2, 1));
        questionnaireRepository.save(reponse);
    }

    private Competence competence(String id, String nom, String categorie) {
        Competence competence = new Competence();
        competence.setCompetenceId(id);
        competence.setNom(nom);
        competence.setCategorie(categorie);
        return competenceRepository.save(competence);
    }

    private CompetenceCollaborateur skill(Collaborateur collaborateur, Competence competence, int actuel, int cible) {
        CompetenceCollaborateur skill = new CompetenceCollaborateur();
        skill.setCollaborateur(collaborateur);
        skill.setCompetence(competence);
        skill.setNiveauActuel(actuel);
        skill.setNiveauCible(cible);
        return skill;
    }

    private Poste poste(String id, String nom, String critique, Entite direction, Competence c1, Competence c2) {
        Poste poste = new Poste();
        poste.setPosteId(id);
        poste.setNomPoste(nom);
        poste.setPosteCritique(critique);
        poste.setCriticite("Haute");
        poste.setEntite(direction);
        poste.setCompetenceRequise1(c1);
        poste.setNiveau1(4);
        poste.setCompetenceRequise2(c2);
        poste.setNiveau2(3);
        return posteRepository.save(poste);
    }
}
