package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Sexe;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.repository.DeclarationVigilanceRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.BilanFeuille;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.talent360bank.talent360bank.entity.SourceEvaluation.MANAGER;
import static com.talent360bank.talent360bank.service.ClasseurDeTest.*;
import static com.talent360bank.talent360bank.service.ImportClasseurService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Import du classeur sur une vraie base, a partir d'un classeur genere
 * ({@link ClasseurDeTest}).
 *
 * <p>Sans transaction de test : l'import ecrit dans sa propre transaction et
 * journalise dans une autre, comme en production ; un rollback de test
 * masquerait justement ce qui est verifie ici (journal d'un import annule,
 * mises a jour d'un re-import). Les lignes creees sont supprimees apres
 * chaque test : la base en memoire est partagee avec d'autres classes.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
@Import({ImportService.class, com.talent360bank.talent360bank.securite.UtilisateurCourant.class, ImportClasseurService.class, TrimestreService.class,
        SuccesseursIdentifiesEnBase.class, ValidationsComiteEnBase.class, ViviersThematiquesEnBase.class,
        FaitsVigilanceEnBase.class})
class ImportServiceJpaTest {

    /** Hors des annees des autres tests, qui en laissent parfois en base. */
    private static final int ANNEE = 2090;
    private static final int NUMERO = 3;

    @Autowired
    private ImportService importService;
    @Autowired
    private SuccesseursIdentifiesEnBase successeursEnBase;
    @Autowired
    private ValidationsComiteEnBase validationsEnBase;
    @Autowired
    private ViviersThematiquesEnBase viviersEnBase;
    @Autowired
    private FaitsVigilanceEnBase faitsEnBase;

    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private CompetenceCollaborateurRepository skillRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PotentielRepository potentielRepository;
    @Autowired
    private SuccesseurIdentifieRepository successeurRepository;
    @Autowired
    private ValidationComiteRepository validationRepository;
    @Autowired
    private RattachementVivierRepository rattachementRepository;
    @Autowired
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private DeclarationVigilanceRepository declarationRepository;
    @Autowired
    private com.talent360bank.talent360bank.repository.UtilisateurRepository utilisateurRepository;
    @Autowired
    private ImportExcelRepository importExcelRepository;
    @Autowired
    private EntiteRepository entiteRepository;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private <T> T enTransaction(Supplier<T> lecture) {
        return new TransactionTemplate(transactionManager).execute(statut -> lecture.get());
    }

    private ResultatImport importer(ClasseurDeTest classeur) {
        return importService.importer(classeur.fichier(), ANNEE, NUMERO);
    }

    private Trimestre trimestre() {
        return trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE).orElseThrow();
    }

    private static BilanFeuille bilan(ResultatImport resultat, String feuille) {
        return resultat.feuilles().stream().filter(b -> b.feuille().equals(feuille)).findFirst().orElseThrow();
    }

    @AfterEach
    void nettoyer() {
        new TransactionTemplate(transactionManager).executeWithoutResult(statut -> {
            String collaborateurs = "(select e from Collaborateur e where e.idCollaborateur like 'TST%')";
            entityManager.createQuery("delete from ImportExcel").executeUpdate();
            entityManager.createQuery("delete from RattachementVivier").executeUpdate();
            for (String entite : List.of("DeclarationVigilance", "QuestionnaireEngagement", "ValidationComite",
                    "Performance", "Potentiel", "SuccesseurIdentifie", "CompetenceCollaborateur", "Score")) {
                entityManager.createQuery("delete from " + entite + " x where x.collaborateur in " + collaborateurs)
                        .executeUpdate();
            }
            entityManager.createQuery("delete from Poste p where p.posteId like 'TST%'").executeUpdate();
            entityManager.createQuery("delete from Competence c where c.competenceId like 'TST%'").executeUpdate();
            entityManager.createQuery(
                    "update Collaborateur e set e.manager = null where e.idCollaborateur like 'TST%'").executeUpdate();
            entityManager.createQuery("delete from Manager m where m.collaborateur in " + collaborateurs)
                    .executeUpdate();
            entityManager.createQuery("delete from Collaborateur e where e.idCollaborateur like 'TST%'")
                    .executeUpdate();
            // Entites devenues orphelines, des agences vers les directions (cle parent).
            for (TypeEntite niveau : List.of(TypeEntite.AGENCE, TypeEntite.REGION, TypeEntite.DEPARTEMENT,
                    TypeEntite.DIRECTION)) {
                entityManager.createQuery("delete from Entite e where e.type = :niveau"
                                + " and not exists (select c from Collaborateur c where c.entite = e)"
                                + " and not exists (select p from Poste p where p.entite = e)"
                                + " and not exists (select m from Manager m where m.entiteGeree = e)"
                                + " and not exists (select f from Entite f where f.parent = e)")
                        .setParameter("niveau", niveau).executeUpdate();
            }
            entityManager.createQuery("delete from Parametre p where p.trimestre in "
                    + "(select t from Trimestre t where t.annee = " + ANNEE + ")").executeUpdate();
            entityManager.createQuery("delete from Trimestre t where t.annee = " + ANNEE).executeUpdate();
        });
    }

    // ------------------------------------------------------------------ import complet

    @Test
    void un_classeur_complet_est_importe_feuille_par_feuille() {
        ResultatImport resultat = importer(ClasseurDeTest.complet());

        assertThat(resultat.statut()).isEqualTo(StatutImport.SUCCES);
        assertThat(resultat.erreurs()).isEmpty();
        assertThat(resultat.feuilles()).extracting(BilanFeuille::feuille, BilanFeuille::nbLignes)
                .containsExactly(
                        tuple(FEUILLE_COLLABORATEURS, 3), tuple(FEUILLE_COMPETENCES, 2), tuple(FEUILLE_SKILLS, 3),
                        tuple(FEUILLE_POSTES, 2), tuple(FEUILLE_POSTES_CRITIQUES, 1), tuple(FEUILLE_PERFORMANCE, 3),
                        tuple(FEUILLE_POTENTIEL, 3), tuple(FEUILLE_SUCCESSION, 2), tuple(FEUILLE_TALENTS, 3),
                        tuple(FEUILLE_VIGILANCE, 3));
        assertThat(resultat.nbLignes()).isEqualTo(25);
    }

    @Test
    void les_collaborateurs_portent_sexe_naissance_statut_et_manager() {
        importer(ClasseurDeTest.complet());

        enTransaction(() -> {
            Collaborateur siham = collaborateurRepository.findById(E1).orElseThrow();
            assertThat(siham.getSexe()).isEqualTo(Sexe.FEMME);
            assertThat(siham.getDateNaissance()).isEqualTo(LocalDate.of(1990, 5, 12));
            assertThat(siham.getDateEntree()).isEqualTo(LocalDate.of(2015, 3, 1));
            assertThat(siham.getDirection()).isEqualTo("Reseau Retail");
            assertThat(siham.getGrade()).isEqualTo("Confirme");
            assertThat(siham.getStatut()).isEqualTo(StatutCollaborateur.ACTIF);
            // Manager declare plus bas dans la feuille : pose a la seconde passe.
            assertThat(siham.getManager().getIdCollaborateur()).isEqualTo(E3);

            Collaborateur omar = collaborateurRepository.findById(E2).orElseThrow();
            assertThat(omar.getSexe()).isEqualTo(Sexe.HOMME);
            assertThat(omar.getStatut()).isEqualTo(StatutCollaborateur.INACTIF);

            assertThat(collaborateurRepository.findById(E3).orElseThrow().getManager()).isNull();
            return null;
        });
    }

    @Test
    void competences_postes_et_notes_sont_en_base() {
        importer(ClasseurDeTest.complet());
        Trimestre trimestre = trimestre();

        enTransaction(() -> {
            List<CompetenceCollaborateur> skills =
                    skillRepository.findByCollaborateurIdsAvecCompetence(List.of(E1, E2, E3));
            assertThat(skills).extracting(s -> s.getCollaborateur().getIdCollaborateur(),
                            s -> s.getCompetence().getNom(), CompetenceCollaborateur::getNiveauActuel,
                            CompetenceCollaborateur::getNiveauCible, CompetenceCollaborateur::getGap)
                    .containsExactlyInAnyOrder(
                            tuple(E1, COMPETENCE_1, 3, 4, 1),
                            tuple(E1, COMPETENCE_2, 2, 4, 2),
                            tuple(E2, COMPETENCE_1, 5, 4, -1));

            Poste directeur = posteRepository.findByIdAvecCompetences(P1).orElseThrow();
            assertThat(directeur.getPosteCritique()).isEqualTo("Oui");
            assertThat(directeur.getCompetenceRequise1().getCompetenceId()).isEqualTo(C2);
            assertThat(directeur.getNiveau1()).isEqualTo(5);
            assertThat(directeur.getCompetenceRequise3()).isNull();
            assertThat(directeur.getTitulaireId()).isEqualTo(E3);
            assertThat(directeur.getTitulaireNom()).isEqualTo("Mounir Tahiri");

            Performance performance = performanceRepository
                    .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E3, trimestre, MANAGER).orElseThrow();
            assertThat(performance.getNoteObjectifs()).isEqualByComparingTo("70.5");
            assertThat(performance.getNoteDeveloppement()).isEqualByComparingTo("69");

            Potentiel potentiel = potentielRepository
                    .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre, MANAGER)
                    .orElseThrow();
            assertThat(potentiel.getNoteLearning()).isEqualByComparingTo("90");
            assertThat(potentiel.getNoteAutonomie()).isEqualByComparingTo("83");
            return null;
        });
    }

    @Test
    void le_trimestre_est_ouvert_avec_ses_reglages_par_defaut() {
        importer(ClasseurDeTest.complet());

        assertThat(parametreRepository.existsByTrimestre(trimestre())).isTrue();
    }

    @Test
    void l_import_est_journalise() {
        ResultatImport resultat = importer(ClasseurDeTest.complet());

        ImportExcel journal = enTransaction(() -> {
            ImportExcel entree = importExcelRepository.findAllRecentsDabord().get(0);
            entree.getTrimestre().getAnnee();
            return entree;
        });
        assertThat(journal.getIdImport()).isEqualTo(resultat.idImport());
        assertThat(journal.getSource()).isEqualTo(ImportService.SOURCE_CLASSEUR);
        assertThat(journal.getNomFichier()).isEqualTo("classeur-test.xlsx");
        assertThat(journal.getDateImport()).isEqualTo(LocalDate.now());
        assertThat(journal.getStatut()).isEqualTo("SUCCES");
        assertThat(journal.getNbLignes()).isEqualTo(25);
        assertThat(journal.getNbErreurs()).isZero();
        assertThat(journal.getTrimestre().getAnnee()).isEqualTo(ANNEE);
        assertThat(journal.getUtilisateur()).isNull();
    }

    @Test
    void l_import_garde_le_compte_rh_connecte() {
        utilisateurRepository.save(new com.talent360bank.talent360bank.entity.Utilisateur("rh.import", "x",
                com.talent360bank.talent360bank.entity.Role.RH));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("rh.import", null,
                        java.util.List.of()));
        try {
            importer(ClasseurDeTest.complet());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        // Le journal lu comme l'ecran Import le lit (utilisateur charge avec la ligne).
        ImportExcel journal = importExcelRepository.findAllRecentsDabord().get(0);
        assertThat(journal.getUtilisateur()).isNotNull();
        assertThat(journal.getUtilisateur().getLogin()).isEqualTo("rh.import");
    }

    // ------------------------------------------------------------------ sources des saisies RH

    @Test
    void les_sources_en_base_rendent_les_saisies_importees() {
        importer(ClasseurDeTest.complet());
        Trimestre trimestre = trimestre();

        assertThat(successeursEnBase.successeursIdentifies(P1)).containsExactlyInAnyOrder(E1, E2);
        assertThat(successeursEnBase.successeursIdentifies(P2)).isEmpty();

        assertThat(validationsEnBase.statut(E1, trimestre)).isEqualTo(StatutValidationComite.OUI);
        assertThat(validationsEnBase.statut(E2, trimestre)).isEqualTo(StatutValidationComite.NON);
        assertThat(validationsEnBase.statut(E3, trimestre)).isEqualTo(StatutValidationComite.EN_ATTENTE);

        assertThat(viviersEnBase.vivierPourDirection("Reseau Retail")).contains(VivierThematique.COMMERCIAL);
        assertThat(viviersEnBase.vivierPourDirection("Risques")).contains(VivierThematique.RISQUES);
        assertThat(viviersEnBase.vivierPourDirection("Inconnue")).isEmpty();

        Map<String, FaitsVigilance> faits = faitsEnBase.faitsDuTrimestre(trimestre);
        assertThat(faits.get(E1).declare(SignalVigilance.SANS_MOBILITE_4_ANS)).isTrue();
        assertThat(faits.get(E1).declare(SignalVigilance.MOBILITE_NON_TRAITEE)).isFalse();
        assertThat(faits.get(E1).declare(SignalVigilance.SANS_DEVELOPPEMENT_RECENT)).isTrue();
        assertThat(faits.get(E1).declare(SignalVigilance.FORMATION_NON_FAITE)).isTrue();
        assertThat(faits.get(E2).declare(SignalVigilance.MOBILITE_NON_TRAITEE)).isTrue();
        // Cellules vides : inconnu, pas Non.
        assertThat(faits.get(E3).declare(SignalVigilance.SANS_MOBILITE_4_ANS)).isNull();
    }

    @Test
    void l_engagement_devient_un_questionnaire_du_trimestre() {
        importer(ClasseurDeTest.complet());

        List<QuestionnaireEngagement> questionnaires =
                questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre());
        assertThat(questionnaires).extracting(q -> q.getCollaborateur().getIdCollaborateur(),
                        q -> q.getScoreEngagement().intValueExact())
                .containsExactlyInAnyOrder(tuple(E1, 76), tuple(E2, 54));
    }

    // ------------------------------------------------------------------ organigramme et managers

    @Test
    void l_organigramme_est_construit_depuis_les_colonnes_h_a_k() {
        importer(ClasseurDeTest.complet());

        enTransaction(() -> {
            Entite agence = collaborateurRepository.findById(E1).orElseThrow().getEntite();
            assertThat(agence.getType()).isEqualTo(TypeEntite.AGENCE);
            assertThat(agence.getLibelle()).isEqualTo("Agence Tanger Nord");
            Entite region = agence.getParent();
            assertThat(region.getType()).isEqualTo(TypeEntite.REGION);
            assertThat(region.getLibelle()).isEqualTo("Tanger");
            Entite departement = region.getParent();
            assertThat(departement.getType()).isEqualTo(TypeEntite.DEPARTEMENT);
            assertThat(departement.getLibelle()).isEqualTo("Reseau Retail - Nord");
            Entite direction = departement.getParent();
            assertThat(direction.getType()).isEqualTo(TypeEntite.DIRECTION);
            assertThat(direction.getLibelle()).isEqualTo("Reseau Retail");
            assertThat(direction.getParent()).isNull();
            assertThat(agence.getCode())
                    .isEqualTo("DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_NORD/REG:TANGER/AGE:AGENCE_TANGER_NORD");

            // Les accesseurs historiques remontent l'arbre.
            Collaborateur siham = collaborateurRepository.findById(E1).orElseThrow();
            assertThat(siham.getDirection()).isEqualTo("Reseau Retail");
            assertThat(siham.getDepartement()).isEqualTo("Reseau Retail - Nord");
            assertThat(siham.getRegion()).isEqualTo("Tanger");
            assertThat(siham.getAgence()).isEqualTo("Agence Tanger Nord");
            return null;
        });
    }

    @Test
    void une_entite_partagee_n_est_creee_qu_une_fois() {
        importer(ClasseurDeTest.complet());

        enTransaction(() -> {
            // E1 et E3 : meme direction, departement et region, agences differentes.
            Entite agenceE1 = collaborateurRepository.findById(E1).orElseThrow().getEntite();
            Entite agenceE3 = collaborateurRepository.findById(E3).orElseThrow().getEntite();
            assertThat(agenceE3.getIdEntite()).isNotEqualTo(agenceE1.getIdEntite());
            assertThat(agenceE3.getParent().getIdEntite()).isEqualTo(agenceE1.getParent().getIdEntite());

            // Les postes (07 C) se rattachent a la direction des collaborateurs.
            Entite reseauRetail = agenceE1.ancetre(TypeEntite.DIRECTION);
            assertThat(posteRepository.findById(P1).orElseThrow().getEntite().getIdEntite())
                    .isEqualTo(reseauRetail.getIdEntite());
            assertThat(posteRepository.findById(P2).orElseThrow().getDirection()).isEqualTo("Risques");

            // 2 directions, 2 departements, 2 regions, 3 agences.
            assertThat(entitesDuClasseur()).extracting(Entite::getType)
                    .containsExactlyInAnyOrder(TypeEntite.DIRECTION, TypeEntite.DIRECTION,
                            TypeEntite.DEPARTEMENT, TypeEntite.DEPARTEMENT, TypeEntite.REGION, TypeEntite.REGION,
                            TypeEntite.AGENCE, TypeEntite.AGENCE, TypeEntite.AGENCE);
            return null;
        });
    }

    @Test
    void une_meme_region_sous_deux_departements_donne_deux_entites() {
        // E2 passe en region Tanger, sous le departement Risques - Credit.
        importer(ClasseurDeTest.complet().cellule(FEUILLE_COLLABORATEURS, 1, 9, "Tanger"));

        enTransaction(() -> {
            assertThat(entitesDuClasseur()).filteredOn(e -> e.getLibelle().equals("Tanger"))
                    .extracting(e -> e.getParent().getLibelle())
                    .containsExactlyInAnyOrder("Reseau Retail - Nord", "Risques - Credit");
            return null;
        });
    }

    @Test
    void une_cellule_vide_saute_son_niveau() {
        // E2 sans departement : sa region se rattache directement a la direction.
        importer(ClasseurDeTest.complet().cellule(FEUILLE_COLLABORATEURS, 1, 8, null));

        enTransaction(() -> {
            Collaborateur omar = collaborateurRepository.findById(E2).orElseThrow();
            assertThat(omar.getDepartement()).isNull();
            assertThat(omar.getEntite().getParent().getType()).isEqualTo(TypeEntite.REGION);
            assertThat(omar.getEntite().getParent().getParent().getType()).isEqualTo(TypeEntite.DIRECTION);
            assertThat(omar.getDirection()).isEqualTo("Risques");
            return null;
        });
    }

    @Test
    void un_libelle_trop_long_rejette_la_ligne_sans_creer_d_entite() {
        ResultatImport resultat = importer(ClasseurDeTest.complet()
                .cellule(FEUILLE_COLLABORATEURS, 1, 10, "A".repeat(Entite.LONGUEUR_LIBELLE + 1)));

        assertThat(message(resultat, FEUILLE_COLLABORATEURS)).contains("colonne K", "agence");
        assertThat(collaborateurRepository.findById(E2)).isEmpty();
        // Seule la direction Risques existe, creee par le poste P2 (07_POSTES C).
        enTransaction(() -> {
            assertThat(entitesDuClasseur()).filteredOn(e -> e.getCode().startsWith("DIR:RISQUES"))
                    .extracting(Entite::getCode).containsExactly("DIR:RISQUES");
            return null;
        });
    }

    @Test
    void chaque_manager_id_distinct_devient_un_manager_lie_a_son_collaborateur() {
        importer(ClasseurDeTest.complet());

        enTransaction(() -> {
            // E3 manage E1 et E2 : un seul Manager, partage.
            List<Manager> managers = managersDuClasseur();
            assertThat(managers).singleElement().extracting(Manager::getIdCollaborateur).isEqualTo(E3);
            Manager mounir = managers.get(0);
            assertThat(mounir.getEntiteGeree()).isNull();

            assertThat(collaborateurRepository.findById(E1).orElseThrow().getManager().getIdManager())
                    .isEqualTo(mounir.getIdManager());
            assertThat(collaborateurRepository.findById(E2).orElseThrow().getManager().getIdManager())
                    .isEqualTo(mounir.getIdManager());
            assertThat(collaborateurRepository.findById(E3).orElseThrow().getManager()).isNull();
            return null;
        });
    }

    @Test
    void la_performance_est_rattachee_au_manager_du_collaborateur() {
        importer(ClasseurDeTest.complet());
        Trimestre trimestre = trimestre();

        enTransaction(() -> {
            Manager evaluateur = performanceRepository
                    .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre, MANAGER)
                    .orElseThrow().getEvaluateur();
            assertThat(evaluateur.getIdCollaborateur()).isEqualTo(E3);
            // Sans manager (E3), pas d'evaluateur connu.
            assertThat(performanceRepository
                    .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E3, trimestre, MANAGER)
                    .orElseThrow().getEvaluateur()).isNull();
            return null;
        });
    }

    @Test
    void reimporter_ne_double_ni_les_entites_ni_les_managers() {
        importer(ClasseurDeTest.complet());
        List<String> entites = enTransaction(() -> entitesDuClasseur().stream().map(Entite::getCode).toList());
        Integer idManager = enTransaction(() -> managersDuClasseur().get(0).getIdManager());

        importer(ClasseurDeTest.complet());

        enTransaction(() -> {
            assertThat(entitesDuClasseur()).extracting(Entite::getCode)
                    .containsExactlyInAnyOrderElementsOf(entites);
            assertThat(managersDuClasseur()).extracting(Manager::getIdManager).containsExactly(idManager);
            assertThat(collaborateurRepository.findById(E1).orElseThrow().getManager().getIdManager())
                    .isEqualTo(idManager);
            return null;
        });
    }

    /** Entites des directions du classeur de test. */
    private List<Entite> entitesDuClasseur() {
        return entiteRepository.findAll().stream()
                .filter(e -> e.getCode().startsWith("DIR:RESEAU_RETAIL") || e.getCode().startsWith("DIR:RISQUES"))
                .toList();
    }

    private List<Manager> managersDuClasseur() {
        return managerRepository.findAll().stream()
                .filter(m -> m.getIdCollaborateur().startsWith("TST"))
                .toList();
    }

    // ------------------------------------------------------------------ erreurs

    @Test
    void une_ligne_invalide_est_rapportee_avec_sa_feuille_et_sa_ligne_et_les_autres_sont_importees() {
        ClasseurDeTest classeur = ClasseurDeTest.complet()
                .cellule(FEUILLE_PERFORMANCE, 1, 3, 120)                  // E2 : objectifs sur 100
                .cellule(FEUILLE_POTENTIEL, 0, 5, "beaucoup")             // E1 : pas un nombre
                .cellule(FEUILLE_SKILLS, 0, 5, 7)                         // niveau hors 1-5
                .cellule(FEUILLE_TALENTS, 2, 6, "Peut-etre")
                .cellule(FEUILLE_VIGILANCE, 1, 6, "Parfois")
                .ligne(FEUILLE_VIGILANCE, "TST999", "Inconnu", "RH", 60);

        ResultatImport resultat = importer(classeur);

        assertThat(resultat.statut()).isEqualTo(StatutImport.PARTIEL);
        assertThat(resultat.erreurs()).extracting(ErreurImport::feuille, ErreurImport::ligne)
                .containsExactlyInAnyOrder(
                        tuple(FEUILLE_SKILLS, ligneExcel(FEUILLE_SKILLS, 0)),
                        tuple(FEUILLE_PERFORMANCE, ligneExcel(FEUILLE_PERFORMANCE, 1)),
                        tuple(FEUILLE_POTENTIEL, ligneExcel(FEUILLE_POTENTIEL, 0)),
                        tuple(FEUILLE_TALENTS, ligneExcel(FEUILLE_TALENTS, 2)),
                        tuple(FEUILLE_VIGILANCE, ligneExcel(FEUILLE_VIGILANCE, 1)),
                        tuple(FEUILLE_VIGILANCE, ligneExcel(FEUILLE_VIGILANCE, 3)));
        assertThat(message(resultat, FEUILLE_PERFORMANCE)).contains("colonne D", "100");
        assertThat(message(resultat, FEUILLE_POTENTIEL)).contains("colonne F", "beaucoup");
        assertThat(message(resultat, FEUILLE_SKILLS)).contains("colonne F", "1 a 5");
        assertThat(message(resultat, FEUILLE_TALENTS)).contains("colonne G", "Peut-etre");
        assertThat(resultat.erreurs()).filteredOn(e -> e.feuille().equals(FEUILLE_VIGILANCE))
                .extracting(ErreurImport::message)
                .anySatisfy(m -> assertThat(m).contains("TST999", "inconnu"));

        assertThat(bilan(resultat, FEUILLE_PERFORMANCE).nbLignes()).isEqualTo(2);
        assertThat(bilan(resultat, FEUILLE_PERFORMANCE).nbErreurs()).isEqualTo(1);
        // La ligne rejetee n'a rien ecrit ; les autres sont en base.
        Trimestre trimestre = trimestre();
        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E2, trimestre, MANAGER)).isEmpty();
        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre, MANAGER)).isPresent();
        assertThat(declarationRepository.findByTrimestreAvecCollaborateur(trimestre))
                .extracting(d -> d.getCollaborateur().getIdCollaborateur()).containsExactlyInAnyOrder(E1, E3);
        assertThat(importExcelRepository.findAllRecentsDabord().get(0).getStatut()).isEqualTo("PARTIEL");
        assertThat(importExcelRepository.findAllRecentsDabord().get(0).getNbErreurs()).isEqualTo(6);
    }

    @Test
    void un_manager_inconnu_est_signale_et_le_collaborateur_importe_sans_manager() {
        ResultatImport resultat = importer(ClasseurDeTest.complet().cellule(FEUILLE_COLLABORATEURS, 1, 13, "TST404"));

        assertThat(resultat.erreurs()).extracting(ErreurImport::feuille, ErreurImport::ligne)
                .containsExactly(tuple(FEUILLE_COLLABORATEURS, ligneExcel(FEUILLE_COLLABORATEURS, 1)));
        assertThat(resultat.erreurs().get(0).message()).contains("colonne N", "TST404");
        enTransaction(() -> {
            assertThat(collaborateurRepository.findById(E2).orElseThrow().getManager()).isNull();
            return null;
        });
    }

    @Test
    void un_collaborateur_invalide_est_ecarte_et_ses_lignes_des_autres_feuilles_aussi() {
        ResultatImport resultat = importer(ClasseurDeTest.complet()
                .cellule(FEUILLE_COLLABORATEURS, 1, 5, null)     // date d'entree obligatoire
                .cellule(FEUILLE_COLLABORATEURS, 0, 3, "X"));    // sexe inconnu

        assertThat(resultat.erreurs()).filteredOn(e -> e.feuille().equals(FEUILLE_COLLABORATEURS))
                .extracting(ErreurImport::message)
                .anySatisfy(m -> assertThat(m).contains("colonne F", "dateEntree"))
                .anySatisfy(m -> assertThat(m).contains("colonne D", "\"X\""));
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
        assertThat(collaborateurRepository.findById(E2)).isEmpty();
        assertThat(bilan(resultat, FEUILLE_PERFORMANCE).nbErreurs()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ format et periode

    @Test
    void une_feuille_absente_refuse_tout_le_classeur_avant_toute_ecriture() {
        ResultatImport resultat = importer(ClasseurDeTest.complet().sans(FEUILLE_POTENTIEL));

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.erreurs()).containsExactly(
                new ErreurImport(FEUILLE_POTENTIEL, null, "Feuille absente du classeur"));
        assertThat(resultat.message()).contains("Format du classeur non conforme");
        // Rien n'est ecrit, pas meme le trimestre ; le refus est journalise.
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
        assertThat(trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE)).isEmpty();
        assertThat(importExcelRepository.findAllRecentsDabord().get(0).getStatut()).isEqualTo("ECHEC");
    }

    @Test
    void un_en_tete_introuvable_refuse_le_classeur() {
        ResultatImport resultat = importer(ClasseurDeTest.complet().enteteModifie(FEUILLE_TALENTS, "Matricule"));

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.erreurs()).singleElement().satisfies(erreur -> {
            assertThat(erreur.feuille()).isEqualTo(FEUILLE_TALENTS);
            assertThat(erreur.ligne()).isNull();
            assertThat(erreur.message()).contains("Employee_ID");
        });
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
    }

    @Test
    void chaque_colonne_renommee_ou_deplacee_et_feuille_renommee_est_listee() {
        ResultatImport resultat = importer(ClasseurDeTest.complet()
                .enteteColonne(FEUILLE_COLLABORATEURS, 7, "Direction RH")            // H renommee
                .enteteColonne(FEUILLE_PERFORMANCE, 3, "Competences (/100)")         // D <-> E echangees
                .enteteColonne(FEUILLE_PERFORMANCE, 4, "Objectifs (/100)")
                .sans(FEUILLE_VIGILANCE).autreFeuille("12_Vigilance RH"));

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.erreurs()).extracting(ErreurImport::feuille, ErreurImport::message)
                .containsExactlyInAnyOrder(
                        tuple(FEUILLE_COLLABORATEURS,
                                "colonne H : en-tête \"Direction\" attendu, trouvé \"Direction RH\""),
                        tuple(FEUILLE_PERFORMANCE, "colonne D : en-tête \"Objectifs (/100)\" attendu, "
                                + "trouvé \"Competences (/100)\" (colonne déplacée en E)"),
                        tuple(FEUILLE_PERFORMANCE, "colonne E : en-tête \"Competences (/100)\" attendu, "
                                + "trouvé \"Objectifs (/100)\" (colonne déplacée en D)"),
                        tuple(FEUILLE_VIGILANCE, "Feuille absente du classeur (renommée en \"12_Vigilance RH\" ?)"));
        // Ligne de l'en-tete, numerotee comme dans Excel.
        assertThat(resultat.erreurs()).filteredOn(e -> e.feuille().equals(FEUILLE_COLLABORATEURS))
                .extracting(ErreurImport::ligne).containsExactly(4);
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
    }

    @Test
    void les_en_tetes_sont_compares_sans_casse_ni_accents() {
        ResultatImport resultat = importer(ClasseurDeTest.complet()
                .enteteColonne(FEUILLE_COLLABORATEURS, 8, "Département")
                .enteteColonne(FEUILLE_POSTES, 15, "POSTE CRITIQUE ?"));

        assertThat(resultat.statut()).isEqualTo(StatutImport.SUCCES);
    }

    @Test
    void un_classeur_qui_annonce_une_autre_periode_est_refuse() {
        ResultatImport resultat = importer(ClasseurDeTest.complet()
                .autreFeuille("00_DASHBOARD").commentaire("00_DASHBOARD", "Tableau de bord - au 15/09/2026"));

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.message()).contains("T3 2026", "00_DASHBOARD A2", "15/09/2026",
                "T" + NUMERO + " " + ANNEE);
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
    }

    @Test
    void la_periode_du_nom_de_fichier_doit_etre_celle_du_trimestre() {
        ResultatImport refuse = importService.importer(
                ClasseurDeTest.complet().fichier("campagne_T4_" + ANNEE + ".xlsx"), ANNEE, NUMERO);
        assertThat(refuse.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(refuse.message()).contains("T4 " + ANNEE, "nom du fichier");

        ResultatImport accepte = importService.importer(
                ClasseurDeTest.complet().fichier(ANNEE + "-T" + NUMERO + " campagne.xlsx"), ANNEE, NUMERO);
        assertThat(accepte.statut()).isEqualTo(StatutImport.SUCCES);
    }

    // ------------------------------------------------------------------ nouvelle campagne, fichier corrige

    @Test
    void un_collaborateur_absent_de_la_campagne_passe_inactif_puis_revient_actif() {
        importer(ClasseurDeTest.complet());

        ResultatImport sansSiham = importer(ClasseurDeTest.complet().sansCollaborateur(E1));

        assertThat(sansSiham.statut()).isEqualTo(StatutImport.SUCCES);
        assertThat(sansSiham.desactives()).containsExactly(E1);
        Collaborateur siham = collaborateurRepository.findById(E1).orElseThrow();
        assertThat(siham.getStatut()).isEqualTo(StatutCollaborateur.INACTIF);
        // Ses saisies du trimestre suivent le fichier : retirees.
        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre(), MANAGER)).isEmpty();
        assertThat(bilan(sansSiham, FEUILLE_PERFORMANCE).nbRetirees()).isEqualTo(1);
        assertThat(bilan(sansSiham, FEUILLE_POTENTIEL).nbRetirees()).isEqualTo(1);

        ResultatImport retour = importer(ClasseurDeTest.complet());

        assertThat(retour.desactives()).isEmpty();
        assertThat(retour.reactives()).containsExactly(E1);
        assertThat(collaborateurRepository.findById(E1).orElseThrow().getStatut())
                .isEqualTo(StatutCollaborateur.ACTIF);
    }

    @Test
    void une_ligne_01_rejetee_ne_desactive_pas_son_collaborateur() {
        importer(ClasseurDeTest.complet());

        ResultatImport resultat = importer(ClasseurDeTest.complet().cellule(FEUILLE_COLLABORATEURS, 0, 3, "X"));

        assertThat(resultat.desactives()).isEmpty();
        assertThat(collaborateurRepository.findById(E1).orElseThrow().getStatut())
                .isEqualTo(StatutCollaborateur.ACTIF);
    }

    @Test
    void un_fichier_corrige_retire_les_saisies_du_trimestre_qu_il_ne_porte_plus() {
        importer(ClasseurDeTest.complet());

        // Le fichier corrige ne note plus E2 en performance ni en vigilance.
        ResultatImport corrige = importer(ClasseurDeTest.complet()
                .retirerLigne(FEUILLE_PERFORMANCE, 1).retirerLigne(FEUILLE_VIGILANCE, 1));

        Trimestre trimestre = trimestre();
        assertThat(corrige.desactives()).isEmpty();
        assertThat(performanceRepository.findByTrimestreAvecCollaborateur(trimestre, MANAGER))
                .extracting(p -> p.getCollaborateur().getIdCollaborateur()).containsExactlyInAnyOrder(E1, E3);
        assertThat(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre))
                .extracting(q -> q.getCollaborateur().getIdCollaborateur()).containsExactly(E1);
        assertThat(declarationRepository.findByTrimestreAvecCollaborateur(trimestre))
                .extracting(d -> d.getCollaborateur().getIdCollaborateur()).containsExactlyInAnyOrder(E1, E3);
        // Question + declaration pour E2 en 12.
        assertThat(bilan(corrige, FEUILLE_VIGILANCE).nbRetirees()).isEqualTo(2);
    }

    @Test
    void avec_une_ligne_rejetee_la_feuille_ne_retire_rien() {
        importer(ClasseurDeTest.complet());

        ResultatImport corrige = importer(ClasseurDeTest.complet()
                .retirerLigne(FEUILLE_PERFORMANCE, 1).cellule(FEUILLE_PERFORMANCE, 0, 3, 150));

        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E2, trimestre(), MANAGER))
                .isPresent();
        assertThat(corrige.erreurs()).filteredOn(e -> e.feuille().equals(FEUILLE_PERFORMANCE))
                .extracting(ErreurImport::message)
                .anySatisfy(m -> assertThat(m).contains("1 ligne(s) du trimestre absente(s)"));
    }

    // ------------------------------------------------------------------ simulation

    @Test
    void une_simulation_valide_et_compte_sans_rien_enregistrer() {
        ResultatImport resultat = importService.importer(ClasseurDeTest.complet().fichier(), ANNEE, NUMERO, true);

        assertThat(resultat.simulation()).isTrue();
        assertThat(resultat.statut()).isEqualTo(StatutImport.SUCCES);
        assertThat(resultat.nbLignes()).isEqualTo(25);
        assertThat(resultat.idImport()).isNull();
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
        assertThat(entiteRepository.findAll()).filteredOn(e -> e.getCode().startsWith("DIR:RISQUES")).isEmpty();
        assertThat(trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE)).isEmpty();
        assertThat(importExcelRepository.findAllRecentsDabord()).isEmpty();
    }

    @Test
    void une_simulation_annonce_les_desactivations_sans_les_faire() {
        importer(ClasseurDeTest.complet());

        ResultatImport resultat = importService.importer(
                ClasseurDeTest.complet().sansCollaborateur(E1).fichier(), ANNEE, NUMERO, true);

        assertThat(resultat.desactives()).containsExactly(E1);
        assertThat(collaborateurRepository.findById(E1).orElseThrow().getStatut())
                .isEqualTo(StatutCollaborateur.ACTIF);
        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre(), MANAGER))
                .isPresent();
        assertThat(importExcelRepository.findAllRecentsDabord()).hasSize(1);
    }

    @Test
    void une_simulation_refuse_un_format_non_conforme_comme_un_import() {
        ResultatImport resultat = importService.importer(
                ClasseurDeTest.complet().sans(FEUILLE_TALENTS).fichier(), ANNEE, NUMERO, true);

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.erreurs()).extracting(ErreurImport::feuille).containsExactly(FEUILLE_TALENTS);
        assertThat(importExcelRepository.findAllRecentsDabord()).isEmpty();
    }

    @Test
    void un_fichier_illisible_echoue_sans_rien_ecrire_et_laisse_une_trace() {
        MockMultipartFile texte = new MockMultipartFile("fichier", "notes.txt", "text/plain",
                "ceci n'est pas un classeur".getBytes());

        ResultatImport resultat = importService.importer(texte, ANNEE, NUMERO);

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.message()).startsWith("Fichier illisible");
        assertThat(collaborateurRepository.findById(E1)).isEmpty();
        assertThat(trimestreRepository.findByNumeroAndAnnee(NUMERO, ANNEE)).isEmpty();
        ImportExcel journal = importExcelRepository.findAllRecentsDabord().get(0);
        assertThat(journal.getStatut()).isEqualTo("ECHEC");
        assertThat(journal.getNomFichier()).isEqualTo("notes.txt");
        assertThat(journal.getMessage()).startsWith("Fichier illisible");
    }

    @Test
    void un_classeur_sans_aucune_feuille_connue_echoue() {
        ResultatImport resultat = importer(ClasseurDeTest.vide()
                .sans(FEUILLE_COLLABORATEURS).sans(FEUILLE_PERFORMANCE).sans(FEUILLE_POTENTIEL)
                .sans(FEUILLE_COMPETENCES).sans(FEUILLE_SKILLS).sans(FEUILLE_POSTES)
                .sans(FEUILLE_POSTES_CRITIQUES).sans(FEUILLE_SUCCESSION).sans(FEUILLE_TALENTS)
                .sans(FEUILLE_VIGILANCE).autreFeuille("Feuil1"));

        assertThat(resultat.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(resultat.nbErreurs()).isEqualTo(10);
        assertThat(resultat.message()).contains("Format du classeur non conforme (10 écart(s))");
    }

    @Test
    void un_trimestre_invalide_ou_un_fichier_vide_sont_refuses_avant_tout_import() {
        MockMultipartFile vide = new MockMultipartFile("fichier", "vide.xlsx", null, new byte[0]);

        assertThatThrownBy(() -> importService.importer(ClasseurDeTest.complet().fichier(), ANNEE, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 1 et 4");
        assertThatThrownBy(() -> importService.importer(vide, ANNEE, NUMERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vide");
        assertThat(importExcelRepository.findAllRecentsDabord()).isEmpty();
    }

    // ------------------------------------------------------------------ re-import

    @Test
    void reimporter_le_meme_trimestre_met_a_jour_sans_doubler() {
        importer(ClasseurDeTest.complet());

        ResultatImport second = importer(ClasseurDeTest.complet()
                .cellule(FEUILLE_COLLABORATEURS, 0, 1, "Alaoui-Bennani")
                .cellule(FEUILLE_PERFORMANCE, 0, 3, 60)
                .cellule(FEUILLE_SKILLS, 0, 4, 4)
                .cellule(FEUILLE_POSTES, 0, 1, "Directeur regional renomme")
                .cellule(FEUILLE_TALENTS, 2, 6, "Oui")
                .cellule(FEUILLE_TALENTS, 1, 8, "Vivier Expertise")
                .cellule(FEUILLE_VIGILANCE, 0, 3, 40)
                .cellule(FEUILLE_VIGILANCE, 0, 5, "Non"));

        assertThat(second.statut()).isEqualTo(StatutImport.SUCCES);
        Trimestre trimestre = trimestre();

        // Aucun doublon.
        assertThat(collaborateurRepository.findAllById(List.of(E1, E2, E3))).hasSize(3);
        assertThat(skillRepository.findByCollaborateurIdsAvecCompetence(List.of(E1, E2, E3))).hasSize(3);
        assertThat(posteRepository.findAllById(List.of(P1, P2))).hasSize(2);
        assertThat(performanceRepository.findByTrimestreAvecCollaborateur(trimestre, MANAGER)).hasSize(3);
        assertThat(potentielRepository.findByTrimestreAvecCollaborateur(trimestre, MANAGER)).hasSize(3);
        assertThat(successeurRepository.findAllAvecPosteEtCollaborateur()).hasSize(2);
        assertThat(validationRepository.findByTrimestreAvecCollaborateur(trimestre)).hasSize(3);
        assertThat(rattachementRepository.findAll()).hasSize(2);
        assertThat(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre)).hasSize(2);
        assertThat(declarationRepository.findByTrimestreAvecCollaborateur(trimestre)).hasSize(3);
        assertThat(trimestreRepository.findAll()).filteredOn(t -> t.getAnnee() == ANNEE).hasSize(1);

        // Valeurs mises a jour.
        assertThat(collaborateurRepository.findById(E1).orElseThrow().getNom()).isEqualTo("Alaoui-Bennani");
        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre, MANAGER).orElseThrow()
                .getNoteObjectifs()).isEqualByComparingTo("60");
        assertThat(skillRepository.findByCollaborateurIdsAvecCompetence(List.of(E1))).filteredOn(
                s -> s.getCompetence().getCompetenceId().equals(C1)).singleElement()
                .extracting(CompetenceCollaborateur::getNiveauActuel).isEqualTo(4);
        assertThat(posteRepository.findById(P1).orElseThrow().getNomPoste()).isEqualTo("Directeur regional renomme");
        // Le titulaire pose par 08 survit a la mise a jour du poste par 07.
        assertThat(posteRepository.findById(P1).orElseThrow().getTitulaireId()).isEqualTo(E3);
        assertThat(validationsEnBase.statut(E3, trimestre)).isEqualTo(StatutValidationComite.OUI);
        assertThat(viviersEnBase.vivierPourDirection("Risques")).contains(VivierThematique.EXPERTISE);
        assertThat(questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre)).filteredOn(
                q -> q.getCollaborateur().getIdCollaborateur().equals(E1)).singleElement()
                .extracting(q -> q.getScoreEngagement().intValueExact()).isEqualTo(40);
        assertThat(faitsEnBase.faitsDuTrimestre(trimestre).get(E1).declare(SignalVigilance.SANS_MOBILITE_4_ANS))
                .isFalse();

        // Deux entrees au journal, la plus recente en premier.
        List<ImportExcel> journal = importExcelRepository.findAllRecentsDabord();
        assertThat(journal).hasSize(2);
        assertThat(journal.get(0).getIdImport()).isEqualTo(second.idImport());
    }

    @Test
    void un_successeur_retire_de_09_est_retire_de_la_base() {
        importer(ClasseurDeTest.complet());

        importer(ClasseurDeTest.complet().retirerLigne(FEUILLE_SUCCESSION, 1));

        assertThat(successeursEnBase.successeursIdentifies(P1)).containsExactly(E1);
    }

    @Test
    void avec_une_ligne_09_invalide_aucun_successeur_n_est_retire() {
        importer(ClasseurDeTest.complet());

        ResultatImport second = importer(ClasseurDeTest.complet()
                .remplacer(FEUILLE_SUCCESSION, 1, P1, "TST404", "Inconnu"));

        assertThat(successeursEnBase.successeursIdentifies(P1)).containsExactlyInAnyOrder(E1, E2);
        assertThat(second.erreurs()).filteredOn(e -> e.feuille().equals(FEUILLE_SUCCESSION))
                .extracting(ErreurImport::ligne)
                .containsExactlyInAnyOrder(ligneExcel(FEUILLE_SUCCESSION, 1), null);
    }

    @Test
    void les_notes_d_un_autre_trimestre_ne_sont_pas_touchees() {
        importer(ClasseurDeTest.complet());
        importService.importer(ClasseurDeTest.complet().cellule(FEUILLE_PERFORMANCE, 0, 3, 55).fichier(),
                ANNEE, 4);

        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, trimestre(), MANAGER).orElseThrow()
                .getNoteObjectifs()).isEqualByComparingTo("85");
        Trimestre t4 = trimestreRepository.findByNumeroAndAnnee(4, ANNEE).orElseThrow();
        assertThat(performanceRepository
                .findByCollaborateurIdCollaborateurAndTrimestreAndSource(E1, t4, MANAGER).orElseThrow()
                .getNoteObjectifs()).isEqualByComparingTo("55");
    }

    private static String message(ResultatImport resultat, String feuille) {
        return resultat.erreurs().stream().filter(e -> e.feuille().equals(feuille)).findFirst().orElseThrow()
                .message();
    }
}
