package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.SuccesseurIdentifie;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.DeclarationVigilanceRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.ui.model.VueEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.ComparaisonEnfant;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.Compte;
import com.talent360bank.talent360bank.ui.model.VueManager.CompteCase;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vue entite sur une vraie base : sous-arbre, synthese egale a la somme des
 * enfants, memes chiffres que la vue manager pour les memes personnes, entite
 * vide, 404, postes critiques, et un nombre de requetes qui ne grandit pas
 * avec le sous-arbre.
 *
 * <p>Organigramme : Reseau (direction) > Nord (departement) > Tanger (region :
 * agences Centre et Port), Big (region : quatre agences) et Fes (region :
 * agence Medina, vide). L'equipe du manager MPORT (rattache au departement)
 * est exactement l'agence Port.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vue-entite;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VueEntiteViewServiceTest {

    @Autowired
    private VueEntiteViewService service;
    @Autowired
    private VueManagerViewService vueManagerService;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
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
    private QuestionnaireEngagementRepository questionnaireRepository;
    @Autowired
    private DeclarationVigilanceRepository declarationRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private SuccesseurIdentifieRepository successeurRepository;

    private Trimestre trimestre;
    private Entite direction;
    private Entite nord;
    private Entite tanger;
    private Entite centre;
    private Entite port;
    private Entite big;
    private Entite medina;

    @BeforeAll
    void poserLesDonnees() {
        trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(1);
        trimestre = trimestreRepository.save(trimestre);
        parametreRepository.save(Parametre.parDefaut(trimestre));

        direction = entiteRepository.save(new Entite("Reseau", TypeEntite.DIRECTION, null));
        nord = entiteRepository.save(new Entite("Nord", TypeEntite.DEPARTEMENT, direction));
        tanger = entiteRepository.save(new Entite("Tanger", TypeEntite.REGION, nord));
        centre = entiteRepository.save(new Entite("Tanger Centre", TypeEntite.AGENCE, tanger));
        port = entiteRepository.save(new Entite("Tanger Port", TypeEntite.AGENCE, tanger));
        big = entiteRepository.save(new Entite("Big", TypeEntite.REGION, nord));
        Entite fes = entiteRepository.save(new Entite("Fes", TypeEntite.REGION, nord));
        medina = entiteRepository.save(new Entite("Fes Medina", TypeEntite.AGENCE, fes));

        // Agence Centre : un talent, un profil a vigilance elevee, un archive (hors effectif).
        Collaborateur talent = personne("E01", "Alami", centre, null, "92", "91", "80.00", false);
        personne("E02", "Berrada", centre, null, "70", "72", "40.00", true);
        collaborateur("E03", "Chami", centre, null, StatutCollaborateur.ARCHIVE);

        // Agence Port = equipe du manager MPORT, lui-meme rattache au departement.
        Manager mport = managerRepository.save(new Manager(
                personne("MPORT", "Zaki", nord, null, "85", "80", "70.00", false)));
        personne("E04", "Daoudi", port, mport, "88", "86", "75.00", false);
        personne("E05", "Ennaji", port, mport, "60", "65", "55.00", true);
        collaborateur("E06", "Fassi", port, mport, StatutCollaborateur.ACTIF);    // aucune donnee

        // Region Big : quatre agences de trois personnes.
        Collaborateur titulaireBig = null;
        for (int a = 1; a <= 4; a++) {
            Entite agence = entiteRepository.save(new Entite("Big " + a, TypeEntite.AGENCE, big));
            for (int p = 1; p <= 3; p++) {
                Collaborateur c = personne("B" + a + p, "Nom" + a + p, agence, null, "7" + p, "8" + p, "60.00",
                        p == 1);
                if (titulaireBig == null) {
                    titulaireBig = c;
                }
            }
        }

        // Un poste critique par region, rattache a la direction, tenu dans la region, avec un successeur.
        posteCritique("PST1", direction, talent, "E04");
        posteCritique("PST2", direction, titulaireBig, "B12");

        calculTrimestreService.calculer(trimestre);
    }

    // ------------------------------------------------------------ agence et region

    @Test
    void une_agence_donne_son_entite_ses_chiffres_ses_alertes_et_ses_postes() {
        VueEntite vue = service.construire(centre.getCode(), 2026, 1);

        assertThat(vue.entite().libelle()).isEqualTo("Tanger Centre");
        assertThat(vue.entite().type()).isEqualTo("AGENCE");
        assertThat(vue.entite().chemin()).containsExactly("Reseau", "Nord", "Tanger", "Tanger Centre");
        assertThat(vue.entite().parent().code()).isEqualTo(tanger.getCode());
        assertThat(vue.entite().enfants()).isEmpty();
        assertThat(vue.enfants()).isEmpty();

        // E03 est archive : hors effectif.
        assertThat(vue.synthese().effectif()).isEqualTo(2);
        assertThat(vue.synthese().nbTalents()).isEqualTo(1);
        // E02 : engagement 40, deux faits declares : 25 + 20 + 15 = 60, ELEVEE.
        assertThat(vue.alertes()).singleElement().satisfies(alerte -> {
            assertThat(alerte.matricule()).isEqualTo("E02");
            assertThat(alerte.niveau()).isEqualTo("ELEVEE");
            assertThat(alerte.indice()).isEqualByComparingTo("60.00");
            assertThat(alerte.entite().libelle()).isEqualTo("Tanger Centre");
        });
        // PST1 est rattache a la direction mais tenu par E01, de l'agence.
        assertThat(vue.postesCritiques()).singleElement().satisfies(poste -> {
            assertThat(poste.posteId()).isEqualTo("PST1");
            assertThat(poste.rattachement()).isEqualTo("TITULAIRE");
            assertThat(poste.nbSuccesseurs()).isEqualTo(1);
            assertThat(poste.couverture()).isNotNull();
        });
        assertThat(vue.managers()).isEmpty();
        assertThat(vue.donneesManquantes()).isEmpty();
    }

    @Test
    void la_synthese_d_une_region_est_la_somme_de_ses_agences() {
        VueEntite region = service.construire(tanger.getCode(), 2026, 1);
        VueEntite vueCentre = service.construire(centre.getCode(), 2026, 1);
        VueEntite vuePort = service.construire(port.getCode(), 2026, 1);
        List<VueManager.Synthese> agences = List.of(vueCentre.synthese(), vuePort.synthese());

        assertThat(region.entite().enfants()).extracting(VueEntite.EntiteEnfant::libelle)
                .containsExactly("Tanger Centre", "Tanger Port");
        assertThat(region.entite().enfants()).extracting(VueEntite.EntiteEnfant::effectif).containsExactly(2, 3);

        VueManager.Synthese somme = region.synthese();
        assertThat(somme.effectif()).isEqualTo(somme(agences, VueManager.Synthese::effectif)).isEqualTo(5);
        assertThat(somme.nbAvecScore()).isEqualTo(somme(agences, VueManager.Synthese::nbAvecScore));
        assertThat(somme.nbTalents()).isEqualTo(somme(agences, VueManager.Synthese::nbTalents));
        assertThat(somme.nbHautsPotentiels()).isEqualTo(somme(agences, VueManager.Synthese::nbHautsPotentiels));
        assertThat(somme.nbVivierSuccession()).isEqualTo(somme(agences, VueManager.Synthese::nbVivierSuccession));
        assertThat(somme.nbSansVigilance()).isEqualTo(somme(agences, VueManager.Synthese::nbSansVigilance));
        for (int i = 0; i < 9; i++) {
            int n = i;
            assertThat(somme.neufBox().get(i).nombre()).as("case " + (i + 1))
                    .isEqualTo(somme(agences, s -> s.neufBox().get(n).nombre()));
        }
        for (int i = 0; i < somme.categoriesPerformance().size(); i++) {
            int n = i;
            assertThat(somme.categoriesPerformance().get(i).nombre())
                    .isEqualTo(somme(agences, s -> s.categoriesPerformance().get(n).nombre()));
        }
        for (int i = 0; i < somme.niveauxVigilance().size(); i++) {
            int n = i;
            assertThat(somme.niveauxVigilance().get(i).nombre())
                    .isEqualTo(somme(agences, s -> s.niveauxVigilance().get(n).nombre()));
        }

        // Une ligne de comparaison par agence, avec les chiffres de sa propre vue.
        assertThat(region.enfants()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            ComparaisonEnfant ligne = region.enfants().get(i);
            VueManager.Synthese agence = agences.get(i);
            assertThat(ligne.effectif()).isEqualTo(agence.effectif());
            assertThat(ligne.nbAvecScore()).isEqualTo(agence.nbAvecScore());
            assertThat(ligne.moyennePerformance()).isEqualTo(agence.moyennePerformance());
            assertThat(ligne.moyennePotentiel()).isEqualTo(agence.moyennePotentiel());
            assertThat(ligne.moyenneEngagement()).isEqualTo(agence.moyenneEngagement());
        }
        ComparaisonEnfant lignePort = region.enfants().get(1);
        assertThat(lignePort.nbVigilanceElevee()).isEqualTo(vuePort.alertes().size());
        assertThat(region.enfants().get(0).pourcentageTalents()).isEqualByComparingTo("50.0");
        assertThat(region.alertes()).extracting(VueEntite.AlerteVigilance::indice)
                .isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(region.postesCritiques()).extracting(VueEntite.PosteCritiqueVue::posteId).containsExactly("PST1");
    }

    @Test
    void la_vue_entite_et_la_vue_manager_donnent_les_memes_chiffres_pour_les_memes_personnes() {
        VueManager manager = vueManagerService.construire("MPORT", 2026, 1);
        VueEntite agence = service.construire(port.getCode(), 2026, 1);

        assertThat(manager.membres()).extracting(VueManager.Membre::matricule)
                .containsExactlyInAnyOrder("E04", "E05", "E06");
        assertThat(agence.synthese()).isEqualTo(manager.synthese());
    }

    @Test
    void le_departement_compte_son_manager_rattache_directement_et_liste_ses_managers() {
        VueEntite vue = service.construire(nord.getCode(), 2026, 1);

        // Tanger (5) + Big (12) + Fes (0) + MPORT rattache au departement lui-meme.
        assertThat(vue.synthese().effectif()).isEqualTo(18);
        assertThat(vue.enfants()).extracting(ComparaisonEnfant::libelle).containsExactly("Big", "Fes", "Tanger");
        assertThat(vue.enfants().stream().mapToInt(ComparaisonEnfant::effectif).sum()).isEqualTo(17);
        assertThat(vue.managers()).extracting(VueManager.ManagerResume::matricule).containsExactly("MPORT");
        assertThat(vue.managers().get(0).tailleEquipe()).isEqualTo(3);

        VueEntite vueDirection = service.construire(direction.getCode(), 2026, 1);
        assertThat(vueDirection.postesCritiques()).extracting(VueEntite.PosteCritiqueVue::rattachement)
                .containsOnly("ENTITE");
        assertThat(vueDirection.entite().parent()).isNull();
    }

    // ------------------------------------------------------------ cas limites

    @Test
    void une_entite_vide_donne_une_synthese_vide_sans_erreur() {
        VueEntite vue = service.construire(medina.getCode(), 2026, 1);

        assertThat(vue.synthese().effectif()).isZero();
        assertThat(vue.synthese().moyennePerformance()).isNull();
        assertThat(vue.synthese().neufBox()).hasSize(9).extracting(CompteCase::nombre).containsOnly(0);
        assertThat(vue.synthese().niveauxVigilance()).extracting(Compte::nombre).containsOnly(0);
        assertThat(vue.alertes()).isEmpty();
        assertThat(vue.postesCritiques()).isEmpty();
        assertThat(vue.managers()).isEmpty();
        assertThat(vue.donneesManquantes()).isEmpty();
    }

    @Test
    void une_entite_ou_un_trimestre_inconnu_est_introuvable() {
        assertThatThrownBy(() -> service.construire("DIR:INCONNUE", 2026, 1))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessage("Aucune entité DIR:INCONNUE");
        assertThatThrownBy(() -> service.construire(centre.getCode(), 2031, 2))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessage("Aucun trimestre T2 2031");
    }

    // ------------------------------------------------------------ arbre et requetes

    @Test
    void l_arbre_des_entites_donne_l_effectif_de_chaque_sous_arbre() {
        List<NoeudEntite> arbre = service.listerEntites();

        NoeudEntite reseau = arbre.stream().filter(n -> n.code().equals(direction.getCode())).findFirst().orElseThrow();
        assertThat(reseau.effectif()).isEqualTo(18);
        NoeudEntite noeudNord = reseau.enfants().get(0);
        assertThat(noeudNord.libelle()).isEqualTo("Nord");
        assertThat(noeudNord.enfants()).extracting(NoeudEntite::libelle).containsExactly("Big", "Fes", "Tanger");
        assertThat(noeudNord.enfants()).extracting(NoeudEntite::effectif).containsExactly(12, 0, 5);
        assertThat(noeudNord.enfants().get(2).enfants()).extracting(NoeudEntite::effectif).containsExactly(2, 3);
    }

    @Test
    void le_nombre_de_requetes_ne_grandit_pas_avec_le_sous_arbre() {
        long petiteRegion = requetes(() -> service.construire(tanger.getCode(), 2026, 1));   // 2 agences, 5 personnes
        long grandeRegion = requetes(() -> service.construire(big.getCode(), 2026, 1));      // 4 agences, 12 personnes

        assertThat(service.construire(big.getCode(), 2026, 1).synthese().effectif()).isEqualTo(12);
        assertThat(grandeRegion).isEqualTo(petiteRegion);
        assertThat(petiteRegion).isLessThanOrEqualTo(25);
        assertThat(requetes(() -> service.listerEntites())).isEqualTo(2);
    }

    // ------------------------------------------------------------ outils

    private static int somme(List<VueManager.Synthese> syntheses, ToIntFunction<VueManager.Synthese> valeur) {
        return syntheses.stream().mapToInt(valeur).sum();
    }

    private long requetes(Runnable appel) {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        appel.run();
        return statistiques.getPrepareStatementCount();
    }

    private Collaborateur collaborateur(String id, String nom, Entite entite, Manager manager,
                                        StatutCollaborateur statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom(nom);
        collaborateur.setPrenom("Prenom" + id);
        collaborateur.setFonction("Conseiller");
        collaborateur.setEntite(entite);
        collaborateur.setManager(manager);
        collaborateur.setDateEntree(LocalDate.of(2018, 1, 1));
        collaborateur.setStatut(statut);
        return collaborateurRepository.save(collaborateur);
    }

    /** Collaborateur actif avec notes, questionnaire et, si demande, deux faits de vigilance. */
    private Collaborateur personne(String id, String nom, Entite entite, Manager manager, String performance,
                                   String potentiel, String engagement, boolean faits) {
        Collaborateur collaborateur = collaborateur(id, nom, entite, manager, StatutCollaborateur.ACTIF);
        BigDecimal p = new BigDecimal(performance);
        BigDecimal q = new BigDecimal(potentiel);
        performanceRepository.save(new Performance(collaborateur, trimestre, p, p, p, p, p));
        potentielRepository.save(new Potentiel(collaborateur, trimestre, q, q, q, q, q, q, q));
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(collaborateur);
        reponse.setTrimestre(trimestre);
        reponse.setScoreEngagement(new BigDecimal(engagement));
        questionnaireRepository.save(reponse);
        if (faits) {
            DeclarationVigilance declaration = new DeclarationVigilance(collaborateur, trimestre);
            declaration.setSansMobilite4Ans(true);
            declaration.setMobiliteNonTraitee(true);
            declarationRepository.save(declaration);
        }
        return collaborateur;
    }

    private void posteCritique(String id, Entite direction, Collaborateur titulaire, String successeur) {
        Poste poste = new Poste();
        poste.setPosteId(id);
        poste.setNomPoste("Directeur " + id);
        poste.setPosteCritique("Oui");
        poste.setCriticite("Elevee");
        poste.setEntite(direction);
        poste.setTitulaireId(titulaire.getIdCollaborateur());
        poste.setTitulaireNom(titulaire.getPrenom() + " " + titulaire.getNom());
        posteRepository.save(poste);
        successeurRepository.save(new SuccesseurIdentifie(poste,
                collaborateurRepository.findById(successeur).orElseThrow()));
    }
}
