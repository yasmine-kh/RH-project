package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.config.Matrice9BoxInitializer;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.FaitsVigilance;
import com.talent360bank.talent360bank.service.FaitsVigilanceEnMemoire;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.SuccesseursIdentifiesEnMemoire;
import com.talent360bank.talent360bank.service.SuccessionService;
import com.talent360bank.talent360bank.service.TableauDeBordService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.ValidationsComiteEnMemoire;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.ViviersThematiquesEnMemoire;
import com.talent360bank.talent360bank.service.enums.NiveauCouverture;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.StatutGapCompetence;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rejoue un echantillon d'employes du jeu de donnees Excel dans le moteur et
 * compare nos resultats aux valeurs deja calculees par le classeur.
 *
 * <p>Le fichier est gitignore : sans lui, toute la classe est ignoree.
 *
 * <p>Les reglages sont ceux de {@link Parametre#parDefaut}, pas ceux lus dans
 * 00_PARAMETRES : un ecart revele donc aussi une valeur par defaut qui aurait
 * derive de la specification. Les libelles sont compares sans accents ni
 * casse, le classeur n'en portant pas.
 */
class DatasetExcelComparaisonTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    /** Date de reference de l'anciennete dans 01_COLLABORATEURS (DATE(2026,9,15)). */
    private static final LocalDate DATE_REFERENCE_CLASSEUR = LocalDate.of(2026, 9, 15);

    private static final int CANDIDATS_SUCCESSION = 6;
    private static final int PREMIERS_COLLABORATEURS = 3;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static LecteurXlsx classeur;
    private static List<String> echantillon;

    private static Parametre parametre;
    private static CalculService calculService;
    private static NeufBoxService neufBoxService;
    private static TalentService talentService;
    private static SuccessionService successionService;

    private record Ecart(String employe, String champ, String attendu, String obtenu) {
        @Override
        public String toString() {
            return employe + " | " + champ + " | attendu " + attendu + " | obtenu " + obtenu;
        }
    }

    @BeforeAll
    static void charger() throws Exception {
        assumeTrue(Files.exists(FICHIER), "Jeu de donnees absent (" + FICHIER + "), comparaison ignoree");
        classeur = new LecteurXlsx(FICHIER);

        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(3);
        trimestre.setAnnee(2026);
        parametre = Parametre.parDefaut(trimestre);

        calculService = new CalculService(mock(ParametreRepository.class),
                mock(PerformanceRepository.class), mock(PotentielRepository.class));
        neufBoxService = new NeufBoxService(matriceDeReference(), mock(ScoreRepository.class), calculService);
        talentService = new TalentService(mock(ScoreRepository.class), calculService);
        successionService = new SuccessionService(mock(PosteRepository.class), mock(ScoreRepository.class),
                mock(PotentielRepository.class), mock(EmployeeSkillRepository.class), calculService);

        echantillon = choisirEchantillon();
    }

    // --- comparaisons --------------------------------------------------------

    @Test
    void les_scores_de_performance_et_de_potentiel_sont_ceux_du_classeur() {
        List<Ecart> ecarts = new ArrayList<>();
        for (String id : echantillon) {
            comparerNombre(ecarts, id, "02_PERFORMANCE.Score Performance",
                    parEmploye("02_PERFORMANCE").get(id).get("I"), scorePerformance(id));
            comparerNombre(ecarts, id, "03_POTENTIEL.Score Potentiel",
                    parEmploye("03_POTENTIEL").get(id).get("K"), scorePotentiel(id));
        }
        verifier(ecarts);
    }

    @Test
    void la_case_9_box_est_celle_du_classeur() {
        List<Ecart> ecarts = new ArrayList<>();
        for (String id : echantillon) {
            Matrice9Box case9Box = neufBoxService.placer(scorePerformance(id), scorePotentiel(id), parametre);
            comparerLibelle(ecarts, id, "04_9BOX.Categorie Talent",
                    parEmploye("04_9BOX").get(id).get("I"), case9Box.getCategorie());
        }
        verifier(ecarts);
    }

    /**
     * Categories individuelles sur toute la population : 02_PERFORMANCE J
     * (00_PARAMETRES section 4) et 03_POTENTIEL L (seuils de l'axe potentiel
     * de la 9-box, lignes 26 et 27).
     */
    @Test
    void les_categories_de_performance_et_de_potentiel_sont_celles_du_classeur() {
        List<Ecart> ecarts = new ArrayList<>();
        for (String id : parEmploye("02_PERFORMANCE").keySet()) {
            comparerLibelle(ecarts, id, "02_PERFORMANCE.Categorie Performance",
                    parEmploye("02_PERFORMANCE").get(id).get("J"),
                    calculService.categoriePerformance(scorePerformance(id),
                            parametre.getSeuilsCategoriePerformance()).getLibelle());
            comparerLibelle(ecarts, id, "03_POTENTIEL.Categorie Potentiel",
                    parEmploye("03_POTENTIEL").get(id).get("L"),
                    neufBoxService.categoriePotentiel(scorePotentiel(id), parametre).getLibelle());
        }
        verifier(ecarts);
        assertThat(parEmploye("02_PERFORMANCE")).hasSize(100);
    }

    /**
     * Statut de gap (06_EMPLOYEE_SKILLS H) sur toutes les lignes : niveaux
     * actuel et cible en E et F, seuil de Prioritaire de 00_PARAMETRES
     * ligne 77 (2 par defaut).
     */
    @Test
    void le_statut_de_gap_de_chaque_competence_est_celui_du_classeur() {
        List<Ecart> ecarts = new ArrayList<>();
        int lignes = 0;
        for (Map<String, String> ligne : classeur.feuille("06_EMPLOYEE_SKILLS").values()) {
            if (!ligne.getOrDefault("B", "").matches("BP\\d+")) {
                continue;
            }
            lignes++;
            StatutGapCompetence statut = calculService.statutGap(entier(ligne.get("E")), entier(ligne.get("F")),
                    parametre.getSeuilsGapCompetence());
            comparerLibelle(ecarts, ligne.get("A"), "06_EMPLOYEE_SKILLS.Statut Gap",
                    ligne.get("H"), statut == null ? null : statut.getLibelle());
        }
        verifier(ecarts);
        assertThat(lignes).isEqualTo(2500);
    }

    /**
     * Sur toute la population et non l'echantillon : ces statuts ne demandent
     * que les deux scores, et l'echantillon ne contient aucun haut potentiel
     * qui ne soit pas aussi talent (11 dans le classeur).
     */
    @Test
    void les_statuts_talent_haut_potentiel_et_releve_sont_ceux_du_classeur() {
        List<Ecart> ecarts = new ArrayList<>();
        for (String id : parEmploye("10_TALENTS").keySet()) {
            Map<String, String> ligne = parEmploye("10_TALENTS").get(id);
            boolean talent = talentService.estTalent(score(id), parametre);
            boolean hautPotentiel = talentService.estHautPotentiel(score(id), parametre);
            comparerLibelle(ecarts, id, "10_TALENTS.Talent propose (auto)", ligne.get("E"), ouiNon(talent));
            comparerLibelle(ecarts, id, "10_TALENTS.Haut Potentiel propose (auto)",
                    ligne.get("F"), ouiNon(hautPotentiel));
            comparerLibelle(ecarts, id, "10_TALENTS.Dans le vivier de releve",
                    ligne.get("J"), ouiNon(talent || hautPotentiel));
        }
        verifier(ecarts);
    }

    @Test
    void le_matching_succession_est_celui_du_classeur() {
        Map<String, Competence> referentiel = referentielParNom();
        Map<String, Poste> postes = postes(referentiel);

        List<Ecart> ecarts = new ArrayList<>();
        for (Map<String, String> ligne : classeur.feuille("09_SUCCESSION").values()) {
            String posteId = ligne.getOrDefault("A", "");
            String id = ligne.getOrDefault("B", "");
            if (!posteId.matches("PST\\d+") || !echantillon.contains(id)) {
                continue;
            }
            Employe candidat = employe(id);
            ResultatMatching resultat = successionService.evaluer(candidat, postes.get(posteId),
                    score(id), potentiel(id), competences(candidat, referentiel), parametre);

            String prefixe = "09_SUCCESSION " + posteId + ".";
            comparerNombre(ecarts, id, prefixe + "Sc. Competences", ligne.get("E"), resultat.detail().competences());
            comparerNombre(ecarts, id, prefixe + "Sc. Experience", ligne.get("H"), resultat.detail().experience());
            comparerNombre(ecarts, id, prefixe + "Score Matching", ligne.get("K"), resultat.scoreMatching());
            comparerLibelle(ecarts, id, prefixe + "Readiness", ligne.get("L"), libelleClasseur(resultat.readiness()));
        }
        verifier(ecarts);
    }

    /**
     * Sur tous les postes et tous les successeurs de 09_SUCCESSION, par le
     * chemin complet du service (depots simules a partir du classeur) : postes
     * critiques retenus, nombre de successeurs, meilleur matching, couverture,
     * puis les chiffres du tableau de bord.
     */
    @Test
    void la_couverture_des_postes_critiques_est_celle_du_classeur() {
        PosteCritiqueService service = posteCritiqueServiceSurLeClasseur();
        Trimestre trimestre = parametre.getTrimestre();

        List<CouverturePoste> couvertures = service.listerPostesCritiques(trimestre);

        Map<String, Map<String, String>> attendus = parPoste("08_POSTES_CRITIQUES");
        assertThat(couvertures).extracting(c -> c.poste().getPosteId())
                .containsExactlyElementsOf(attendus.keySet());

        List<Ecart> ecarts = new ArrayList<>();
        for (CouverturePoste couverture : couvertures) {
            String posteId = couverture.poste().getPosteId();
            Map<String, String> ligne = attendus.get(posteId);
            comparerNombre(ecarts, posteId, "08_POSTES_CRITIQUES.Nb Successeurs",
                    ligne.get("G"), BigDecimal.valueOf(couverture.nbSuccesseurs()));
            // Le classeur rend 0 sans successeur (IFERROR(MAXIFS(...), 0)), nous null.
            comparerNombre(ecarts, posteId, "08_POSTES_CRITIQUES.Meilleur Matching", ligne.get("H"),
                    couverture.meilleurMatching() == null ? BigDecimal.ZERO : couverture.meilleurMatching());
            comparerLibelle(ecarts, posteId, "08_POSTES_CRITIQUES.Couverture",
                    ligne.get("I"), libelleClasseur(couverture.niveau()));
        }
        verifier(ecarts);

        // Chiffres cles de 00_DASHBOARD.
        assertThat(couvertures).hasSize(15);
        assertThat(service.detecterAlertes(trimestre)).extracting(c -> c.poste().getPosteId())
                .containsExactly("PST13");
        assertThat(service.tauxCouverture(couvertures).setScale(0, RoundingMode.HALF_UP))
                .isEqualByComparingTo("93");
    }

    /**
     * Talent valide (10_TALENTS!H = talent propose ET decision du comite en G)
     * sur toute la population, puis le chiffre cle de 00_DASHBOARD par le
     * chemin complet du tableau de bord.
     */
    @Test
    void les_talents_valides_par_le_comite_sont_ceux_du_classeur() {
        ValidationsComiteEnMemoire decisions = new ValidationsComiteEnMemoire();
        for (Map.Entry<String, Map<String, String>> ligne : parEmploye("10_TALENTS").entrySet()) {
            decisions.decider(ligne.getKey(), statutComite(ligne.getValue().get("G")));
        }
        ValidationComiteService comite = new ValidationComiteService(talentService, decisions);
        Trimestre trimestre = parametre.getTrimestre();

        List<Ecart> ecarts = new ArrayList<>();
        for (String id : parEmploye("10_TALENTS").keySet()) {
            boolean valide = comite.estTalentValide(talentService.estTalent(score(id), parametre),
                    decisions.statut(id, trimestre));
            comparerLibelle(ecarts, id, "10_TALENTS.Talent valide",
                    parEmploye("10_TALENTS").get(id).get("H"), ouiNon(valide));
        }
        verifier(ecarts);

        // 00_DASHBOARD E6 : Talents valides (Comite) = COUNTIF(10_TALENTS!H, "Oui").
        List<Score> scores = parEmploye("01_COLLABORATEURS").keySet().stream()
                .map(DatasetExcelComparaisonTest::score).toList();
        ScoreRepository scoreRepository = mock(ScoreRepository.class);
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(scores);
        ParametreRepository parametreRepository = mock(ParametreRepository.class);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));
        TalentService talents = new TalentService(scoreRepository, new CalculService(parametreRepository,
                mock(PerformanceRepository.class), mock(PotentielRepository.class)));
        TableauDeBordService tableauDeBord = new TableauDeBordService(talents,
                new ValidationComiteService(talents, decisions), mock(VigilanceService.class),
                mock(PosteCritiqueService.class), scoreRepository, mock(Matrice9BoxRepository.class));

        assertThat(tableauDeBord.synthese(trimestre).nbTalentsValides())
                .isEqualTo(Integer.parseInt(classeur.feuille("00_DASHBOARD").get(6).get("E")))
                .isEqualTo(8);
    }

    /**
     * Vivier thematique (10_TALENTS!I, une valeur saisie) sur toute la
     * population. Le rattachement direction / vivier est ecrit ici a la main,
     * tel qu'il sera saisi dans le referentiel, et non relu dans la colonne
     * comparee : sinon la comparaison ne prouverait rien.
     */
    @Test
    void le_vivier_thematique_est_celui_du_classeur() {
        VivierThematiqueService service = new VivierThematiqueService(talentService, calculService,
                mock(ScoreRepository.class), new ViviersThematiquesEnMemoire()
                .rattacher(VivierThematique.COMMERCIAL, "Reseau Retail", "Corporate Banking")
                .rattacher(VivierThematique.DIGITAL, "IT & Digital", "Marketing & Communication")
                .rattacher(VivierThematique.EXPERTISE, "Finance", "Audit Interne", "Juridique")
                .rattacher(VivierThematique.MANAGEMENT, "RH")
                .rattacher(VivierThematique.RISQUES, "Risques", "Conformite"));

        List<Ecart> ecarts = new ArrayList<>();
        for (String id : parEmploye("10_TALENTS").keySet()) {
            comparerLibelle(ecarts, id, "10_TALENTS.Vivier thematique", parEmploye("10_TALENTS").get(id).get("I"),
                    service.vivierDe(employe(id)).map(VivierThematique::getLibelle).orElse("(non classe)"));
        }
        verifier(ecarts);
    }

    /**
     * Indice et niveau de vigilance (12_VIGILANCE L et M) sur toute la
     * population, par le chemin complet du lot du trimestre : engagement lu en
     * D et compare au seuil du moteur, faits importes lus en F a K. Un seul
     * trimestre dans le classeur : la baisse de performance vient donc du
     * drapeau importe, faute d'historique.
     */
    @Test
    void l_indice_et_le_niveau_de_vigilance_sont_ceux_du_classeur() {
        Trimestre trimestre = parametre.getTrimestre();
        Map<String, Map<String, String>> lignes = parEmploye("12_VIGILANCE");

        List<Score> scores = new ArrayList<>();
        List<QuestionnaireEngagement> engagements = new ArrayList<>();
        FaitsVigilanceEnMemoire faits = new FaitsVigilanceEnMemoire();
        for (Map.Entry<String, Map<String, String>> entree : lignes.entrySet()) {
            String id = entree.getKey();
            Map<String, String> ligne = entree.getValue();
            Score score = score(id);
            scores.add(score);

            QuestionnaireEngagement engagement = new QuestionnaireEngagement();
            engagement.setEmploye(score.getEmploye());
            engagement.setTrimestre(trimestre);
            engagement.setScoreEngagement(nombre(ligne.get("D")));
            engagements.add(engagement);

            faits.declarer(id, FaitsVigilance.builder()
                    .sansMobilite4Ans(drapeau(ligne.get("F")))
                    .mobiliteNonTraitee(drapeau(ligne.get("G")))
                    .sansDeveloppementRecent(drapeau(ligne.get("H")))
                    .baissePerformance(drapeau(ligne.get("I")))
                    .faibleReconnaissance(drapeau(ligne.get("J")))
                    .formationNonFaite(drapeau(ligne.get("K")))
                    .build());
        }

        ScoreRepository scoreRepository = mock(ScoreRepository.class);
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(scores);
        QuestionnaireEngagementRepository questionnaireRepository = mock(QuestionnaireEngagementRepository.class);
        when(questionnaireRepository.findByTrimestreAvecEmploye(any())).thenReturn(engagements);
        TrimestreRepository trimestreRepository = mock(TrimestreRepository.class);
        when(trimestreRepository.findPrecedents(anyInt(), anyInt(), any())).thenReturn(List.of());
        ParametreRepository parametreRepository = mock(ParametreRepository.class);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        VigilanceService service = new VigilanceService(scoreRepository, questionnaireRepository,
                trimestreRepository, new CalculService(parametreRepository,
                mock(PerformanceRepository.class), mock(PotentielRepository.class)), faits);

        List<ResultatVigilance> resultats = service.evaluerTrimestre(trimestre);
        assertThat(resultats).hasSize(lignes.size()).hasSize(100);

        List<Ecart> ecarts = new ArrayList<>();
        for (ResultatVigilance resultat : resultats) {
            String id = resultat.employe().getEmployeeId();
            Map<String, String> ligne = lignes.get(id);
            comparerNombre(ecarts, id, "12_VIGILANCE.Indice de Vigilance", ligne.get("L"), resultat.indice());
            comparerLibelle(ecarts, id, "12_VIGILANCE.Niveau de vigilance",
                    ligne.get("M"), resultat.niveau().getLibelle());
        }
        verifier(ecarts);

        Map<NiveauVigilance, Long> parNiveau = new EnumMap<>(NiveauVigilance.class);
        for (ResultatVigilance resultat : resultats) {
            parNiveau.merge(resultat.niveau(), 1L, Long::sum);
        }
        assertThat(parNiveau).containsExactly(
                Map.entry(NiveauVigilance.FAIBLE, 55L),
                Map.entry(NiveauVigilance.MODEREE, 35L),
                Map.entry(NiveauVigilance.ELEVEE, 10L));
    }

    // --- echantillon ---------------------------------------------------------

    /**
     * Des candidats de 09_SUCCESSION, pour moitie avec un score de competences
     * sous 100 : a 100, toutes les formules de couverture s'accordent et un
     * ecart de methode passerait inapercu. Completes par les premiers
     * collaborateurs, pour avoir aussi des profils faibles.
     */
    private static List<String> choisirEchantillon() {
        Set<String> incomplets = new LinkedHashSet<>();
        Set<String> complets = new LinkedHashSet<>();
        for (Map<String, String> ligne : classeur.feuille("09_SUCCESSION").values()) {
            String id = ligne.getOrDefault("B", "");
            if (!ligne.getOrDefault("A", "").matches("PST\\d+") || !id.matches("BP\\d+")) {
                continue;
            }
            boolean couvertureComplete = nombre(ligne.get("E")).compareTo(new BigDecimal("100")) >= 0;
            (couvertureComplete ? complets : incomplets).add(id);
        }

        Set<String> ids = new LinkedHashSet<>();
        incomplets.stream().limit(CANDIDATS_SUCCESSION / 2).forEach(ids::add);
        complets.stream().filter(id -> !ids.contains(id))
                .limit(CANDIDATS_SUCCESSION - ids.size()).forEach(ids::add);
        parEmploye("01_COLLABORATEURS").keySet().stream()
                .limit(PREMIERS_COLLABORATEURS)
                .forEach(ids::add);
        return List.copyOf(ids);
    }

    // --- construction des donnees d'entree -----------------------------------

    private static Employe employe(String id) {
        Map<String, String> ligne = parEmploye("01_COLLABORATEURS").get(id);
        Employe employe = new Employe();
        employe.setEmployeeId(id);
        employe.setNom(ligne.get("B"));
        employe.setPrenom(ligne.get("C"));
        employe.setDirection(ligne.get("H"));
        employe.setStatut(StatutEmploye.ACTIF);
        // Le moteur mesure l'anciennete a aujourd'hui, le classeur au 15/09/2026 :
        // la date d'entree est decalee d'autant pour comparer la meme duree.
        employe.setDateEntree(dateExcel(ligne.get("F"))
                .plusDays(ChronoUnit.DAYS.between(DATE_REFERENCE_CLASSEUR, LocalDate.now())));
        return employe;
    }

    private static BigDecimal scorePerformance(String id) {
        Map<String, String> ligne = parEmploye("02_PERFORMANCE").get(id);
        Performance performance = new Performance();
        performance.setNoteObjectifs(nombre(ligne.get("D")));
        performance.setNoteCompetences(nombre(ligne.get("E")));
        performance.setNoteComportement(nombre(ligne.get("F")));
        performance.setNoteContribution(nombre(ligne.get("G")));
        performance.setNoteDeveloppement(nombre(ligne.get("H")));
        return calculService.calculerScorePerformance(performance, parametre);
    }

    private static Potentiel potentiel(String id) {
        Map<String, String> ligne = parEmploye("03_POTENTIEL").get(id);
        Potentiel potentiel = new Potentiel();
        potentiel.setNoteLearning(nombre(ligne.get("D")));
        potentiel.setNoteLeadership(nombre(ligne.get("E")));
        potentiel.setNoteAdaptabilite(nombre(ligne.get("F")));
        potentiel.setNoteComplexite(nombre(ligne.get("G")));
        potentiel.setNoteMobilite(nombre(ligne.get("H")));
        potentiel.setNoteStrategie(nombre(ligne.get("I")));
        potentiel.setNoteAutonomie(nombre(ligne.get("J")));
        return potentiel;
    }

    private static BigDecimal scorePotentiel(String id) {
        return calculService.calculerScorePotentiel(potentiel(id), parametre);
    }

    private static Score score(String id) {
        Score score = new Score();
        score.setEmploye(employe(id));
        score.setScorePerformance(scorePerformance(id));
        score.setScorePotentiel(scorePotentiel(id));
        return score;
    }

    private static Map<String, Competence> referentielParNom() {
        Map<String, Competence> referentiel = new HashMap<>();
        for (Map<String, String> ligne : classeur.feuille("05_REFERENTIEL_COMPETENCES").values()) {
            if (ligne.getOrDefault("A", "").matches("C\\d+")) {
                Competence competence = new Competence();
                competence.setCompetenceId(ligne.get("A"));
                competence.setNom(ligne.get("B"));
                competence.setCategorie(ligne.get("C"));
                referentiel.put(ligne.get("B"), competence);
            }
        }
        return referentiel;
    }

    private static Map<String, Poste> postes(Map<String, Competence> referentiel) {
        Map<String, Map<String, String>> critiques = parPoste("08_POSTES_CRITIQUES");
        Map<String, Poste> postes = new HashMap<>();
        for (Map<String, String> ligne : classeur.feuille("07_POSTES").values()) {
            String posteId = ligne.getOrDefault("A", "");
            if (!posteId.matches("PST\\d+")) {
                continue;
            }
            Poste poste = new Poste();
            poste.setPosteId(posteId);
            poste.setNomPoste(ligne.get("B"));
            poste.setCompetenceRequise1(referentiel.get(ligne.get("F")));
            poste.setNiveau1(entier(ligne.get("G")));
            poste.setCompetenceRequise2(referentiel.get(ligne.get("H")));
            poste.setNiveau2(entier(ligne.get("I")));
            poste.setCompetenceRequise3(referentiel.get(ligne.get("J")));
            poste.setNiveau3(entier(ligne.get("K")));
            poste.setCompetenceRequise4(referentiel.get(ligne.get("L")));
            poste.setNiveau4(entier(ligne.get("M")));
            poste.setCompetenceRequise5(referentiel.get(ligne.get("N")));
            poste.setNiveau5(entier(ligne.get("O")));
            poste.setCriticite(ligne.get("E"));
            poste.setPosteCritique(ligne.get("P"));
            if (critiques.containsKey(posteId)) {
                poste.setTitulaireId(critiques.get(posteId).get("E"));
            }
            postes.put(posteId, poste);
        }
        return postes;
    }

    private static List<EmployeeSkill> competences(Employe employe, Map<String, Competence> referentiel) {
        List<EmployeeSkill> competences = new ArrayList<>();
        for (Map<String, String> ligne : classeur.feuille("06_EMPLOYEE_SKILLS").values()) {
            if (employe.getEmployeeId().equals(ligne.get("B")) && referentiel.containsKey(ligne.get("C"))) {
                EmployeeSkill skill = new EmployeeSkill();
                skill.setEmploye(employe);
                skill.setCompetence(referentiel.get(ligne.get("C")));
                skill.setNiveauActuel(entier(ligne.get("E")));
                skill.setNiveauCible(entier(ligne.get("F")));
                competences.add(skill);
            }
        }
        return competences;
    }

    /**
     * Le service des postes critiques branche sur le classeur : 07_POSTES pour
     * les postes, 09_SUCCESSION (Poste_ID, Employee_ID) pour les successeurs
     * identifies, et les donnees des employes pour leur matching.
     */
    private static PosteCritiqueService posteCritiqueServiceSurLeClasseur() {
        Map<String, Competence> referentiel = referentielParNom();

        SuccesseursIdentifiesEnMemoire successeurs = new SuccesseursIdentifiesEnMemoire();
        for (Map<String, String> ligne : classeur.feuille("09_SUCCESSION").values()) {
            String posteId = ligne.getOrDefault("A", "");
            String id = ligne.getOrDefault("B", "");
            if (posteId.matches("PST\\d+") && id.matches("BP\\d+")) {
                successeurs.identifier(posteId, id);
            }
        }

        List<Score> scores = new ArrayList<>();
        List<Potentiel> potentiels = new ArrayList<>();
        for (String id : parEmploye("01_COLLABORATEURS").keySet()) {
            scores.add(score(id));
            Potentiel potentiel = potentiel(id);
            potentiel.setEmploye(employe(id));
            potentiels.add(potentiel);
        }

        PosteRepository posteRepository = mock(PosteRepository.class);
        when(posteRepository.findAll()).thenReturn(new ArrayList<>(postes(referentiel).values()));

        EmployeRepository employeRepository = mock(EmployeRepository.class);
        when(employeRepository.findAllById(anyIterable())).thenAnswer(appel -> {
            List<Employe> employes = new ArrayList<>();
            for (Object id : (Iterable<?>) appel.getArgument(0)) {
                employes.add(employe((String) id));
            }
            return employes;
        });

        EmployeeSkillRepository employeeSkillRepository = mock(EmployeeSkillRepository.class);
        when(employeeSkillRepository.findByEmployeIdsAvecCompetence(anyCollection())).thenAnswer(appel -> {
            List<EmployeeSkill> skills = new ArrayList<>();
            for (Object id : (Collection<?>) appel.getArgument(0)) {
                skills.addAll(competences(employe((String) id), referentiel));
            }
            return skills;
        });

        ScoreRepository scoreRepository = mock(ScoreRepository.class);
        when(scoreRepository.findByTrimestreAvecEmploye(any())).thenReturn(scores);
        PotentielRepository potentielRepository = mock(PotentielRepository.class);
        when(potentielRepository.findByTrimestreAvecEmploye(any())).thenReturn(potentiels);
        ParametreRepository parametreRepository = mock(ParametreRepository.class);
        when(parametreRepository.findByTrimestre(any())).thenReturn(Optional.of(parametre));

        CalculService calcul = new CalculService(parametreRepository,
                mock(PerformanceRepository.class), potentielRepository);
        return new PosteCritiqueService(posteRepository, employeRepository, scoreRepository,
                potentielRepository, employeeSkillRepository, successionService, calcul, successeurs);
    }

    /** La vraie table de reference de l'application, chargee dans un depot en memoire. */
    private static Matrice9BoxRepository matriceDeReference() throws Exception {
        Map<String, Matrice9Box> cases = new HashMap<>();
        Matrice9BoxRepository depot = mock(Matrice9BoxRepository.class);
        when(depot.findByNiveauPerformanceAndNiveauPotentiel(anyInt(), anyInt())).thenAnswer(appel ->
                Optional.ofNullable(cases.get(appel.getArgument(0) + "/" + appel.getArgument(1))));
        when(depot.save(any(Matrice9Box.class))).thenAnswer(appel -> {
            Matrice9Box case9Box = appel.getArgument(0);
            cases.put(case9Box.getNiveauPerformance() + "/" + case9Box.getNiveauPotentiel(), case9Box);
            return case9Box;
        });
        new Matrice9BoxInitializer(depot).run(null);
        return depot;
    }

    // --- outils --------------------------------------------------------------

    /** Lignes d'une feuille indexees par Employee_ID (colonne A), dans l'ordre. */
    private static Map<String, Map<String, String>> parEmploye(String feuille) {
        Map<String, Map<String, String>> index = new LinkedHashMap<>();
        for (Map<String, String> ligne : classeur.feuille(feuille).values()) {
            String id = ligne.getOrDefault("A", "");
            if (id.matches("BP\\d+")) {
                index.put(id, ligne);
            }
        }
        return index;
    }

    /** Lignes d'une feuille indexees par Poste_ID (colonne A), dans l'ordre. */
    private static Map<String, Map<String, String>> parPoste(String feuille) {
        Map<String, Map<String, String>> index = new LinkedHashMap<>();
        for (Map<String, String> ligne : classeur.feuille(feuille).values()) {
            String id = ligne.getOrDefault("A", "");
            if (id.matches("PST\\d+")) {
                index.put(id, ligne);
            }
        }
        return index;
    }

    private static String libelleClasseur(NiveauCouverture niveau) {
        return switch (niveau) {
            case ALERTE -> "Aucun successeur - ALERTE";
            case READY_NOW -> "Couverte - Ready Now";
            case MOINS_1_AN -> "Couverte - <1 an";
            case PARTIELLE -> "Partielle - a renforcer";
        };
    }

    private static StatutValidationComite statutComite(String libelle) {
        for (StatutValidationComite statut : StatutValidationComite.values()) {
            if (normaliser(statut.getLibelle()).equals(normaliser(libelle))) {
                return statut;
            }
        }
        throw new IllegalStateException("Decision du comite inconnue dans le classeur : " + libelle);
    }

    private static String ouiNon(boolean valeur) {
        return valeur ? "Oui" : "Non";
    }

    /** Oui / Non saisi dans le classeur, null pour toute autre valeur (inconnu). */
    private static Boolean drapeau(String valeur) {
        if ("oui".equals(normaliser(valeur))) {
            return true;
        }
        return "non".equals(normaliser(valeur)) ? false : null;
    }

    private static String libelleClasseur(NiveauReadiness readiness) {
        return switch (readiness) {
            case READY_NOW -> "Ready Now";
            case MOINS_1_AN -> "Ready < 1 an";
            case ENTRE_1_ET_2_ANS -> "Ready 1-2 ans";
            case PLUS_2_ANS -> "Ready > 2 ans";
        };
    }

    private static void comparerNombre(List<Ecart> ecarts, String id, String champ,
                                       String attendu, BigDecimal obtenu) {
        if (attendu == null || obtenu == null) {
            if (attendu != null || obtenu != null) {
                ecarts.add(new Ecart(id, champ, String.valueOf(attendu), String.valueOf(obtenu)));
            }
            return;
        }
        BigDecimal valeurAttendue = nombre(attendu);
        if (valeurAttendue.subtract(obtenu).abs().compareTo(TOLERANCE) > 0) {
            ecarts.add(new Ecart(id, champ, valeurAttendue.stripTrailingZeros().toPlainString(),
                    obtenu.toPlainString()));
        }
    }

    private static void comparerLibelle(List<Ecart> ecarts, String id, String champ,
                                        String attendu, String obtenu) {
        if (!normaliser(attendu).equals(normaliser(obtenu))) {
            ecarts.add(new Ecart(id, champ, attendu, obtenu));
        }
    }

    private static String normaliser(String libelle) {
        if (libelle == null) {
            return "";
        }
        return Normalizer.normalize(libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase();
    }

    /** Arrondi au centieme : le classeur garde le bruit binaire des flottants (61.600000000000009). */
    private static BigDecimal nombre(String valeur) {
        return new BigDecimal(valeur).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Integer entier(String valeur) {
        return valeur == null ? null : (int) Double.parseDouble(valeur);
    }

    private static LocalDate dateExcel(String serie) {
        return LocalDate.of(1899, 12, 30).plusDays((long) Double.parseDouble(serie));
    }

    private static void verifier(List<Ecart> ecarts) {
        String rapport = ecarts.size() + " ecart(s) avec le classeur :\n"
                + String.join("\n", ecarts.stream().map(Ecart::toString).toList());
        if (!ecarts.isEmpty()) {
            System.out.println(rapport);
        }
        assertThat(ecarts).as(rapport).isEmpty();
    }
}
