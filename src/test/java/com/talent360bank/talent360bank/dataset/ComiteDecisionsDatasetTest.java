package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.entity.ValidationSuccession;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.repository.ValidationSuccessionRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.VivierSyntheseService;
import com.talent360bank.talent360bank.service.enums.DecisionSuccession;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.ui.model.ComiteTalentRow;
import com.talent360bank.talent360bank.ui.model.ComiteTalentView;
import com.talent360bank.talent360bank.ui.model.ImportPageView;
import com.talent360bank.talent360bank.ui.model.SuccessionComiteRow;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Decisions du Comite Talent sur le vrai classeur (T3 2026) : chaque decision est enregistree avec sa
 * date, le compte RH connecte et son commentaire ; elle se modifie ; les chiffres "Talents valides" et
 * "Successions a valider" suivent ; la derniere revue des viviers et des postes critiques s'affiche ; un
 * nouvel import du trimestre garde les decisions de l'application et le signale.
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:comite-decisions-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(username = ComiteDecisionsDatasetTest.LOGIN, roles = "RH")
@EnabledIf("classeurPresent")
class ComiteDecisionsDatasetTest {

    static final String LOGIN = "rh.comite";
    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");
    private static final String T3 = "2026-3";
    private static final String AUJOURDHUI = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

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
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private ValidationComiteRepository validationComiteRepository;
    @Autowired
    private ValidationSuccessionRepository validationSuccessionRepository;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private VivierSyntheseService vivierSyntheseService;

    private Trimestre t3;
    /** Talent propose sans decision dans le classeur, puis decide ici. */
    private String enAttente;
    /** Talent valide "Oui" dans le classeur, puis "Non" ici : desaccord au reimport. */
    private String valideParLeClasseur;
    private SuccessionComiteRow succession;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        assertThat(importService.importer(fichier(), 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        t3 = trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow();
        trimestreService.modifierDateReference(t3, LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(t3);
        utilisateurRepository.save(new Utilisateur(LOGIN, "non-utilise", Role.RH));
    }

    private static MockMultipartFile fichier() throws Exception {
        return new MockMultipartFile("fichier", FICHIER.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(FICHIER));
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
    }

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ComiteTalentView comite() throws Exception {
        return (ComiteTalentView) page("/comite-talent?trimestre=" + T3).getModelAndView().getModel().get("vue");
    }

    private String talentsValidesComite() throws Exception {
        return comite().getKpis().get(0).getValue();
    }

    private String talentsValidesTableauDeBord() throws Exception {
        TableauDeBordInteractif vue = (TableauDeBordInteractif) page("/?trimestre=" + T3).getModelAndView()
                .getModel().get("vue");
        return vue.kpis().stream().filter(k -> k.code().equals("talentsValides")).findFirst().orElseThrow().valeur();
    }

    private ValidationComite decisionTalent(String matricule) {
        return validationComiteRepository.findByTrimestreAvecAuteur(t3).stream()
                .filter(v -> v.getCollaborateur().getIdCollaborateur().equals(matricule)).findFirst().orElseThrow();
    }

    private ValidationSuccession decisionSuccession(String poste, String matricule) {
        return validationSuccessionRepository.findByTrimestreAvecDetails(t3).stream()
                .filter(v -> v.getPoste().getPosteId().equals(poste)
                        && v.getSuccesseur().getIdCollaborateur().equals(matricule))
                .findFirst().orElseThrow();
    }

    private MvcResult deciderTalent(String matricule, String decision, String... autres) throws Exception {
        var requete = post("/comite-talent/talents").with(csrf()).param("trimestre", T3)
                .param("matricule", matricule).param("decision", decision);
        for (int i = 0; i < autres.length; i += 2) {
            requete.param(autres[i], autres[i + 1]);
        }
        return mockMvc.perform(requete).andExpect(status().is3xxRedirection()).andReturn();
    }

    private MvcResult deciderSuccession(SuccessionComiteRow s, String decision, String... autres) throws Exception {
        var requete = post("/comite-talent/successions").with(csrf()).param("trimestre", T3)
                .param("poste", s.posteId()).param("matricule", s.matricule()).param("decision", decision);
        for (int i = 0; i < autres.length; i += 2) {
            requete.param(autres[i], autres[i + 1]);
        }
        return mockMvc.perform(requete).andExpect(status().is3xxRedirection()).andReturn();
    }

    // ------------------------------------------------------------ page

    @Test
    @Order(1)
    void la_page_a_des_boutons_actifs_et_compte_les_successions_a_valider() throws Exception {
        String page = html(page("/comite-talent?trimestre=" + T3));
        assertThat(page.toLowerCase()).doesNotContain("en cours de développement", "en cours de developpement");
        String section = page.substring(page.indexOf("id=\"view-comite\""));
        assertThat(section).doesNotContain(" disabled", "indispo", "data-successions-trimestre");
        assertThat(page)
                .contains("name=\"_csrf\"", "action=\"/comite-talent/talents\"",
                        "action=\"/comite-talent/successions\"", "🟠 Valider avec plan", "🔵 Maintenir en vivier");

        ComiteTalentView vue = comite();
        // 10 talents proposes, 8 valides "Oui" par le classeur (00_DASHBOARD E6), 2 sans decision.
        assertThat(vue.getNbProposes()).isEqualTo(10);
        assertThat(talentsValidesComite()).isEqualTo("8");
        List<ComiteTalentRow> attente = vue.getRows().stream().filter(ComiteTalentRow::isEnAttente).toList();
        assertThat(attente).hasSize(2);
        enAttente = attente.get(0).getMatricule();
        valideParLeClasseur = vue.getRows().stream().filter(r -> r.getStatut().equals("OUI")).findFirst()
                .orElseThrow().getMatricule();
        // Aucune decision de succession encore : toutes a valider ; la carte n'affiche plus "—".
        assertThat(vue.getSuccessions()).isNotEmpty();
        assertThat(vue.getNbSuccessionsEnAttente()).isEqualTo(vue.getSuccessions().size());
        assertThat(page).containsPattern("id=\"kpi-successions\"><div class=\"val\">" + vue.getSuccessions().size()
                + "</div><div class=\"lbl\">Successions à valider");
        succession = vue.getSuccessions().get(0);
    }

    // ------------------------------------------------------------ talents

    @Test
    @Order(2)
    void valider_un_talent_enregistre_la_date_le_compte_et_le_commentaire() throws Exception {
        MvcResult reponse = deciderTalent(enAttente, "OUI", "commentaire", "  Revue du comité T3  ");
        assertThat(reponse.getResponse().getRedirectedUrl()).isEqualTo("/comite-talent?trimestre=2026-3#talents");
        assertThat((String) reponse.getFlashMap().get("succesDecision")).startsWith("Décision enregistrée : ")
                .endsWith(" — Oui.");

        ValidationComite decision = decisionTalent(enAttente);
        assertThat(decision.getStatut()).isEqualTo(StatutValidationComite.OUI);
        assertThat(decision.getDateDecision().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(decision.getUtilisateur().getLogin()).isEqualTo(LOGIN);
        assertThat(decision.getCommentaire()).isEqualTo("Revue du comité T3");

        // Tous les "Talents valides" suivent : Comite, tableau de bord, badge de la liste.
        assertThat(talentsValidesComite()).isEqualTo("9");
        assertThat(talentsValidesTableauDeBord()).isEqualTo("9");
        assertThat(html(page("/collaborateurs?trimestre=" + T3 + "&q=" + enAttente))).contains("Talent validé");
        // La decision apparait avec son auteur et sa date, et peut etre modifiee.
        String page = html(page("/comite-talent?trimestre=" + T3));
        assertThat(page).contains("décidé le " + AUJOURDHUI, "par " + LOGIN, "Revue du comité T3",
                "modifier=" + enAttente);
    }

    @Test
    @Order(3)
    void ne_pas_retenir_demande_une_confirmation_et_une_decision_se_modifie() throws Exception {
        // Sans confirmation : rien n'est enregistre, la page demande de confirmer.
        MvcResult sansConfirmation = deciderTalent(enAttente, "NON", "commentaire", "à revoir");
        assertThat(sansConfirmation.getResponse().getRedirectedUrl())
                .isEqualTo("/comite-talent?trimestre=2026-3&confirmer=" + enAttente + "#talent-" + enAttente);
        assertThat(decisionTalent(enAttente).getStatut()).isEqualTo(StatutValidationComite.OUI);
        String confirmation = html(mockMvc.perform(get("/comite-talent?trimestre=" + T3 + "&confirmer=" + enAttente)
                .flashAttr("commentaireDecision", "à revoir")).andReturn());
        assertThat(confirmation).contains("Confirmer : ne pas retenir", "value=\"à revoir\"");

        // Confirme : la decision change, les compteurs aussi.
        deciderTalent(enAttente, "NON", "confirme", "oui");
        assertThat(decisionTalent(enAttente).getStatut()).isEqualTo(StatutValidationComite.NON);
        assertThat(decisionTalent(enAttente).getCommentaire()).isNull();
        assertThat(talentsValidesComite()).isEqualTo("8");
        assertThat(talentsValidesTableauDeBord()).isEqualTo("8");

        // "Modifier" rouvre les boutons de la ligne ; la decision se change de nouveau.
        String modifier = html(page("/comite-talent?trimestre=" + T3 + "&modifier=" + enAttente));
        String ligne = modifier.substring(modifier.indexOf("id=\"talent-" + enAttente + "\""));
        assertThat(ligne.substring(0, ligne.indexOf("</form>"))).contains("🟢 Valider", "⚪ Réévaluer");
        deciderTalent(enAttente, "OUI");
        assertThat(decisionTalent(enAttente).getStatut()).isEqualTo(StatutValidationComite.OUI);
        assertThat(talentsValidesComite()).isEqualTo("9");

        // Un talent valide "Oui" par le classeur, non retenu ici (desaccord verifie au reimport).
        deciderTalent(valideParLeClasseur, "NON", "confirme", "oui");
        assertThat(decisionTalent(valideParLeClasseur).getStatut()).isEqualTo(StatutValidationComite.NON);
        assertThat(talentsValidesComite()).isEqualTo("8");
        assertThat(talentsValidesTableauDeBord()).isEqualTo("8");
    }

    // ------------------------------------------------------------ successions

    @Test
    @Order(4)
    void chaque_decision_de_succession_est_enregistree_et_la_carte_compte_les_restantes() throws Exception {
        int total = comite().getSuccessions().size();

        MvcResult reponse = deciderSuccession(succession, "VALIDER_AVEC_PLAN", "commentaire", "Plan de formation");
        assertThat(reponse.getResponse().getRedirectedUrl()).isEqualTo("/comite-talent?trimestre=2026-3#successions");
        assertThat((String) reponse.getFlashMap().get("succesDecision")).endsWith(" — Validé avec plan.");
        ValidationSuccession decision = decisionSuccession(succession.posteId(), succession.matricule());
        assertThat(decision.getDecision()).isEqualTo(DecisionSuccession.VALIDER_AVEC_PLAN);
        assertThat(decision.getDateDecision().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(decision.getUtilisateur().getLogin()).isEqualTo(LOGIN);
        assertThat(decision.getCommentaire()).isEqualTo("Plan de formation");
        assertThat(comite().getNbSuccessionsEnAttente()).isEqualTo(total - 1);
        assertThat(html(page("/comite-talent?trimestre=" + T3))).contains("Validé avec plan",
                "modifierSuccession=" + succession.posteId() + ":" + succession.matricule());

        // "Ne pas retenir" sans confirmation : rien ne change.
        MvcResult sansConfirmation = deciderSuccession(succession, "NE_PAS_RETENIR");
        assertThat(sansConfirmation.getResponse().getRedirectedUrl()).contains("confirmerSuccession="
                + succession.posteId() + ":" + succession.matricule());
        assertThat(decisionSuccession(succession.posteId(), succession.matricule()).getDecision())
                .isEqualTo(DecisionSuccession.VALIDER_AVEC_PLAN);

        // Reevaluer : de nouveau a valider. Puis chacune des autres decisions.
        deciderSuccession(succession, "REEVALUER");
        assertThat(comite().getNbSuccessionsEnAttente()).isEqualTo(total);
        deciderSuccession(succession, "MAINTENIR_EN_VIVIER");
        assertThat(decisionSuccession(succession.posteId(), succession.matricule()).getDecision())
                .isEqualTo(DecisionSuccession.MAINTENIR_EN_VIVIER);
        deciderSuccession(succession, "NE_PAS_RETENIR", "confirme", "oui");
        assertThat(decisionSuccession(succession.posteId(), succession.matricule()).getDecision())
                .isEqualTo(DecisionSuccession.NE_PAS_RETENIR);
        deciderSuccession(succession, "VALIDER");
        assertThat(decisionSuccession(succession.posteId(), succession.matricule()).getDecision())
                .isEqualTo(DecisionSuccession.VALIDER);
        // Une seule ligne par (trimestre, poste, successeur), quelle que soit le nombre de modifications.
        assertThat(validationSuccessionRepository.findByTrimestreAvecDetails(t3)).hasSize(1);
        assertThat(comite().getNbSuccessionsEnAttente()).isEqualTo(total - 1);
    }

    // ------------------------------------------------------------ derniere revue

    @Test
    @Order(5)
    void la_derniere_revue_est_la_date_de_la_derniere_decision() throws Exception {
        // Postes critiques : celui de la succession decidee porte la date, les autres "—".
        String postes = html(page("/postes-critiques?trimestre=" + T3));
        String carte = postes.substring(postes.indexOf("id=\"poste-" + succession.posteId() + "\""));
        int fin = carte.indexOf("class=\"succ-card\"");
        assertThat(fin < 0 ? carte : carte.substring(0, fin)).contains("Dernière revue : " + AUJOURDHUI);
        int nbPostes = postes.split("class=\"succ-card\"", -1).length - 1;
        assertThat(postes.split("Dernière revue : —", -1).length - 1).isEqualTo(nbPostes - 1);

        // Viviers : la date pour ceux qui ont un membre decide, "—" pour les autres.
        Map<String, Set<String>> membres = vivierSyntheseService.syntheseEtMembres(t3).membres();
        Set<String> decides = new java.util.HashSet<>(List.of(enAttente, valideParLeClasseur, succession.matricule()));
        @SuppressWarnings("unchecked")
        Map<String, LocalDate> revues = (Map<String, LocalDate>) page("/viviers?trimestre=" + T3).getModelAndView()
                .getModel().get("revues");
        membres.forEach((code, matricules) -> {
            if (matricules.stream().anyMatch(decides::contains)) {
                assertThat(revues).as(code).containsEntry(code, LocalDate.now());
            } else {
                assertThat(revues).as(code).doesNotContainKey(code);
            }
        });
        assertThat(revues).isNotEmpty();
        assertThat(html(page("/viviers?trimestre=" + T3))).contains("<span>Dernière revue</span><b>" + AUJOURDHUI + "</b>");
    }

    // ------------------------------------------------------------ reimport

    @Test
    @Order(6)
    void reimporter_le_trimestre_garde_les_decisions_de_l_application_et_le_signale() throws Exception {
        MvcResult resultat = mockMvc.perform(multipart("/import").file(fichier()).param("annee", "2026")
                .param("numero", "3").param("calcul", "true").with(csrf())).andExpect(status().isOk()).andReturn();
        ImportPageView vue = (ImportPageView) resultat.getModelAndView().getModel().get("vue");
        assertThat(vue.rapport().statut()).isEqualTo("SUCCES");

        // Les decisions de l'application sont intactes, avec leur date et leur auteur.
        assertThat(decisionTalent(enAttente).getStatut()).isEqualTo(StatutValidationComite.OUI);
        assertThat(decisionTalent(valideParLeClasseur).getStatut()).isEqualTo(StatutValidationComite.NON);
        assertThat(decisionTalent(valideParLeClasseur).getUtilisateur().getLogin()).isEqualTo(LOGIN);
        assertThat(decisionSuccession(succession.posteId(), succession.matricule()).getDecision())
                .isEqualTo(DecisionSuccession.VALIDER);
        assertThat(talentsValidesComite()).isEqualTo("8");

        // Le classeur dit "Oui" pour ce talent : desaccord signale dans le bilan, la decision "Non" gardee.
        assertThat(vue.rapport().decisionsConservees()).anySatisfy(d -> {
            assertThat(d.feuille()).isEqualTo("10_TALENTS");
            assertThat(d.message()).isEqualTo(valideParLeClasseur + " : le classeur indique « Oui », la décision "
                    + "saisie dans l'application est conservée (« Non », le " + AUJOURDHUI + ").");
        });
        assertThat(html(resultat)).contains("Décisions du Comité conservées", valideParLeClasseur);
        // Pas un ecart du classeur : le statut reste Reussi et rien n'est compte comme erreur.
        assertThat(vue.rapport().nbErreurs()).isZero();
    }

    // ------------------------------------------------------------ refus

    @Test
    @Order(7)
    void une_decision_impossible_est_refusee_avec_un_message_et_rien_n_est_ecrit() throws Exception {
        long avant = validationComiteRepository.count();
        String sansTalent = collaborateurRepository.findAll().stream().map(c -> c.getIdCollaborateur())
                .filter(m -> comiteMatricules().stream().noneMatch(m::equals)).findFirst().orElseThrow();
        MvcResult refus = deciderTalent(sansTalent, "OUI");
        assertThat((String) refus.getFlashMap().get("erreurDecision"))
                .isEqualTo("Décision refusée : " + sansTalent + " n'est pas un talent proposé pour ce trimestre.");
        assertThat(validationComiteRepository.count()).isEqualTo(avant);

        assertThat((String) deciderTalent(enAttente, "PEUT_ETRE").getFlashMap().get("erreurDecision"))
                .isEqualTo("Décision inconnue : rien n'a été enregistré.");
        assertThat((String) deciderTalent(enAttente, "OUI", "commentaire", "x".repeat(501)).getFlashMap()
                .get("erreurDecision")).isEqualTo("Le commentaire dépasse 500 caractères : rien n'a été enregistré.");
        assertThat((String) deciderSuccession(new SuccessionComiteRow(succession.posteId(), null, "INCONNU", null,
                null, null, null, null, true, null), "VALIDER").getFlashMap().get("erreurDecision"))
                .contains("n'est pas un successeur évalué du poste " + succession.posteId());

        // Sans jeton CSRF : refuse ; trimestre inconnu : 404.
        mockMvc.perform(post("/comite-talent/talents").param("trimestre", T3).param("matricule", enAttente)
                .param("decision", "OUI")).andExpect(status().isForbidden());
        mockMvc.perform(post("/comite-talent/talents").with(csrf()).param("trimestre", "2031-1")
                .param("matricule", enAttente).param("decision", "OUI")).andExpect(status().isNotFound());
    }

    private List<String> comiteMatricules() {
        try {
            return comite().getRows().stream().map(ComiteTalentRow::getMatricule).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Order(8)
    void sans_connexion_aucune_decision_n_est_possible() throws Exception {
        mockMvc.perform(post("/comite-talent/talents").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .anonymous())
                        .param("trimestre", T3).param("matricule", enAttente).param("decision", "NON")
                        .param("confirme", "oui"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrlPattern("**/login"));
        assertThat(decisionTalent(enAttente).getStatut()).isEqualTo(StatutValidationComite.OUI);
    }
}
