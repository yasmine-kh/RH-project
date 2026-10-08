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
import com.talent360bank.talent360bank.service.TableauDeBordService;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Tableau de bord RH d'un trimestre (page "/") : les chiffres du moteur que la page affiche, et
 * seulement eux. Le reste de la page (cartes sur les personnes, graphiques, listes, alertes) est
 * construit par {@link TableauDeBordInteractifService} sur la population du trimestre.
 *
 * <p>REGLE : aucun calcul ici et aucun chiffre ecrit en dur. Postes critiques, couverture,
 * successions Ready Now, postes sans successeur, postes en alerte et repartition 9-box viennent de
 * {@link TableauDeBordService}. Un chiffre sans source de donnees n'est pas affiche plutot que
 * d'etre invente.
 *
 * <p>Sans reglages pour le trimestre, le moteur ne peut rien calculer : la page n'affiche alors que
 * l'effectif et l'engagement (qui n'en dependent pas), avec le message d'erreur ; ils ne sont
 * construits que dans ce cas.
 *
 * <p><strong>Requetes.</strong> Un nombre fixe quelle que soit la population
 * (voir SansSessionOuverteIntegrationTest).
 */
@Service
public class DashboardService {

    private static final int PRECISION_MOYENNE = 2;

    private final TableauDeBordService tableauDeBordService;
    private final CollaborateurRepository collaborateurRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;

    public DashboardService(TableauDeBordService tableauDeBordService, CollaborateurRepository collaborateurRepository,
                            QuestionnaireEngagementRepository questionnaireRepository,
                            Matrice9BoxRepository matrice9BoxRepository) {
        this.tableauDeBordService = tableauDeBordService;
        this.collaborateurRepository = collaborateurRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
    }

    /**
     * @param trimestre trimestre affiche (TrimestreCourantService), null s'il n'en existe aucun
     */
    public TableauDeBordView construire(Trimestre trimestre) {
        if (trimestre == null) {
            return new TableauDeBordView(null, List.of(), List.of(), null);
        }
        String libelle = TrimestreCourantService.libelle(trimestre);

        SyntheseTableauDeBord synthese;
        try {
            synthese = tableauDeBordService.synthese(trimestre);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            long actifs = collaborateurRepository.countByStatut(StatutCollaborateur.ACTIF);
            return new TableauDeBordView(libelle,
                    List.of(new KpiCard("Collaborateurs actifs", String.valueOf(actifs)), engagement(trimestre)),
                    List.of(), e.getMessage());
        }

        List<KpiCard> kpis = List.of(
                new KpiCard("Postes critiques", String.valueOf(synthese.nbPostesCritiques())),
                new KpiCard("Couverture succession", pourcentage(synthese.tauxCouverture())),
                new KpiCard("Successeurs Ready Now", String.valueOf(synthese.nbSuccessionsReadyNow())),
                new KpiCard("Postes critiques sans successeur", String.valueOf(synthese.nbPostesSansSuccesseur())),
                new KpiCard("Postes critiques en alerte", String.valueOf(synthese.nbAlertesPostesCritiques())));
        return new TableauDeBordView(libelle, kpis, neufBox(synthese), null);
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
                + ")", moyenne == null ? "—" : moyenne.toPlainString());
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
