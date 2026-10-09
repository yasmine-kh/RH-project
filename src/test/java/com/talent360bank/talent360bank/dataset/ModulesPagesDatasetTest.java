package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.SyntheseCompetences;
import com.talent360bank.talent360bank.service.resultat.SyntheseVivier;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.Notifications;
import com.talent360bank.talent360bank.ui.model.SuiviCampagne;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
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
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les ecrans sur le vrai classeur (T3 2026) : chaque page repond 200 avec ses
 * donnees, et l'accueil montre chaque bloc de 00_DASHBOARD avec les memes
 * chiffres (sauf les plans de developpement, feuille 11 non importee).
 *
 * <p>Ignore sans le classeur, qui n'est pas versionne. Base en memoire a part.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:modules-pages-dataset;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(roles = "RH")
@EnabledIf("classeurPresent")
class ModulesPagesDatasetTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

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
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private com.talent360bank.talent360bank.service.VivierSyntheseService vivierSyntheseService;
    @Autowired
    private com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository questionnaireRepository;

    private LecteurXlsx classeur;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @BeforeAll
    void importerEtCalculer() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));
        assertThat(importService.importer(fichier, 2026, 3).statut()).isEqualTo(StatutImport.SUCCES);
        trimestreService.modifierDateReference(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow(),
                LocalDate.of(2026, 9, 15));
        calculTrimestreService.calculer(trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow());
        classeur = new LecteurXlsx(FICHIER);
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
    }

    private static String html(MvcResult resultat) throws Exception {
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static <T> T modele(MvcResult resultat, String nom) {
        return (T) resultat.getModelAndView().getModel().get(nom);
    }

    private int entier(int ligne, String colonne) {
        return (int) Double.parseDouble(classeur.feuille("00_DASHBOARD").get(ligne).get(colonne));
    }

    // --- Accueil = 00_DASHBOARD -------------------------------------------------------------

    @Test
    @Order(1)
    void l_accueil_montre_chaque_bloc_de_00_dashboard_avec_ses_chiffres() throws Exception {
        MvcResult resultat = page("/");
        TableauDeBordView tableau = modele(resultat, "tableau");
        TableauDeBordInteractif accueil = modele(resultat, "vue");
        Map<String, String> kpis = new HashMap<>();
        accueil.kpis().forEach(kpi -> kpis.put(kpi.code(), kpi.valeur()));

        // Bloc 1, chiffres cles (lignes 6, 10, 15), tels que les cartes de la page les affichent.
        assertThat(kpis.get("population")).isEqualTo(String.valueOf(entier(6, "A")));
        assertThat(kpis.get("postesCritiques")).isEqualTo(String.valueOf(entier(6, "C")));
        assertThat(kpis.get("talentsValides")).isEqualTo(String.valueOf(entier(6, "E")));
        assertThat(kpis.get("hautsPotentiels")).isEqualTo(String.valueOf(entier(6, "G")));
        assertThat(kpis.get("couverture")).startsWith(String.valueOf(entier(10, "A")));
        assertThat(kpis.get("readyNow")).isEqualTo(String.valueOf(entier(10, "C")));
        assertThat(kpis.get("postesSansReleve")).isEqualTo(String.valueOf(entier(10, "E")));
        assertThat(accueil.personnes().stream().filter(x -> "ELEVEE".equals(x.vigilance())).count())
                .isEqualTo(entier(15, "E"));
        assertThat(accueil.personnes().stream().filter(x -> "MODEREE".equals(x.vigilance())).count())
                .isEqualTo(entier(15, "G"));

        // Bloc 2, repartition 9-box (lignes 21 a 29).
        Map<String, Integer> attendu = new HashMap<>();
        for (int ligne = 21; ligne <= 29; ligne++) {
            attendu.put(normaliser(classeur.feuille("00_DASHBOARD").get(ligne).get("A")), entier(ligne, "B"));
        }
        assertThat(tableau.neufBox().stream()
                .collect(Collectors.toMap(c -> normaliser(c.libelle()), CaseTableau::nombre))).isEqualTo(attendu);

        // Bloc 3, postes critiques sans successeur (lignes 35 a 49).
        assertThat(accueil.alertes().stream().filter(a -> "POSTE_SANS_SUCCESSEUR".equals(a.type()))
                .map(TableauDeBordInteractif.AlerteLigne::lien).toList())
                .singleElement().asString().contains("PST13");
        String page = html(resultat);
        assertThat(page).contains("#poste-PST13");
        // Bloc 2 : chaque case de la matrice (balisage du prototype) porte son effectif.
        for (CaseTableau c : tableau.neufBox()) {
            assertThat(page).containsPattern("<span class=\"cnt\">" + c.nombre() + "</span></div><div class=\"lbl\">[^<]* "
                    + java.util.regex.Pattern.quote(c.libelle()) + "</div>");
        }
    }

    // --- nouveaux ecrans ----------------------------------------------------------------------

    @Test
    @Order(2)
    void la_liste_des_collaborateurs_filtre_trie_et_pagine() throws Exception {
        ListeCollaborateurs tous = modele(page("/collaborateurs"), "vue");
        assertThat(tous.nbTotal()).isEqualTo(100);
        assertThat(tous.lignes()).hasSize(20);
        assertThat(tous.nbPages()).isEqualTo(5);

        MvcResult talents = page("/collaborateurs?talent=true&tri=PERFORMANCE");
        ListeCollaborateurs vue = modele(talents, "vue");
        assertThat(vue.nbFiltres()).isEqualTo(10);
        assertThat(vue.lignes()).allSatisfy(l -> assertThat(l.estTalent()).isTrue());
        assertThat(vue.criteres().decroissant()).isTrue();
        assertThat(html(talents)).contains("/fiche-collaborateur?matricule=BP019");

        ListeCollaborateurs page3 = modele(page("/collaborateurs?page=3&tri=MATRICULE&ordre=asc"), "vue");
        assertThat(page3.lignes().get(0).matricule()).isEqualTo("BP041");
        assertThat(html(page("/collaborateurs?page=2"))).contains("page=3");

        ListeCollaborateurs filtre = modele(page("/collaborateurs?case=9&vivier=RELEVE&q=khatib"), "vue");
        assertThat(filtre.lignes()).extracting(ListeCollaborateurs.LigneCollaborateur::matricule).contains("BP019");
        assertThat(filtre.lignes()).allSatisfy(l -> {
            assertThat(l.neufBox().numero()).isEqualTo(9);
            assertThat(l.viviers()).extracting(ListeCollaborateurs.VivierRef::code).contains("RELEVE");
            assertThat(l.nomComplet().toLowerCase(Locale.ROOT)).contains("khatib");
        });

        // Un critere invalide : message, pas d'erreur 500.
        MvcResult invalide = page("/collaborateurs?case=12");
        assertThat((String) modele(invalide, "erreur")).contains("9-box");
    }

    @Test
    @Order(3)
    void le_dashboard_dg_les_competences_les_notifications_et_la_campagne() throws Exception {
        // Le tableau de bord DG est fusionne dans l'accueil : memes blocs, memes chiffres ; l'ancienne adresse y renvoie.
        mockMvc.perform(get("/dashboard-dg")).andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"));
        mockMvc.perform(get("/dashboard-dg?trimestre=2026-3"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/?trimestre=2026-3"));
        // Sur l'accueil interactif, le poste sans successeur (PST13) est dans la liste des alertes ; l'ordre des
        // talents proposes est verifie par CollaborateursDatasetTest.
        MvcResult dg = page("/");
        assertThat(html(dg)).contains("PST13", "id=\"liste-alertes\"");

        MvcResult competences = page("/competences?poste=PST01");
        SyntheseCompetences synthese = modele(competences, "vue");
        assertThat(synthese.nbCollaborateurs()).isEqualTo(4);
        assertThat(html(competences)).contains("id=\"cg-table\"", "Leadership");
        SyntheseCompetences toutes = modele(page("/competences"), "vue");
        assertThat(toutes.nbGapsPrioritaires()).isEqualTo(entier(10, "G"));

        Notifications notifications = modele(page("/notifications"), "vue");
        assertThat(notifications.total()).isEqualTo(23);
        assertThat(notifications.alertes()).hasSize(10);

        MvcResult campagne = page("/campagne");
        SuiviCampagne suivi = modele(campagne, "vue");
        assertThat(suivi.total().nbEvalues()).isEqualTo(100);
        assertThat(html(campagne)).contains("entite=DIR");
        String code = suivi.lignes().get(0).code();
        SuiviCampagne detail = modele(page("/campagne?entite=" + code), "vue");
        assertThat(detail.entite().code()).isEqualTo(code);
        assertThat((String) modele(page("/campagne?entite=INCONNUE"), "erreur")).contains("INCONNUE");
    }

    @Test
    @Order(4)
    void les_viviers_et_les_postes_critiques_montrent_les_donnees_du_jour() throws Exception {
        MvcResult viviers = page("/viviers");
        java.util.List<SyntheseVivier> syntheses = modele(viviers, "syntheses");
        assertThat(syntheses).extracting(SyntheseVivier::code)
                .containsExactly("COMMERCIAL", "DIGITAL", "EXPERTISE", "MANAGEMENT", "RISQUES", "RELEVE");
        assertThat(html(viviers)).contains("Gaps identifiés", "Juridique bancaire (3)");

        String postes = html(page("/postes-critiques"));
        // Tous les successeurs de PST01 (09_SUCCESSION), avec leur readiness.
        assertThat(postes).contains("BP035", "BP013", "BP019", "BP005", "Aucun écart");
    }

    // --- Accueil : indicateurs complementaires (en haut, comme le prototype) ----------------------

    @Test
    @Order(6)
    void l_accueil_montre_les_10_cartes_du_prototype_avec_les_chiffres_du_classeur() throws Exception {
        MvcResult resultat = page("/");
        TableauDeBordView tableau = modele(resultat, "tableau");
        Map<String, String> kpis = new HashMap<>();
        tableau.kpis().forEach(kpi -> kpis.put(kpi.getLabel(), kpi.getValue()));
        TableauDeBordInteractif vue = modele(resultat, "vue");
        long viviersActifs = vivierSyntheseService.synthese(trimestreRepository.findByNumeroAndAnnee(3, 2026)
                .orElseThrow()).stream().filter(v -> !v.releve() && v.effectif() > 0).count();
        Map<String, String> cartes = new java.util.LinkedHashMap<>();
        vue.kpis().forEach(k -> cartes.put(k.code(), k.valeur()));

        // Les 10 cartes du prototype, dans son ordre, avec les chiffres du moteur et de 00_DASHBOARD.
        assertThat(cartes.keySet()).containsExactly("population", "talentsValides", "hautsPotentiels",
                "viviersActifs", "postesCritiques", "couverture", "readyNow", "engagement", "postesSansReleve",
                "gapsCritiques");
        assertThat(cartes.get("population")).isEqualTo(String.valueOf(collaborateurRepository.countByStatut(
                com.talent360bank.talent360bank.entity.StatutCollaborateur.ACTIF))).isEqualTo("100");
        assertThat(cartes.get("talentsValides")).isEqualTo(String.valueOf(entier(6, "E"))).isEqualTo("8");
        assertThat(cartes.get("hautsPotentiels")).isEqualTo(String.valueOf(entier(6, "G"))).isEqualTo("21");
        assertThat(cartes.get("viviersActifs")).isEqualTo(String.valueOf(viviersActifs));
        assertThat(cartes.get("postesCritiques")).isEqualTo(String.valueOf(entier(6, "C"))).isEqualTo("15");
        assertThat(cartes.get("couverture")).isEqualTo(kpis.get("Couverture succession"))
                .startsWith(String.valueOf(entier(10, "A")));
        assertThat(cartes.get("readyNow")).isEqualTo(String.valueOf(entier(10, "C"))).isEqualTo("16");
        // Engagement : moyenne des questionnaires des actifs du trimestre, a 2 decimales.
        List<java.math.BigDecimal> scores = questionnaireRepository.findByTrimestreAvecCollaborateur(
                        trimestreRepository.findByNumeroAndAnnee(3, 2026).orElseThrow()).stream()
                .filter(q -> q.getCollaborateur().estCalculable())
                .map(com.talent360bank.talent360bank.entity.QuestionnaireEngagement::getScoreEngagement)
                .filter(java.util.Objects::nonNull).toList();
        assertThat(scores).hasSize(100);
        assertThat(cartes.get("engagement")).isEqualTo(scores.stream().reduce(java.math.BigDecimal.ZERO,
                java.math.BigDecimal::add).divide(java.math.BigDecimal.valueOf(scores.size()), 2,
                java.math.RoundingMode.HALF_UP).toPlainString());
        assertThat(cartes.get("postesSansReleve")).isEqualTo(String.valueOf(entier(10, "E"))).isEqualTo("1");
        // 08_POSTES_CRITIQUES I "ALERTE" (moins de successeurs que le minimum) = gaps critiques du prototype.
        long enAlerte = classeur.feuille("08_POSTES_CRITIQUES").values().stream()
                .filter(l -> l.get("I") != null && l.get("I").contains("ALERTE")).count();
        assertThat(cartes.get("gapsCritiques")).isEqualTo(String.valueOf(enAlerte)).isEqualTo("1");

        String html = html(resultat);
        String rangee = html.substring(html.indexOf("id=\"kpis\""), html.indexOf("id=\"tdb-filtres\""));
        assertThat(rangee).contains("👥 Population", "⭐ Talents validés", "🚀 Hauts potentiels", "🔄 Viviers actifs",
                "👔 Postes critiques", "🔗 Couverture succession", "🟢 Successions Ready Now", "❤️ Engagement (100 réponses)",
                "🔴 Postes sans relève", "🧩 Gaps critiques");
        assertThat(rangee.split("class=\"kpi[ \"]", -1)).hasSize(11);
        // Cartes en haut (comme le prototype), puis graphiques, puis listes.
        assertThat(html.indexOf("id=\"kpis\"")).isLessThan(html.indexOf("id=\"tdb-neufbox\""));
        assertThat(html.indexOf("id=\"graphe-alerte\"")).isLessThan(html.indexOf("id=\"liste-collaborateurs\""));
        // Sans filtre : 100 collaborateurs, 23 alertes, aucune puce.
        assertThat(vue.personnes()).hasSize(100);
        assertThat(vue.alertes()).hasSize(23);
        assertThat(vue.puces()).isEmpty();
        assertThat(html).contains("id=\"tdb-nb-collaborateurs\">(100)</span>", "id=\"tdb-nb-alertes\">(23)</span>");
    }

    /**
     * Les cartes retirees de l'accueil (talents proposes, talents cles, releve, a risque, alertes, campagne, postes
     * couverts) : les memes chiffres, verifies la ou ils s'affichent encore.
     */
    @Test
    @Order(6)
    void les_chiffres_des_cartes_retirees_restent_justes_sur_les_autres_pages() throws Exception {
        Map<Integer, Map<String, String>> talents = classeur.feuille("10_TALENTS");
        // Collaborateurs : talents proposes (10_TALENTS E), vivier de releve (J), talents cles (00_DASHBOARD B21),
        // a risque (vigilance moderee + elevee = E15 + G15).
        assertThat(ModulesPagesDatasetTest.<ListeCollaborateurs>modele(page("/collaborateurs?talent=true"), "vue").nbFiltres())
                .isEqualTo((int) compterOui(talents, "E")).isEqualTo(10);
        assertThat(ModulesPagesDatasetTest.<ListeCollaborateurs>modele(page("/collaborateurs?vivier=RELEVE"), "vue").nbFiltres())
                .isEqualTo((int) compterOui(talents, "J")).isEqualTo(21);
        assertThat(ModulesPagesDatasetTest.<ListeCollaborateurs>modele(page("/collaborateurs?case=9"), "vue").nbFiltres())
                .isEqualTo(entier(21, "B")).isEqualTo(10);
        int moderee = ModulesPagesDatasetTest.<ListeCollaborateurs>modele(page("/collaborateurs?vigilance=MODEREE"), "vue").nbFiltres();
        int elevee = ModulesPagesDatasetTest.<ListeCollaborateurs>modele(page("/collaborateurs?vigilance=ELEVEE"), "vue").nbFiltres();
        assertThat(moderee + elevee).isEqualTo(entier(15, "E") + entier(15, "G")).isEqualTo(45);
        // Alertes ouvertes : ecran Notifications (et liste du tableau de bord, voir ci-dessus) = 23.
        Notifications notifications = modele(page("/notifications"), "vue");
        assertThat(notifications.total()).isEqualTo(23);
        // Campagne : 100 / 100 evaluations du manager, aucune auto-evaluation importee.
        SuiviCampagne suivi = modele(page("/campagne"), "vue");
        assertThat(suivi.total().nbEvalues()).isEqualTo(suivi.total().nbActifs()).isEqualTo(100);
        assertThat(suivi.total().nbAutoEvaluations()).isZero();
        // Postes critiques couverts : postes avec au moins un successeur = C6 - E10 = 14.
        java.util.List<com.talent360bank.talent360bank.ui.model.PosteCritiqueRow> postes =
                modele(page("/postes-critiques"), "rows");
        assertThat(postes.stream().filter(r -> r.getNbSuccesseurs() > 0).count())
                .isEqualTo(entier(6, "C") - entier(10, "E")).isEqualTo(14);
    }

    // --- Accueil : filtres de l'URL --------------------------------------------------------------

    @Test
    @Order(7)
    void le_filtre_case_9_de_l_url_ne_montre_que_les_10_talents_cles() throws Exception {
        Set<String> talentsCles = classeur.feuille("04_9BOX").values().stream()
                .filter(l -> l.get("A") != null && l.get("A").matches("BP\\d+"))
                .filter(l -> l.get("H") != null && (int) Double.parseDouble(l.get("H")) == 9)
                .map(l -> l.get("A")).collect(Collectors.toSet());
        assertThat(talentsCles).hasSize(10);

        MvcResult resultat = page("/?trimestre=2026-3&case=9");
        TableauDeBordInteractif vue = modele(resultat, "vue");
        assertThat(vue.personnes()).extracting(TableauDeBordInteractif.Personne::matricule)
                .containsExactlyInAnyOrderElementsOf(talentsCles);
        String html = html(resultat);
        String liste = html.substring(html.indexOf("id=\"tdb-collaborateurs\""), html.indexOf("id=\"liste-alertes\""));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<td class=\"mono\">(BP\\d+)</td>").matcher(liste);
        Set<String> affiches = new java.util.HashSet<>();
        int lignes = 0;
        while (m.find()) {
            affiches.add(m.group(1));
            lignes++;
        }
        assertThat(lignes).isEqualTo(10);
        assertThat(affiches).isEqualTo(talentsCles);
        // Puce, carte Population, case choisie en avant, autres estompees, Reinitialiser visible.
        assertThat(html).contains("9-Box : Talent clé", "id=\"tdb-nb-collaborateurs\">(10)</span>",
                "/?trimestre=2026-3\" ", "Réinitialiser");
        assertThat(vue.kpis().get(0).valeur()).isEqualTo("10");
        assertThat(html).containsPattern("class=\"box9-cell talent selectionnee [^\"]*\"[^>]*data-case=\"9\"");
        assertThat(html).containsPattern("class=\"box9-cell[^\"]*estompee\"[^>]*data-case=\"1\"");
        // Les alertes et les viviers ne portent plus que sur ces 10 personnes (pas de poste critique).
        assertThat(vue.alertes()).allSatisfy(a -> assertThat(talentsCles).contains(a.matricule()));
        assertThat(vue.viviers()).allSatisfy(v -> assertThat(v.membres())
                .allSatisfy(p -> assertThat(talentsCles).contains(p.matricule())));
        // Cliquer la case choisie la retire : son lien revient a la vue sans filtre.
        assertThat(vue.neufBox().stream().filter(TableauDeBordInteractif.CaseCompte::selectionnee).findFirst()
                .orElseThrow().lien()).isEqualTo("/?trimestre=2026-3");
    }

    @Test
    @Order(8)
    void les_filtres_se_cumulent_et_une_valeur_inconnue_est_ignoree() throws Exception {
        TableauDeBordInteractif tout = modele(page("/"), "vue");
        TableauDeBordInteractif filtre = modele(page("/?case=9&vigilance=FAIBLE&vivier=RELEVE"), "vue");
        assertThat(filtre.puces()).extracting(TableauDeBordInteractif.Puce::parametre)
                .containsExactly("case", "vigilance", "vivier");
        assertThat(filtre.personnes()).isNotEmpty().allSatisfy(p -> {
            assertThat(p.caseNumero()).isEqualTo(9);
            assertThat(p.vigilance()).isEqualTo("FAIBLE");
            assertThat(p.viviers()).contains("RELEVE");
        });
        assertThat(filtre.personnes()).hasSize((int) tout.personnes().stream()
                .filter(p -> Integer.valueOf(9).equals(p.caseNumero()) && "FAIBLE".equals(p.vigilance())
                        && p.viviers().contains("RELEVE")).count());
        // Retirer une puce garde les autres filtres.
        assertThat(filtre.puces().get(1).lienRetrait()).isEqualTo("/?trimestre=2026-3&case=9&vivier=RELEVE");
        // Filtre entite : alertes de la direction, postes compris.
        TableauDeBordInteractif.Personne premiere = tout.personnes().get(0);
        TableauDeBordInteractif parEntite = modele(page("/?entite=" + premiere.directionCode()), "vue");
        assertThat(parEntite.personnes()).allSatisfy(p -> assertThat(p.direction()).isEqualTo(premiere.direction()));
        assertThat(parEntite.alertes()).allSatisfy(a -> assertThat(a.direction()).isEqualTo(premiere.direction()));
        // Une valeur inconnue est ignoree, sans erreur.
        TableauDeBordInteractif inconnu = modele(page("/?case=12&vigilance=X&alerte=%3Cb%3E"), "vue");
        assertThat(inconnu.puces()).isEmpty();
        assertThat(inconnu.personnes()).hasSize(100);
    }

    private static long compterOui(Map<Integer, Map<String, String>> feuille, String colonne) {
        return feuille.values().stream().filter(l -> l.get("A") != null && l.get("A").matches("BP\\d+"))
                .filter(l -> "oui".equalsIgnoreCase(l.get(colonne))).count();
    }

    // --- echappement -----------------------------------------------------------------------------

    @Test
    @Order(9)
    void les_donnees_embarquees_dans_l_accueil_sont_echappees() throws Exception {
        Collaborateur bp002 = collaborateurRepository.findById("BP002").orElseThrow();
        String nom = bp002.getNom();
        bp002.setNom("</script><script>alert(1)</script>\"'&");
        collaborateurRepository.save(bp002);
        try {
            String html = html(page("/"));
            assertThat(html).doesNotContain("<script>alert(1)</script>", "</script><script>alert");
            String donnees = html.substring(html.indexOf("data-donnees=\""));
            donnees = donnees.substring(0, donnees.indexOf("\">"));
            // JSON dans un attribut : chevrons, guillemets, apostrophes et esperluettes echappes.
            assertThat(donnees).contains("&lt;/script&gt;&lt;script&gt;alert(1)&lt;/script&gt;", "&quot;", "&#39;",
                    "&amp;").doesNotContain("<", ">");
        } finally {
            bp002.setNom(nom);
            collaborateurRepository.save(bp002);
        }
    }

    @Test
    @Order(5)
    void le_texte_des_cellules_est_echappe() throws Exception {
        Collaborateur bp001 = collaborateurRepository.findById("BP001").orElseThrow();
        bp001.setNom("<script>alert(1)</script>");
        collaborateurRepository.save(bp001);

        String page = html(page("/collaborateurs?q=BP001"));
        assertThat(page).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    /** Ecrans pas encore construits : seuls a le dire (audit, rounds 1 et 2 : le Comite Talent est fait). */
    private static final Set<String> A_CONSTRUIRE = Set.of("/engagement", "/historique");

    @Test
    @Order(4)
    void seuls_engagement_et_historique_sont_en_cours_de_developpement() throws Exception {
        String accueil = html(page("/"));
        String nav = accueil.substring(accueil.indexOf("<nav id=\"nav\">"), accueil.indexOf("</nav>"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("href=\"(/[^\"#?]*)").matcher(nav);
        java.util.LinkedHashSet<String> ecrans = new java.util.LinkedHashSet<>();
        while (m.find()) {
            ecrans.add(m.group(1));
        }
        assertThat(ecrans).hasSize(17).containsAll(A_CONSTRUIRE).contains("/comite-talent").doesNotContain("/carriere-mobilite");
        for (String ecran : ecrans) {
            String texte = html(page(ecran)).toLowerCase(Locale.ROOT);
            if (A_CONSTRUIRE.contains(ecran)) {
                assertThat(texte).as(ecran).contains("en cours de développement");
            } else {
                assertThat(texte).as(ecran).doesNotContain("en cours de développement", "en cours de developpement");
            }
        }
        // Le Talent Passport d'un collaborateur, la vue manager et la campagne non plus.
        for (String ecran : List.of("/fiche-collaborateur?matricule=BP001", "/managers/BP026", "/campagne")) {
            assertThat(html(page(ecran)).toLowerCase(Locale.ROOT)).as(ecran)
                    .doesNotContain("en cours de développement");
        }
        // Campagne : sans auto-evaluation importee, "Non importé" comme le tableau de bord.
        assertThat(html(page("/campagne"))).contains("Non importé");
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
