package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.service.CompetenceCollaborateurService;
import com.talent360bank.talent360bank.service.TableauDeBordService;
import com.talent360bank.talent360bank.service.VivierThematiqueService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.MembreVivierThematique;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatViviersThematiques;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CompteNiveau;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.VivierTableau;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tableau de bord RH d'un trimestre (page "/").
 *
 * <p>REGLE : aucun calcul ici et aucun chiffre ecrit en dur. Talents valides,
 * hauts potentiels, vivier de releve, vigilance, postes critiques, Ready Now et
 * repartition 9-box viennent de {@link TableauDeBordService} ; les viviers
 * thematiques de {@link VivierThematiqueService} ; les gaps de competences de
 * {@link CompetenceCollaborateurService} ; les alertes prioritaires de
 * {@link AlertesViewService}, la source de l'ecran Alertes (les deux ne
 * peuvent pas diverger). Seules les syntheses d'affichage (compter, moyenner)
 * sont faites ici. Un chiffre sans source de donnees n'est pas affiche plutot
 * que d'etre invente.
 *
 * <p>Sans reglages pour le trimestre, le moteur ne peut rien calculer : seuls
 * l'effectif et l'engagement (qui n'en dependent pas) sont affiches, avec le
 * message d'erreur.
 *
 * <p><strong>Requetes.</strong> Un nombre fixe quelle que soit la population
 * (voir SansSessionOuverteIntegrationTest).
 */
@Service
public class DashboardService {

    private static final int PRECISION_MOYENNE = 2;

    private final TableauDeBordService tableauDeBordService;
    private final VivierThematiqueService vivierThematiqueService;
    private final CompetenceCollaborateurService competenceCollaborateurService;
    private final CollaborateurRepository collaborateurRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final AlertesViewService alertesViewService;

    public DashboardService(TableauDeBordService tableauDeBordService,
                            VivierThematiqueService vivierThematiqueService,
                            CompetenceCollaborateurService competenceCollaborateurService,
                            CollaborateurRepository collaborateurRepository,
                            QuestionnaireEngagementRepository questionnaireRepository,
                            Matrice9BoxRepository matrice9BoxRepository,
                            AlertesViewService alertesViewService) {
        this.alertesViewService = alertesViewService;
        this.tableauDeBordService = tableauDeBordService;
        this.vivierThematiqueService = vivierThematiqueService;
        this.competenceCollaborateurService = competenceCollaborateurService;
        this.collaborateurRepository = collaborateurRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
    }

    /**
     * @param trimestre trimestre affiche (TrimestreCourantService), null s'il n'en existe aucun
     */
    public TableauDeBordView construire(Trimestre trimestre) {
        long actifs = collaborateurRepository.countByStatut(StatutCollaborateur.ACTIF);
        KpiCard effectif = new KpiCard("Collaborateurs actifs", String.valueOf(actifs), "bi-people", "kpi-blue");
        if (trimestre == null) {
            return new TableauDeBordView(null, List.of(effectif), List.of(), 0, 0, List.of(), 0, List.of(), 0,
                    List.of(), null);
        }
        String libelle = TrimestreCourantService.libelle(trimestre);
        KpiCard engagement = engagement(trimestre);

        SyntheseTableauDeBord synthese;
        ResultatViviersThematiques viviers;
        int gapsPrioritaires;
        List<AlerteVue> alertes;
        try {
            synthese = tableauDeBordService.synthese(trimestre);
            viviers = vivierThematiqueService.getViviersThematiques(trimestre);
            gapsPrioritaires = competenceCollaborateurService.compterGapsPrioritaires(trimestre);
            // Les memes alertes que l'ecran Alertes, deja triees par gravite.
            alertes = alertesViewService.alertes(trimestre);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            return new TableauDeBordView(libelle, List.of(effectif, engagement), List.of(), 0, 0, List.of(), 0,
                    List.of(), 0, List.of(), e.getMessage());
        }

        List<VivierTableau> lignesViviers = viviers(viviers, synthese.couvertures());
        int nbEvaluesVigilance = synthese.vigilanceParNiveau().values().stream().mapToInt(Integer::intValue).sum();

        List<KpiCard> kpis = List.of(
                effectif,
                new KpiCard("Talents validés par le Comité", String.valueOf(synthese.nbTalentsValides()),
                        "bi-patch-check", "kpi-green"),
                new KpiCard("Hauts potentiels", String.valueOf(synthese.nbHautsPotentiels()),
                        "bi-graph-up-arrow", "kpi-purple"),
                new KpiCard("Vivier de succession (relève)", String.valueOf(synthese.nbVivierReleve()),
                        "bi-diagram-3", "kpi-purple"),
                new KpiCard("Viviers actifs", String.valueOf(lignesViviers.stream().filter(v -> v.effectif() > 0)
                        .count()), "bi-collection", "kpi-blue"),
                new KpiCard("Postes critiques", String.valueOf(synthese.nbPostesCritiques()),
                        "bi-briefcase", "kpi-blue"),
                new KpiCard("Couverture succession", pourcentage(synthese.tauxCouverture()),
                        "bi-link-45deg", "kpi-green"),
                new KpiCard("Successeurs Ready Now", String.valueOf(synthese.nbSuccessionsReadyNow()),
                        "bi-check2-circle", "kpi-green"),
                new KpiCard("Postes critiques sans successeur", String.valueOf(synthese.nbPostesSansSuccesseur()),
                        "bi-x-circle", "kpi-red"),
                new KpiCard("Postes critiques en alerte", String.valueOf(synthese.nbAlertesPostesCritiques()),
                        "bi-exclamation-triangle", "kpi-orange"),
                new KpiCard("À risque (vigilance modérée ou élevée)", String.valueOf(synthese.nbARisque()),
                        "bi-exclamation-octagon", "kpi-red"),
                new KpiCard("Compétences en gap prioritaire", String.valueOf(gapsPrioritaires),
                        "bi-mortarboard", "kpi-orange"),
                engagement);

        return new TableauDeBordView(libelle, kpis, neufBox(synthese), synthese.nbPlaces9Box(),
                synthese.nbNonPlaces9Box(), vigilance(synthese), (int) Math.max(0, actifs - nbEvaluesVigilance),
                alertes.stream().limit(TableauDeBordView.ALERTES_AFFICHEES).toList(), alertes.size(),
                lignesViviers, null);
    }

    /** Moyenne des questionnaires d'engagement des actifs (score /100) et nombre de reponses. */
    private KpiCard engagement(Trimestre trimestre) {
        List<BigDecimal> scores = questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre).stream()
                .filter(reponse -> reponse.getCollaborateur().estCalculable())
                .map(QuestionnaireEngagement::getScoreEngagement)
                .filter(Objects::nonNull)
                .toList();
        BigDecimal moyenne = moyenne(scores);
        return new KpiCard("Engagement moyen /100 (" + scores.size() + " réponse" + (scores.size() > 1 ? "s" : "")
                + ")", moyenne == null ? "—" : moyenne.toPlainString(), "bi-heart", "kpi-orange");
    }

    /** Les 9 cases, performance elevee en haut puis potentiel faible a gauche, comme la page 9-Box. */
    private List<CaseTableau> neufBox(SyntheseTableauDeBord synthese) {
        return matrice9BoxRepository.findAll().stream()
                .sorted(Comparator.comparing(Matrice9Box::getNiveauPerformance, Comparator.reverseOrder())
                        .thenComparing(Matrice9Box::getNiveauPotentiel))
                .map(case9Box -> new CaseTableau(case9Box.getCategorie(), case9Box.getNiveauPerformance(),
                        case9Box.getNiveauPotentiel(),
                        synthese.repartition9Box().getOrDefault(case9Box.getCategorie(), 0)))
                .toList();
    }

    private static List<CompteNiveau> vigilance(SyntheseTableauDeBord synthese) {
        List<CompteNiveau> comptes = new ArrayList<>();
        for (NiveauVigilance niveau : NiveauVigilance.values()) {
            comptes.add(new CompteNiveau(niveau.name(), niveau.getLibelle(),
                    synthese.vigilanceParNiveau().getOrDefault(niveau, 0)));
        }
        return List.copyOf(comptes);
    }

    /**
     * Une ligne par vivier thematique : effectif, moyennes, talents, membres
     * Ready Now et postes critiques couverts par un membre.
     */
    private static List<VivierTableau> viviers(ResultatViviersThematiques viviers,
                                               List<CouverturePoste> couvertures) {
        Set<String> readyNow = couvertures.stream()
                .flatMap(couverture -> couverture.successeurs().stream())
                .filter(successeur -> successeur.readiness() == NiveauReadiness.READY_NOW)
                .map(successeur -> successeur.candidat().getIdCollaborateur())
                .collect(Collectors.toSet());

        List<VivierTableau> lignes = new ArrayList<>();
        for (VivierThematique vivier : VivierThematique.values()) {
            List<MembreVivierThematique> membres = viviers.membresDe(vivier);
            Set<String> ids = membres.stream()
                    .map(membre -> membre.score().getCollaborateur().getIdCollaborateur())
                    .collect(Collectors.toSet());
            int postesCouverts = (int) couvertures.stream()
                    .filter(couverture -> couverture.successeurs().stream()
                            .map(ResultatMatching::candidat)
                            .anyMatch(candidat -> ids.contains(candidat.getIdCollaborateur())))
                    .count();
            lignes.add(new VivierTableau(vivier.getCode(), vivier.getLibelle(), membres.size(),
                    moyenne(membres, membre -> membre.score().getScorePerformance()),
                    moyenne(membres, membre -> membre.score().getScorePotentiel()),
                    (int) membres.stream().filter(MembreVivierThematique::talent).count(),
                    (int) ids.stream().filter(readyNow::contains).count(),
                    postesCouverts));
        }
        return List.copyOf(lignes);
    }

    private static BigDecimal moyenne(List<MembreVivierThematique> membres,
                                      Function<MembreVivierThematique, BigDecimal> valeur) {
        return moyenne(membres.stream().map(valeur).filter(Objects::nonNull).toList());
    }

    /** Moyenne arrondie a 2 decimales, null sans valeur. */
    private static BigDecimal moyenne(List<BigDecimal> valeurs) {
        if (valeurs.isEmpty()) {
            return null;
        }
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valeurs.size()), PRECISION_MOYENNE, RoundingMode.HALF_UP);
    }

    private static String pourcentage(BigDecimal taux) {
        return taux == null ? "—" : taux.stripTrailingZeros().toPlainString() + " %";
    }
}
