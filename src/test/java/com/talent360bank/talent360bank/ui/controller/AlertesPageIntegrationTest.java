package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.SuccesseurIdentifie;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
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
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.AlertesView;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TypeAlerte;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Ecran Alertes sur une population connue : chaque type d'alerte, le tri par
 * gravite, les compteurs, les filtres, le trimestre affiche, et l'egalite avec
 * le panneau du tableau de bord.
 *
 * <p>T1 2026, reglages par defaut sauf un minimum de 2 successeurs par poste
 * critique. M01 manage l'agence Centre (Reseau Retail) :
 * <ul>
 *   <li>A01 95/95, talent, sans decision du Comite ; auto-evaluation 100 (ecart +5, sous le seuil) ;</li>
 *   <li>A02 manager 70/70, auto-evaluation 90/90 : ecart +20, au-dela du seuil de 15 ;</li>
 *   <li>A03 auto-evaluation seule ; A04 performance du manager seule ; A05 aucune evaluation ;</li>
 *   <li>A06 60/60, engagement 20 + deux faits : vigilance 60, ELEVEE ;</li>
 *   <li>A07 deux competences en gap Prioritaire ;</li>
 *   <li>R01 (Risques) talent valide par le Comite : aucune alerte.</li>
 * </ul>
 * PA (Reseau Retail) sans successeur ; PB (Risques) un seul successeur, A01, pour un minimum de 2.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:alertes-page;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class AlertesPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CalculTrimestreService calculTrimestreService;
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
    private ValidationComiteRepository validationComiteRepository;
    @Autowired
    private CompetenceRepository competenceRepository;
    @Autowired
    private CompetenceCollaborateurRepository competenceCollaborateurRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private SuccesseurIdentifieRepository successeurRepository;

    private Trimestre t1;
    private Competence competence;

    @BeforeAll
    void peupler() {
        t1 = trimestre(1);
        Parametre parametre = Parametre.parDefaut(t1);
        parametre.getSeuilsCouverture().setNbMinSuccesseurs(2);
        parametreRepository.save(parametre);
        Entite retail = entiteRepository.save(new Entite("Reseau Retail", TypeEntite.DIRECTION, null));
        Entite centre = entiteRepository.save(new Entite("Agence Centre", TypeEntite.AGENCE, retail));
        Entite risques = entiteRepository.save(new Entite("Risques", TypeEntite.DIRECTION, null));
        competence = new Competence();
        competence.setCompetenceId("C01");
        competence.setNom("Credit");
        competence = competenceRepository.save(competence);

        Collaborateur m01 = notes(collaborateur("M01", centre, null), "70", "70");
        Manager manager = managerRepository.save(new Manager(m01));

        Collaborateur a01 = notes(collaborateur("A01", centre, manager), "95", "95");
        auto(a01, "100", "100");
        Collaborateur a02 = notes(collaborateur("A02", centre, manager), "70", "70");
        auto(a02, "90", "90");
        Collaborateur a03 = collaborateur("A03", centre, manager);
        auto(a03, "80", "80");
        Collaborateur a04 = collaborateur("A04", centre, manager);
        BigDecimal soixante = new BigDecimal("60");
        performanceRepository.save(new Performance(a04, t1, soixante, soixante, soixante, soixante, soixante));
        collaborateur("A05", centre, manager);
        Collaborateur a06 = notes(collaborateur("A06", centre, manager), "60", "60");
        questionnaire(a06, "20");
        DeclarationVigilance faits = new DeclarationVigilance(a06, t1);
        faits.setSansMobilite4Ans(true);
        faits.setMobiliteNonTraitee(true);
        declarationRepository.save(faits);
        Collaborateur a07 = notes(collaborateur("A07", centre, manager), "70", "70");
        skill(a07, 1, 4);
        skill(a07, 2, 5);
        Collaborateur r01 = notes(collaborateur("R01", risques, null), "95", "95");
        validationComiteRepository.save(new ValidationComite(r01, t1, StatutValidationComite.OUI));

        poste("PA", "Directeur regional", retail);
        Poste pb = poste("PB", "Responsable risques", risques);
        successeurRepository.save(new SuccesseurIdentifie(pb, a01));

        calculTrimestreService.calculer(t1);
    }

    // ------------------------------------------------------------ chaque type d'alerte

    @Test
    @Order(1)
    void chaque_type_d_alerte_est_calcule_et_trie_par_gravite() throws Exception {
        AlertesView vue = vue(get("/alertes"));

        assertThat(vue.trimestreLibelle()).isEqualTo("T1 2026");
        assertThat(vue.erreur()).isNull();
        assertThat(vue.alertes()).extracting(AlerteVue::severite, AlerteVue::type, AlerteVue::matricule)
                .containsExactly(
                        tuple(SeveriteAlerte.CRITIQUE, TypeAlerte.POSTE_SANS_SUCCESSEUR, "PA"),
                        tuple(SeveriteAlerte.ELEVEE, TypeAlerte.POSTE_SOUS_MINIMUM, "PB"),
                        tuple(SeveriteAlerte.ELEVEE, TypeAlerte.VIGILANCE_ELEVEE, "A06"),
                        tuple(SeveriteAlerte.ELEVEE, TypeAlerte.EVALUATION_MANAGER_MANQUANTE, "A03"),
                        tuple(SeveriteAlerte.ELEVEE, TypeAlerte.EVALUATION_MANAGER_MANQUANTE, "A04"),
                        tuple(SeveriteAlerte.ELEVEE, TypeAlerte.EVALUATION_MANAGER_MANQUANTE, "A05"),
                        tuple(SeveriteAlerte.MOYENNE, TypeAlerte.ECART_AUTO_MANAGER, "A02"),
                        tuple(SeveriteAlerte.MOYENNE, TypeAlerte.TALENT_SANS_DECISION, "A01"),
                        tuple(SeveriteAlerte.MOYENNE, TypeAlerte.GAPS_COMPETENCES_PRIORITAIRES, "A07"));
        assertThat(vue.total()).isEqualTo(9);

        Map<String, AlerteVue> parSujet = vue.alertes().stream()
                .collect(Collectors.toMap(a -> a.type() + "/" + a.matricule(), Function.identity()));

        AlerteVue pa = parSujet.get("POSTE_SANS_SUCCESSEUR/PA");
        assertThat(pa.sujet()).isEqualTo("Directeur regional");
        assertThat(pa.direction()).isEqualTo("Reseau Retail");
        assertThat(pa.lien()).isEqualTo("/postes-critiques?trimestre=2026-1");
        assertThat(parSujet.get("POSTE_SOUS_MINIMUM/PB").message()).contains("1 successeur", "minimum 2");

        AlerteVue a06 = parSujet.get("VIGILANCE_ELEVEE/A06");
        assertThat(a06.sujet()).isEqualTo("PrenomA06 NomA06");
        assertThat(a06.entite()).isEqualTo("Agence Centre");
        assertThat(a06.direction()).isEqualTo("Reseau Retail");
        assertThat(a06.message()).contains("60", "Engagement faible");
        assertThat(a06.lien()).isEqualTo("/api/trimestres/2026/1/collaborateurs/A06/fiche");

        assertThat(parSujet.get("EVALUATION_MANAGER_MANQUANTE/A03").message()).contains("Auto-évaluation seule");
        assertThat(parSujet.get("EVALUATION_MANAGER_MANQUANTE/A04").message()).contains("potentiel");
        assertThat(parSujet.get("EVALUATION_MANAGER_MANQUANTE/A05").message()).contains("Aucune évaluation");
        assertThat(parSujet.get("EVALUATION_MANAGER_MANQUANTE/A05").lien())
                .isEqualTo("/api/trimestres/2026/1/managers/M01/vue");

        AlerteVue a02 = parSujet.get("ECART_AUTO_MANAGER/A02");
        assertThat(a02.message()).contains("performance +20", "potentiel +20", "seuil 15");
        assertThat(a02.lienLibelle()).isEqualTo("Vue manager");

        assertThat(parSujet.get("TALENT_SANS_DECISION/A01").lien())
                .isEqualTo("/comite-talent?trimestre=2026-1&statut=EN_ATTENTE");
        assertThat(parSujet.get("GAPS_COMPETENCES_PRIORITAIRES/A07").message()).startsWith("2 compétence(s)");
    }

    @Test
    @Order(2)
    void les_compteurs_portent_sur_toutes_les_alertes_par_type_et_par_gravite() throws Exception {
        AlertesView vue = vue(get("/alertes").param("severite", "MOYENNE"));

        assertThat(vue.parSeverite()).extracting(AlertesView.Compteur::code, AlertesView.Compteur::nombre)
                .containsExactly(tuple("CRITIQUE", 1), tuple("ELEVEE", 5), tuple("MOYENNE", 3));
        assertThat(vue.parType()).extracting(AlertesView.Compteur::code, AlertesView.Compteur::nombre)
                .containsExactly(tuple("POSTE_SANS_SUCCESSEUR", 1), tuple("POSTE_SOUS_MINIMUM", 1),
                        tuple("UN_SEUL_SUCCESSEUR", 0),
                        tuple("VIGILANCE_ELEVEE", 1), tuple("EVALUATION_MANAGER_MANQUANTE", 3),
                        tuple("ECART_AUTO_MANAGER", 1), tuple("TALENT_SANS_DECISION", 1),
                        tuple("NOUVEAU_TALENT", 0), tuple("GAPS_COMPETENCES_PRIORITAIRES", 1));
        // Un seul trimestre : pas de nouveau talent, et la page le dit.
        assertThat(vue.informations()).anyMatch(information -> information.contains("aucun trimestre précédent"));
        assertThat(vue.total()).isEqualTo(9);
        assertThat(vue.alertes()).hasSize(3);
    }

    @Test
    @Order(3)
    void le_panneau_du_tableau_de_bord_montre_les_memes_alertes() throws Exception {
        AlertesView alertes = vue(get("/alertes"));
        TableauDeBordView tableau = (TableauDeBordView) mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("tableau");

        assertThat(tableau.nbAlertes()).isEqualTo(alertes.total()).isEqualTo(9);
        assertThat(tableau.alertes()).isEqualTo(alertes.alertes().subList(0, TableauDeBordView.ALERTES_AFFICHEES));
    }

    // ------------------------------------------------------------ filtres

    @Test
    @Order(4)
    void les_filtres_par_type_gravite_direction_et_nom() throws Exception {
        assertThat(vue(get("/alertes").param("type", "EVALUATION_MANAGER_MANQUANTE")).alertes())
                .extracting(AlerteVue::matricule).containsExactly("A03", "A04", "A05");
        assertThat(vue(get("/alertes").param("severite", "critique")).alertes())
                .extracting(AlerteVue::matricule).containsExactly("PA");
        assertThat(vue(get("/alertes").param("direction", "Risques")).alertes())
                .extracting(AlerteVue::matricule).containsExactly("PB");
        // Recherche sans casse ni accents, sur le nom ou le matricule.
        assertThat(vue(get("/alertes").param("q", "nómA0")).alertes()).hasSize(7);
        assertThat(vue(get("/alertes").param("q", "a06")).alertes())
                .extracting(AlerteVue::matricule).containsExactly("A06");
        assertThat(vue(get("/alertes").param("type", "EVALUATION_MANAGER_MANQUANTE").param("q", "A05")).alertes())
                .extracting(AlerteVue::matricule).containsExactly("A05");
        // Un filtre inconnu vaut "tous".
        AlertesView inconnu = vue(get("/alertes").param("type", "XYZ").param("severite", "?"));
        assertThat(inconnu.alertes()).hasSize(9);
        assertThat(inconnu.filtres().type()).isNull();
        assertThat(vue(get("/alertes")).directions()).containsExactly("Reseau Retail", "Risques");
    }

    @Test
    @Order(5)
    void la_page_affiche_compteurs_tableau_et_liens() throws Exception {
        mockMvc.perform(get("/alertes").param("type", "VIGILANCE_ELEVEE"))
                .andExpect(status().isOk())
                .andExpect(view().name("alertes"))
                .andExpect(content().string(containsString("Vigilance élevée : 1")))
                .andExpect(content().string(containsString("PrenomA06 NomA06")))
                .andExpect(content().string(containsString("/api/trimestres/2026/1/collaborateurs/A06/fiche")))
                .andExpect(content().string(containsString("1 alerte(s) affichee(s) sur 9")));
    }

    // ------------------------------------------------------------ trimestre affiche

    @Test
    @Order(6)
    void le_trimestre_se_choisit_et_un_trimestre_inconnu_rend_404() throws Exception {
        mockMvc.perform(get("/alertes").param("trimestre", "2031-1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/alertes").param("trimestre", "abc")).andExpect(status().isNotFound());

        trimestre(2);
        mockMvc.perform(get("/alertes"))
                .andExpect(model().attribute("trimestre", new OptionTrimestre("2026-1", "T1 2026", 2026, 1, true)));
        AlertesView t2 = vue(get("/alertes").param("trimestre", "2026-2"));
        assertThat(t2.trimestreLibelle()).isEqualTo("T2 2026");
        assertThat(t2.erreur()).contains("Aucun parametre");
        assertThat(t2.total()).isZero();
        assertThat(t2.alertes()).isEmpty();
    }

    // ------------------------------------------------------------ outils

    private AlertesView vue(MockHttpServletRequestBuilder requete) throws Exception {
        return (AlertesView) mockMvc.perform(requete).andExpect(status().isOk()).andReturn()
                .getModelAndView().getModel().get("vue");
    }

    private Trimestre trimestre(int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(numero);
        trimestre.setDateReference(Trimestre.dernierJour(2026, numero));
        return trimestreRepository.save(trimestre);
    }

    private Collaborateur collaborateur(String id, Entite entite, Manager manager) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom("Nom" + id);
        collaborateur.setPrenom("Prenom" + id);
        collaborateur.setDateEntree(LocalDate.of(2018, 1, 1));
        collaborateur.setEntite(entite);
        collaborateur.setManager(manager);
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
        return collaborateurRepository.save(collaborateur);
    }

    private Collaborateur notes(Collaborateur collaborateur, String performance, String potentiel) {
        BigDecimal p = new BigDecimal(performance);
        BigDecimal q = new BigDecimal(potentiel);
        performanceRepository.save(new Performance(collaborateur, t1, p, p, p, p, p));
        potentielRepository.save(new Potentiel(collaborateur, t1, q, q, q, q, q, q, q));
        return collaborateur;
    }

    private void auto(Collaborateur collaborateur, String performance, String potentiel) {
        BigDecimal p = new BigDecimal(performance);
        BigDecimal q = new BigDecimal(potentiel);
        Performance perf = new Performance(collaborateur, t1, p, p, p, p, p);
        perf.setSource(SourceEvaluation.AUTO);
        performanceRepository.save(perf);
        Potentiel pot = new Potentiel(collaborateur, t1, q, q, q, q, q, q, q);
        pot.setSource(SourceEvaluation.AUTO);
        potentielRepository.save(pot);
    }

    private void questionnaire(Collaborateur collaborateur, String score) {
        QuestionnaireEngagement reponse = new QuestionnaireEngagement();
        reponse.setCollaborateur(collaborateur);
        reponse.setTrimestre(t1);
        reponse.setScoreEngagement(new BigDecimal(score));
        reponse.setDateReponse(LocalDate.of(2026, 3, 1));
        questionnaireRepository.save(reponse);
    }

    private void skill(Collaborateur collaborateur, int actuel, int cible) {
        CompetenceCollaborateur skill = new CompetenceCollaborateur();
        skill.setCollaborateur(collaborateur);
        skill.setCompetence(competence);
        skill.setNiveauActuel(actuel);
        skill.setNiveauCible(cible);
        competenceCollaborateurRepository.save(skill);
    }

    private Poste poste(String id, String nom, Entite direction) {
        Poste poste = new Poste();
        poste.setPosteId(id);
        poste.setNomPoste(nom);
        poste.setEntite(direction);
        poste.setPosteCritique("Oui");
        poste.setCriticite("Elevee");
        poste.setCompetenceRequise1(competence);
        poste.setNiveau1(3);
        return posteRepository.save(poste);
    }
}
