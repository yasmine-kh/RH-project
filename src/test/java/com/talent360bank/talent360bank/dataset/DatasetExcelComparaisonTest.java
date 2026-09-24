package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.config.Matrice9BoxInitializer;
import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.EmployeeSkill;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.NiveauReadiness;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.EmployeeSkillRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.CalculService;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.ResultatMatching;
import com.talent360bank.talent360bank.service.SuccessionService;
import com.talent360bank.talent360bank.service.TalentService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.anyInt;
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

    private static String ouiNon(boolean valeur) {
        return valeur ? "Oui" : "Non";
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
