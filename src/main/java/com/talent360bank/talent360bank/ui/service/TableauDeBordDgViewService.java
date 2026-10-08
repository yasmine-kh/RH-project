package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.service.PosteCibleService;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.service.ValidationComiteService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;
import com.talent360bank.talent360bank.service.resultat.SyntheseTableauDeBord;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.PosteDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.SuccesseurDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordDg.TalentDg;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tableau de bord DG d'un trimestre : les cartes et la matrice du tableau de
 * bord RH, la releve de chaque poste critique et les meilleurs talents avec
 * leur poste cible. L'ecran DG n'existe plus : ses blocs propres (releve des
 * postes, meilleurs talents) sont sur le tableau de bord RH
 * ({@link #construireAccueil}) ; l'API GET /api/dashboard/dg reste.
 *
 * <p>REGLE : aucun calcul ici. Cartes et matrice sont celles de
 * {@link DashboardService} (memes valeurs, jamais recalculees autrement) ; la
 * releve des postes vient des couvertures de sa synthese
 * ({@link com.talent360bank.talent360bank.service.PosteCritiqueService}) ; les
 * talents de {@link TalentService}, leur decision de {@link ValidationComiteService},
 * leur poste cible de {@link PosteCibleService}. Ici, seulement compter, trier et
 * couper la liste des talents ({@link TableauDeBordDg#REGLE_TOP_TALENTS}).
 *
 * <p>Un nombre fixe de requetes quelle que soit la population. Sans
 * transaction propre, comme le tableau de bord RH.
 */
@Service
public class TableauDeBordDgViewService {

    /** Performance + potentiel decroissant, puis performance decroissante, puis matricule. */
    static final Comparator<Score> ORDRE_TOP_TALENTS = Comparator
            .comparing((Score score) -> score.getScorePerformance().add(score.getScorePotentiel()),
                    Comparator.reverseOrder())
            .thenComparing(Score::getScorePerformance, Comparator.reverseOrder())
            .thenComparing(score -> score.getCollaborateur().getIdCollaborateur());

    private final DashboardService dashboardService;
    private final TalentService talentService;
    private final ValidationComiteService validationComiteService;
    private final PosteCibleService posteCibleService;
    private final ParametreRepository parametreRepository;

    public TableauDeBordDgViewService(DashboardService dashboardService, TalentService talentService,
                                      ValidationComiteService validationComiteService,
                                      PosteCibleService posteCibleService, ParametreRepository parametreRepository) {
        this.dashboardService = dashboardService;
        this.talentService = talentService;
        this.validationComiteService = validationComiteService;
        this.posteCibleService = posteCibleService;
        this.parametreRepository = parametreRepository;
    }

    /**
     * Le tableau de bord RH et les blocs DG qu'il affiche, tires de la meme
     * construction (le moteur n'est appele qu'une fois).
     */
    public record Accueil(TableauDeBordView rh, TableauDeBordDg dg) {
    }

    /**
     * @param trimestre trimestre affiche, null s'il n'en existe aucun
     * @param limite    nombre de talents, 1 a {@link TableauDeBordDg#TOP_TALENTS_MAX}
     * @throws IllegalArgumentException si la limite est hors bornes
     */
    public TableauDeBordDg construire(Trimestre trimestre, int limite) {
        return construireAccueil(trimestre, limite).dg();
    }

    /** Comme {@link #construire}, avec le tableau de bord RH dont il reprend les cartes. */
    public Accueil construireAccueil(Trimestre trimestre, int limite) {
        if (limite < 1 || limite > TableauDeBordDg.TOP_TALENTS_MAX) {
            throw new IllegalArgumentException("Le nombre de talents doit etre entre 1 et "
                    + TableauDeBordDg.TOP_TALENTS_MAX);
        }
        DashboardService.Construction construction = dashboardService.construireAvecSynthese(trimestre);
        TableauDeBordView vue = construction.vue();
        SyntheseTableauDeBord synthese = construction.synthese();
        if (synthese == null) {
            return new Accueil(vue, new TableauDeBordDg(vue.trimestreLibelle(), vue.kpis(), vue.neufBox(),
                    List.of(), List.of(), vue.erreur()));
        }

        String erreur = null;
        List<TalentDg> talents = List.of();
        try {
            talents = topTalents(trimestre, limite);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            erreur = "Meilleurs talents : " + e.getMessage();
        }
        return new Accueil(vue, new TableauDeBordDg(vue.trimestreLibelle(), vue.kpis(), vue.neufBox(),
                synthese.couvertures().stream().map(TableauDeBordDgViewService::poste).toList(), talents, erreur));
    }

    /** La releve d'un poste critique, telle que PosteCritiqueService l'a evaluee. */
    static PosteDg poste(CouverturePoste couverture) {
        ResultatMatching meilleur = couverture.meilleurSuccesseur();
        int nbReadyNow = (int) couverture.successeurs().stream()
                .filter(successeur -> successeur.readiness() == NiveauReadiness.READY_NOW)
                .count();
        return new PosteDg(couverture.poste().getPosteId(), couverture.poste().getNomPoste(),
                couverture.poste().getDirection(), couverture.poste().getCriticite(),
                couverture.poste().getTitulaireNom(), couverture.nbSuccesseurs(), nbReadyNow,
                meilleur == null ? null : new SuccesseurDg(meilleur.candidat().getIdCollaborateur(),
                        meilleur.candidat().getNomComplet(), meilleur.scoreMatching(), meilleur.readiness().name(),
                        meilleur.readiness().getLibelle()),
                couverture.niveau().name(), couverture.niveau().getLibelle(), couverture.estEnAlerte());
    }

    private List<TalentDg> topTalents(Trimestre trimestre, int limite) {
        List<Score> top = talentService.detecterTalents(trimestre).stream()
                .sorted(ORDRE_TOP_TALENTS)
                .limit(limite)
                .toList();
        if (top.isEmpty()) {
            return List.of();
        }
        Parametre parametre = parametreRepository.findByTrimestre(trimestre).orElseThrow(
                () -> new RessourceIntrouvableException("Aucun reglage pour le trimestre"));
        Map<String, DecisionComite> decisions = validationComiteService.deciderPour(top, trimestre).stream()
                .collect(Collectors.toMap(d -> d.score().getCollaborateur().getIdCollaborateur(),
                        Function.identity()));
        Map<String, ResultatPosteCible> cibles = posteCibleService.postesCibles(trimestre).stream()
                .collect(Collectors.toMap(c -> c.collaborateur().getIdCollaborateur(), Function.identity()));

        List<TalentDg> talents = new ArrayList<>();
        for (Score score : top) {
            Collaborateur collaborateur = score.getCollaborateur();
            String id = collaborateur.getIdCollaborateur();
            DecisionComite decision = decisions.get(id);
            talents.add(new TalentDg(talents.size() + 1, id, collaborateur.getNomComplet(), score.getDirection(),
                    score.getScorePerformance(), score.getScorePotentiel(),
                    score.getScorePerformance().add(score.getScorePotentiel()),
                    talentService.estHautPotentiel(score, parametre), decision.statut().name(),
                    decision.talentValide(), ListeCollaborateursViewService.posteCible(cibles.get(id))));
        }
        return List.copyOf(talents);
    }
}
