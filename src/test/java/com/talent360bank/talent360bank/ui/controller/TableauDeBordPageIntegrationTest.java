package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.RattachementVivier;
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
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.TableauDeBordService;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.VivierTableau;
import com.talent360bank.talent360bank.ui.model.TypeAlerte;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tableau de bord RH sur une population connue : chaque KPI est celui du
 * moteur (TableauDeBordService, VivierThematiqueService,
 * CompetenceCollaborateurService) pour le trimestre affiche, et quelques
 * valeurs sont verifiees a la main. Choix du trimestre sur tous les ecrans :
 * ?trimestre=, trimestre ouvert mais vide, trimestre inconnu.
 *
 * <p>Population de T1 2026 (reglages par defaut : talent 85/85, haut potentiel
 * pot 85 et perf 75, vigilance moderee 30, elevee 60) :
 * <ul>
 *   <li>D01 95/95, D02 95/95 : talents ; comite Oui pour D01, Non pour D02 ;</li>
 *   <li>D03 80/90 : haut potentiel sans etre talent ;</li>
 *   <li>D04 60/60, engagement 20 + deux faits (sans mobilite, mobilite non
 *   traitee) : 25 + 20 + 15 = 60, vigilance ELEVEE ;</li>
 *   <li>D05 70/70, engagement 40 + formation non faite : 25 + 5 = 30, MODEREE ;</li>
 *   <li>D06 actif sans aucune donnee : pas de vigilance ;</li>
 *   <li>D07 INACTIF, note et questionnaire a 100 : hors de tous les chiffres.</li>
 * </ul>
 * D01, D02, D04 en Reseau Retail (vivier Commercial), D03, D05 en Risques
 * (vivier Risques). Deux postes critiques : PA (successeur D01), PB (aucun).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tableau-de-bord-page;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
class TableauDeBordPageIntegrationTest {

    private static final List<String> ECRANS = List.of("/", "/9box", "/viviers", "/comite-talent",
            "/postes-critiques", "/alertes");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TableauDeBordService tableauDeBordService;
    @Autowired
    private VivierThematiqueService vivierThematiqueService;
    @Autowired
    private CompetenceCollaborateurService competenceCollaborateurService;
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
    private RattachementVivierRepository rattachementRepository;
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
        parametreRepository.save(Parametre.parDefaut(t1));
        Entite retail = entiteRepository.save(new Entite("Reseau Retail", TypeEntite.DIRECTION, null));
        Entite risques = entiteRepository.save(new Entite("Risques", TypeEntite.DIRECTION, null));
        rattachementRepository.save(new RattachementVivier("Reseau Retail", VivierThematique.COMMERCIAL));
        rattachementRepository.save(new RattachementVivier("Risques", VivierThematique.RISQUES));
        competence = new Competence();
        competence.setCompetenceId("C01");
        competence.setNom("Credit");
        competence = competenceRepository.save(competence);

        Collaborateur d01 = note(collaborateur("D01", retail, StatutCollaborateur.ACTIF), "95", "95");
        Collaborateur d02 = note(collaborateur("D02", retail, StatutCollaborateur.ACTIF), "95", "95");
        Collaborateur d03 = note(collaborateur("D03", risques, StatutCollaborateur.ACTIF), "80", "90");
        Collaborateur d04 = note(collaborateur("D04", retail, StatutCollaborateur.ACTIF), "60", "60");
        Collaborateur d05 = note(collaborateur("D05", risques, StatutCollaborateur.ACTIF), "70", "70");
        collaborateur("D06", retail, StatutCollaborateur.ACTIF);
        Collaborateur d07 = note(collaborateur("D07", retail, StatutCollaborateur.INACTIF), "100", "100");

        questionnaire(d01, "80");
        questionnaire(d04, "20");
        questionnaire(d05, "40");
        questionnaire(d07, "100");
        DeclarationVigilance d04Faits = new DeclarationVigilance(d04, t1);
        d04Faits.setSansMobilite4Ans(true);
        d04Faits.setMobiliteNonTraitee(true);
        declarationRepository.save(d04Faits);
        DeclarationVigilance d05Faits = new DeclarationVigilance(d05, t1);
        d05Faits.setFormationNonFaite(true);
        declarationRepository.save(d05Faits);
        validationComiteRepository.save(new ValidationComite(d01, t1, StatutValidationComite.OUI));
        validationComiteRepository.save(new ValidationComite(d02, t1, StatutValidationComite.NON));

        // Gaps (seuil Prioritaire 2) : D01 1 -> 4 et D07 (inactif) 1 -> 5 prioritaires, D02 3 -> 4 a developper.
        skill(d01, 1, 4);
        skill(d02, 3, 4);
        skill(d07, 1, 5);

        Poste pa = poste("PA", "Directeur regional", retail);
        successeurRepository.save(new SuccesseurIdentifie(pa, d01));
        poste("PB", "Responsable risques", risques);

        calculTrimestreService.calculer(t1);
    }

    // ------------------------------------------------------------ KPI = moteur

    @Test
    @Order(1)
    void chaque_kpi_est_celui_du_moteur_pour_le_trimestre_affiche() throws Exception {
        TableauDeBordView tableau = tableau(mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(view().name("dashboard")).andReturn());
        SyntheseTableauDeBord synthese = tableauDeBordService.synthese(t1);
        ResultatViviersThematiques viviers = vivierThematiqueService.getViviersThematiques(t1);

        Map<String, String> kpis = kpis(tableau);
        assertThat(kpis).containsExactly(
                Map.entry("Collaborateurs actifs", "6"),
                Map.entry("Talents validés par le Comité", String.valueOf(synthese.nbTalentsValides())),
                Map.entry("Hauts potentiels", String.valueOf(synthese.nbHautsPotentiels())),
                Map.entry("Vivier de succession (relève)", String.valueOf(synthese.nbVivierReleve())),
                Map.entry("Viviers actifs", String.valueOf(VivierThematique.values().length
                        - (int) java.util.Arrays.stream(VivierThematique.values())
                        .filter(v -> viviers.membresDe(v).isEmpty()).count())),
                Map.entry("Postes critiques", String.valueOf(synthese.nbPostesCritiques())),
                Map.entry("Couverture succession",
                        synthese.tauxCouverture().stripTrailingZeros().toPlainString() + " %"),
                Map.entry("Successeurs Ready Now", String.valueOf(synthese.nbSuccessionsReadyNow())),
                Map.entry("Postes critiques sans successeur", String.valueOf(synthese.nbPostesSansSuccesseur())),
                Map.entry("Postes critiques en alerte", String.valueOf(synthese.nbAlertesPostesCritiques())),
                Map.entry("À risque (vigilance modérée ou élevée)", String.valueOf(synthese.nbARisque())),
                Map.entry("Compétences en gap prioritaire",
                        String.valueOf(competenceCollaborateurService.compterGapsPrioritaires(t1))),
                Map.entry("Engagement moyen /100 (3 réponses)", "46.67"));

        // Les memes chiffres, poses a la main sur cette population.
        assertThat(kpis).containsEntry("Talents validés par le Comité", "1")
                .containsEntry("Hauts potentiels", "3")
                .containsEntry("Vivier de succession (relève)", "3")
                .containsEntry("Viviers actifs", "2")
                .containsEntry("Postes critiques", "2")
                .containsEntry("Couverture succession", "50 %")
                .containsEntry("Postes critiques sans successeur", "1")
                .containsEntry("À risque (vigilance modérée ou élevée)", "2")
                .containsEntry("Compétences en gap prioritaire", "1");
        assertThat(tableau.erreur()).isNull();
        assertThat(tableau.trimestreLibelle()).isEqualTo("T1 2026");
    }

    @Test
    @Order(2)
    void la_matrice_la_vigilance_les_alertes_et_les_viviers_sont_ceux_du_moteur() throws Exception {
        TableauDeBordView tableau = tableau(mockMvc.perform(get("/")).andReturn());
        SyntheseTableauDeBord synthese = tableauDeBordService.synthese(t1);

        assertThat(tableau.neufBox()).hasSize(9);
        assertThat(tableau.neufBox().stream().collect(Collectors.toMap(CaseTableau::libelle, CaseTableau::nombre)))
                .isEqualTo(synthese.repartition9Box());
        assertThat(tableau.neufBox().get(0).niveauPerformance()).isEqualTo(3);
        assertThat(tableau.neufBox().get(0).niveauPotentiel()).isEqualTo(1);
        assertThat(tableau.nbPlaces9Box()).isEqualTo(5);
        assertThat(tableau.nbNonPlaces9Box()).isZero();

        assertThat(tableau.vigilance()).extracting(TableauDeBordView.CompteNiveau::nombre).containsExactly(
                synthese.vigilanceParNiveau().get(NiveauVigilance.FAIBLE),
                synthese.vigilanceParNiveau().get(NiveauVigilance.MODEREE),
                synthese.vigilanceParNiveau().get(NiveauVigilance.ELEVEE)).containsExactly(3, 1, 1);
        assertThat(tableau.nbSansVigilance()).isEqualTo(1);

        // Les alertes de l'ecran Alertes (AlertesViewService), les plus graves d'abord.
        assertThat(tableau.alertes()).extracting(AlerteVue::type, AlerteVue::severite, AlerteVue::matricule)
                .containsExactly(
                        tuple(TypeAlerte.POSTE_SANS_SUCCESSEUR, SeveriteAlerte.CRITIQUE, "PB"),
                        // PA a exactement un successeur : regle "Un seul successeur", un cran sous Critique.
                        tuple(TypeAlerte.UN_SEUL_SUCCESSEUR, SeveriteAlerte.ELEVEE, "PA"),
                        tuple(TypeAlerte.VIGILANCE_ELEVEE, SeveriteAlerte.ELEVEE, "D04"),
                        tuple(TypeAlerte.EVALUATION_MANAGER_MANQUANTE, SeveriteAlerte.ELEVEE, "D06"),
                        tuple(TypeAlerte.GAPS_COMPETENCES_PRIORITAIRES, SeveriteAlerte.MOYENNE, "D01"));
        assertThat(tableau.alertes().get(0).direction()).isEqualTo("Risques");
        assertThat(tableau.alertes().get(2).message()).contains("60");
        assertThat(tableau.nbAlertes()).isEqualTo(5);

        Map<String, VivierTableau> viviers = tableau.viviers().stream()
                .collect(Collectors.toMap(VivierTableau::code, v -> v));
        assertThat(viviers.get("COMMERCIAL").effectif()).isEqualTo(3);
        assertThat(viviers.get("COMMERCIAL").performanceMoyenne()).isEqualByComparingTo("83.33");
        assertThat(viviers.get("COMMERCIAL").nbTalents()).isEqualTo(2);
        assertThat(viviers.get("COMMERCIAL").nbPostesCouverts()).isEqualTo(1);
        assertThat(viviers.get("RISQUES").effectif()).isEqualTo(2);
        assertThat(viviers.get("RISQUES").potentielMoyen()).isEqualByComparingTo("80.00");
        assertThat(viviers.get("DIGITAL").effectif()).isZero();
        assertThat(viviers.get("DIGITAL").performanceMoyenne()).isNull();
    }

    @Test
    @Order(3)
    void la_page_affiche_les_chiffres_sans_valeur_ecrite_en_dur() throws Exception {
        String page = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(page).contains("Dashboard general", "indicateurs RH - T1 2026", "Talents validés par le Comité",
                        "Matrice 9-Box", "Alertes prioritaires", "Vivier Commercial", "46.67")
                .doesNotContain("donnees mockees", "Alertes actives");
    }

    // ------------------------------------------------------------ choix du trimestre

    @Test
    @Order(4)
    void chaque_ecran_pose_le_trimestre_affiche_et_la_liste() throws Exception {
        for (String ecran : ECRANS) {
            mockMvc.perform(get(ecran))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("trimestre",
                            new OptionTrimestre("2026-1", "T1 2026", 2026, 1, true)))
                    .andExpect(model().attribute("trimestres",
                            List.of(new OptionTrimestre("2026-1", "T1 2026", 2026, 1, true))));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2031-1", "abc", "2026-9"})
    @Order(5)
    void un_trimestre_inconnu_rend_404_sur_chaque_ecran(String demande) throws Exception {
        for (String ecran : ECRANS) {
            mockMvc.perform(get(ecran).param("trimestre", demande)).andExpect(status().isNotFound());
        }
    }

    /**
     * T2 ouvert mais vide (import pas encore fait, ou en echec) : les ecrans
     * restent sur T1, qui a des donnees ; T2 reste consultable a la demande.
     */
    @Test
    @Order(6)
    void un_trimestre_plus_recent_mais_vide_ne_vide_pas_les_ecrans() throws Exception {
        trimestre(2);

        for (String ecran : ECRANS) {
            mockMvc.perform(get(ecran))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("trimestre",
                            new OptionTrimestre("2026-1", "T1 2026", 2026, 1, true)))
                    .andExpect(model().attribute("trimestres", List.of(
                            new OptionTrimestre("2026-2", "T2 2026", 2026, 2, false),
                            new OptionTrimestre("2026-1", "T1 2026", 2026, 1, true))));
        }
        assertThat(kpis(tableau(mockMvc.perform(get("/")).andReturn())))
                .containsEntry("Talents validés par le Comité", "1");
        mockMvc.perform(get("/9box")).andExpect(content().string(org.hamcrest.Matchers.containsString("PrenomD01")));

        // T2 a la demande : pas de reglages, le tableau de bord le dit sans echouer.
        TableauDeBordView t2 = tableau(mockMvc.perform(get("/").param("trimestre", "2026-2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("trimestre", new OptionTrimestre("2026-2", "T2 2026", 2026, 2, false)))
                .andReturn());
        assertThat(t2.trimestreLibelle()).isEqualTo("T2 2026");
        assertThat(t2.erreur()).contains("Aucun parametre");
        assertThat(kpis(t2)).containsOnlyKeys("Collaborateurs actifs", "Engagement moyen /100 (0 réponse)");
        assertThat(t2.neufBox()).isEmpty();
        mockMvc.perform(get("/").param("trimestre", "2026-2")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pas encore calcule")));
        mockMvc.perform(get("/9box").param("trimestre", "2026-2")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PrenomD01"))));
        // Postes critiques (ecran d'Ima) : T1 par defaut, T2 sans reglages vide sans erreur.
        mockMvc.perform(get("/postes-critiques")).andExpect(status().isOk())
                .andExpect(view().name("postes-critiques"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Responsable risques")));
        mockMvc.perform(get("/postes-critiques").param("trimestre", "2026-2")).andExpect(status().isOk())
                .andExpect(model().attribute("rows", List.of()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Aucun poste critique")));
    }

    // ------------------------------------------------------------ outils

    private static TableauDeBordView tableau(MvcResult resultat) {
        return (TableauDeBordView) resultat.getModelAndView().getModel().get("tableau");
    }

    private static Map<String, String> kpis(TableauDeBordView tableau) {
        Map<String, String> kpis = new LinkedHashMap<>();
        for (KpiCard kpi : tableau.kpis()) {
            kpis.put(kpi.getLabel(), kpi.getValue());
        }
        return kpis;
    }

    private Trimestre trimestre(int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(numero);
        trimestre.setDateReference(Trimestre.dernierJour(2026, numero));
        return trimestreRepository.save(trimestre);
    }

    private Collaborateur collaborateur(String id, Entite entite, StatutCollaborateur statut) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        collaborateur.setNom("Nom" + id);
        collaborateur.setPrenom("Prenom" + id);
        collaborateur.setDateEntree(LocalDate.of(2018, 1, 1));
        collaborateur.setEntite(entite);
        collaborateur.setStatut(statut);
        return collaborateurRepository.save(collaborateur);
    }

    private Collaborateur note(Collaborateur collaborateur, String performance, String potentiel) {
        BigDecimal p = new BigDecimal(performance);
        BigDecimal q = new BigDecimal(potentiel);
        performanceRepository.save(new Performance(collaborateur, t1, p, p, p, p, p));
        potentielRepository.save(new Potentiel(collaborateur, t1, q, q, q, q, q, q, q));
        return collaborateur;
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
