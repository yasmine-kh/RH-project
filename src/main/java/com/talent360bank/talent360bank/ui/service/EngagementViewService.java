package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.ConfigurationQuestion;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.ReponseQuestionnaire;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ConfigurationQuestionRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.ReponseQuestionnaireRepository;
import com.talent360bank.talent360bank.ui.model.Engagement;
import com.talent360bank.talent360bank.ui.model.Engagement.Dimension;
import com.talent360bank.talent360bank.ui.model.Engagement.Enps;
import com.talent360bank.talent360bank.ui.model.Engagement.Nature;
import com.talent360bank.talent360bank.ui.model.Engagement.ParEntite;
import com.talent360bank.talent360bank.ui.model.Engagement.Question;
import com.talent360bank.talent360bank.ui.model.Engagement.Texte;
import com.talent360bank.talent360bank.ui.model.Engagement.Valeur;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * L'ecran Engagement &amp; Fidelisation : rien n'est devine.
 * <ul>
 *   <li><b>Taux de reponse</b> : actifs qui ont au moins une reponse au questionnaire du trimestre /
 *   actifs. <b>Engagement moyen</b> : moyenne des scores d'engagement /100 importes (12_VIGILANCE) des
 *   actifs, comme la carte du tableau de bord.</li>
 *   <li><b>Par question</b>, dans l'ordre des colonnes : nombre de reponses ; la nature se lit dans les
 *   reponses ({@link #nature}) : toutes numeriques = echelle (moyenne et repartition) ; sinon, si
 *   au plus {@value #CHOIX_MAX} valeurs distinctes et au moins une repetee = choix (repartition) ;
 *   sinon reponse libre (liste avec l'auteur).</li>
 *   <li><b>Dimensions et eNPS</b> : seulement une fois reglees par le RH (page Parametres,
 *   configuration_question). Moyenne d'une dimension = moyenne des reponses numeriques de ses
 *   questions. eNPS = % promoteurs (9-10) - % detracteurs (0-6) sur les reponses entieres de 0 a 10.</li>
 * </ul>
 * Quatre requetes : actifs, reponses, scores d'engagement, reglages des questions.
 */
@Service
public class EngagementViewService {

    static final int CHOIX_MAX = 10;
    public static final String SANS_DIRECTION = "Sans direction";

    private final CollaborateurRepository collaborateurRepository;
    private final ReponseQuestionnaireRepository reponseRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final ConfigurationQuestionRepository configurationRepository;

    public EngagementViewService(CollaborateurRepository collaborateurRepository,
                                 ReponseQuestionnaireRepository reponseRepository,
                                 QuestionnaireEngagementRepository questionnaireRepository,
                                 ConfigurationQuestionRepository configurationRepository) {
        this.collaborateurRepository = collaborateurRepository;
        this.reponseRepository = reponseRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.configurationRepository = configurationRepository;
    }

    @Transactional(readOnly = true)
    public Engagement construire(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        List<Collaborateur> actifs = collaborateurRepository.findByStatutAvecEntite(StatutCollaborateur.ACTIF);
        Set<String> population = new HashSet<>();
        actifs.forEach(c -> population.add(c.getIdCollaborateur()));

        List<ReponseQuestionnaire> reponses = reponseRepository.findByTrimestreAvecCollaborateur(trimestre);
        Set<String> repondants = new HashSet<>();
        for (ReponseQuestionnaire r : reponses) {
            String matricule = r.getCollaborateur().getIdCollaborateur();
            if (population.contains(matricule)) {
                repondants.add(matricule);
            }
        }

        Map<String, BigDecimal> scores = new HashMap<>();
        for (QuestionnaireEngagement q : questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            if (q.getScoreEngagement() != null && population.contains(q.getCollaborateur().getIdCollaborateur())) {
                scores.put(q.getCollaborateur().getIdCollaborateur(), q.getScoreEngagement());
            }
        }

        Map<String, ConfigurationQuestion> reglages = new HashMap<>();
        configurationRepository.findAll().forEach(c -> reglages.put(c.getCodeQuestion(), c));

        List<Question> questions = questions(reponses, reglages);
        return new Engagement(TrimestreCourantService.libelle(trimestre), population.size(), repondants.size(),
                pourcentage(repondants.size(), population.size()), moyenne(scores.values(), 2), scores.size(),
                enps(reponses, reglages), dimensions(reponses, reglages), questions,
                parEntite(actifs, repondants, scores));
    }

    // ------------------------------------------------------------------ questions

    private static List<Question> questions(List<ReponseQuestionnaire> reponses,
                                            Map<String, ConfigurationQuestion> reglages) {
        // Ordre des colonnes du fichier (ordre, puis code) ; texte de la question tel qu'importe.
        Map<String, List<ReponseQuestionnaire>> parCode = new LinkedHashMap<>();
        reponses.stream()
                .sorted(Comparator.comparingInt(ReponseQuestionnaire::getOrdre)
                        .thenComparing(ReponseQuestionnaire::getCodeQuestion))
                .forEach(r -> parCode.computeIfAbsent(r.getCodeQuestion(), k -> new ArrayList<>()).add(r));
        List<Question> questions = new ArrayList<>();
        parCode.forEach((code, liste) -> {
            ConfigurationQuestion reglage = reglages.get(code);
            List<String> valeurs = liste.stream().map(r -> r.getReponse() == null ? "" : r.getReponse().trim())
                    .filter(v -> !v.isEmpty()).toList();
            Nature nature = nature(valeurs);
            BigDecimal moyenne = nature == Nature.NUMERIQUE
                    ? moyenne(valeurs.stream().map(EngagementViewService::nombre).toList(), 2) : null;
            List<Texte> textes = nature != Nature.TEXTE ? List.of() : liste.stream()
                    .filter(r -> r.getReponse() != null && !r.getReponse().isBlank())
                    .map(r -> new Texte(r.getCollaborateur().getIdCollaborateur(), r.getCollaborateur().getNomComplet(),
                            r.getReponse().trim()))
                    .toList();
            questions.add(new Question(code, liste.get(0).getQuestion(),
                    reglage == null ? null : reglage.getDimension(), reglage != null && reglage.isQuestionEnps(),
                    nature, valeurs.size(), moyenne, nature == Nature.TEXTE ? List.of() : distribution(valeurs, nature),
                    textes));
        });
        return List.copyOf(questions);
    }

    /** Toutes numeriques : echelle ; peu de valeurs dont une repetee : choix ; sinon reponse libre. */
    static Nature nature(List<String> valeurs) {
        if (!valeurs.isEmpty() && valeurs.stream().allMatch(v -> nombre(v) != null)) {
            return Nature.NUMERIQUE;
        }
        long distinctes = valeurs.stream().distinct().count();
        return distinctes <= CHOIX_MAX && distinctes < valeurs.size() ? Nature.CHOIX : Nature.TEXTE;
    }

    private static List<Valeur> distribution(List<String> valeurs, Nature nature) {
        Map<String, Integer> comptes = nature == Nature.NUMERIQUE
                ? new TreeMap<>(Comparator.comparing(EngagementViewService::nombre)) : new TreeMap<>();
        valeurs.forEach(v -> comptes.merge(nature == Nature.NUMERIQUE ? nombre(v).stripTrailingZeros().toPlainString() : v,
                1, Integer::sum));
        List<Valeur> distribution = new ArrayList<>();
        comptes.forEach((valeur, nombre) -> distribution.add(new Valeur(valeur, nombre,
                pourcentage(nombre, valeurs.size()))));
        return distribution;
    }

    // ------------------------------------------------------------------ dimensions et eNPS

    private static List<Dimension> dimensions(List<ReponseQuestionnaire> reponses,
                                              Map<String, ConfigurationQuestion> reglages) {
        Map<String, List<String>> codesParDimension = new TreeMap<>();
        reglages.values().stream()
                .filter(c -> c.getDimension() != null && !c.getDimension().isBlank() && !c.isQuestionEnps())
                .sorted(Comparator.comparing(ConfigurationQuestion::getCodeQuestion))
                .forEach(c -> codesParDimension.computeIfAbsent(c.getDimension(), k -> new ArrayList<>())
                        .add(c.getCodeQuestion()));
        List<Dimension> dimensions = new ArrayList<>();
        codesParDimension.forEach((nom, codes) -> {
            List<BigDecimal> valeurs = reponses.stream()
                    .filter(r -> codes.contains(r.getCodeQuestion()))
                    .map(r -> nombre(r.getReponse()))
                    .filter(Objects::nonNull).toList();
            dimensions.add(new Dimension(nom, List.copyOf(codes), valeurs.size(), moyenne(valeurs, 2)));
        });
        return List.copyOf(dimensions);
    }

    private static Enps enps(List<ReponseQuestionnaire> reponses, Map<String, ConfigurationQuestion> reglages) {
        ConfigurationQuestion question = reglages.values().stream().filter(ConfigurationQuestion::isQuestionEnps)
                .findFirst().orElse(null);
        if (question == null) {
            return null;
        }
        String code = question.getCodeQuestion();
        int promoteurs = 0;
        int passifs = 0;
        int detracteurs = 0;
        int hors = 0;
        String texte = null;
        for (ReponseQuestionnaire r : reponses) {
            if (!r.getCodeQuestion().equals(code) || r.getReponse() == null || r.getReponse().isBlank()) {
                continue;
            }
            texte = r.getQuestion();
            BigDecimal note = nombre(r.getReponse());
            if (note == null || note.stripTrailingZeros().scale() > 0 || note.compareTo(BigDecimal.ZERO) < 0
                    || note.compareTo(BigDecimal.TEN) > 0) {
                hors++;
            } else if (note.intValue() >= 9) {
                promoteurs++;
            } else if (note.intValue() >= 7) {
                passifs++;
            } else {
                detracteurs++;
            }
        }
        int total = promoteurs + passifs + detracteurs;
        BigDecimal valeur = total == 0 ? null : BigDecimal.valueOf((promoteurs - detracteurs) * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return new Enps(code, texte, total, promoteurs, passifs, detracteurs, valeur, hors);
    }

    // ------------------------------------------------------------------ par entite

    private static List<ParEntite> parEntite(List<Collaborateur> actifs, Set<String> repondants,
                                             Map<String, BigDecimal> scores) {
        Map<String, List<Collaborateur>> parDirection = new TreeMap<>();
        actifs.forEach(c -> parDirection.computeIfAbsent(
                c.getDirection() == null ? SANS_DIRECTION : c.getDirection(), k -> new ArrayList<>()).add(c));
        List<ParEntite> lignes = new ArrayList<>();
        parDirection.forEach((direction, membres) -> {
            int nbRepondants = (int) membres.stream().filter(c -> repondants.contains(c.getIdCollaborateur())).count();
            List<BigDecimal> valeurs = membres.stream().map(c -> scores.get(c.getIdCollaborateur()))
                    .filter(Objects::nonNull).toList();
            lignes.add(new ParEntite(direction, membres.size(), nbRepondants,
                    pourcentage(nbRepondants, membres.size()), moyenne(valeurs, 2)));
        });
        // "Sans direction" en dernier.
        lignes.sort(Comparator.comparing((ParEntite l) -> SANS_DIRECTION.equals(l.direction()))
                .thenComparing(ParEntite::direction));
        return List.copyOf(lignes);
    }

    // ------------------------------------------------------------------ outils

    /** Nombre ecrit dans une reponse ("4", "3,5", " 10 "), null sinon. */
    static BigDecimal nombre(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texte.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal moyenne(java.util.Collection<BigDecimal> valeurs, int decimales) {
        if (valeurs.isEmpty()) {
            return null;
        }
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valeurs.size()), decimales, RoundingMode.HALF_UP);
    }

    private static BigDecimal pourcentage(int part, int total) {
        return total == 0 ? null : BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 1,
                RoundingMode.HALF_UP);
    }
}
