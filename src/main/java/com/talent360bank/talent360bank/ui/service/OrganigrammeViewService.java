package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.TalentService;
import com.talent360bank.talent360bank.ui.model.AlerteVue;
import com.talent360bank.talent360bank.ui.model.Organigramme;
import com.talent360bank.talent360bank.ui.model.Organigramme.Branche;
import com.talent360bank.talent360bank.ui.model.Organigramme.Direction;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La page Organigramme. Aucun calcul : l'arbre et ses effectifs viennent de
 * {@link VueEntiteViewService#listerEntites()}, les talents de
 * {@link TalentService#detecterTalents}, les alertes de {@link AlertesViewService#alertes},
 * comptes par direction (libelle de la direction, comme le reste de l'application).
 * Nombre de requetes fixe, quelle que soit la taille de l'organigramme.
 */
@Service
public class OrganigrammeViewService {

    private final VueEntiteViewService vueEntiteViewService;
    private final TalentService talentService;
    private final AlertesViewService alertesViewService;

    public OrganigrammeViewService(VueEntiteViewService vueEntiteViewService, TalentService talentService,
                                   AlertesViewService alertesViewService) {
        this.vueEntiteViewService = vueEntiteViewService;
        this.talentService = talentService;
        this.alertesViewService = alertesViewService;
    }

    public Organigramme organigramme(Trimestre trimestre) {
        List<NoeudEntite> racines = vueEntiteViewService.listerEntites();
        Map<String, Long> talents = null;
        Map<String, Long> alertes = null;
        try {
            talents = compter(talentService.detecterTalents(trimestre), Score::getDirection);
            alertes = compter(alertesViewService.alertes(trimestre), AlerteVue::direction);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            // Sans reglages : l'arbre et les effectifs s'affichent quand meme, sans indicateurs.
            talents = null;
            alertes = null;
        }
        Map<String, Long> t = talents;
        Map<String, Long> a = alertes;
        List<Direction> directions = racines.stream()
                .map(racine -> new Direction(fusionner(racine),
                        t == null ? null : t.getOrDefault(racine.libelle(), 0L).intValue(),
                        a == null ? null : a.getOrDefault(racine.libelle(), 0L).intValue()))
                .toList();
        return new Organigramme(directions, t != null);
    }

    /**
     * Fusionne les chaines d'enfants uniques de meme effectif : "Siege - Casablanca"
     * dont le seul enfant "Siege" a le meme effectif devient une ligne
     * "Siege - Casablanca / Siege", qui mene a la vue de "Siege". Les effectifs ne
     * changent pas.
     */
    static Branche fusionner(NoeudEntite noeud) {
        StringBuilder libelle = new StringBuilder(noeud.libelle());
        NoeudEntite courant = noeud;
        while (courant.enfants().size() == 1 && courant.enfants().get(0).effectif() == courant.effectif()) {
            courant = courant.enfants().get(0);
            libelle.append(" / ").append(courant.libelle());
        }
        return new Branche(courant.code(), libelle.toString(), courant.type(), noeud.effectif(),
                courant.enfants().stream().map(OrganigrammeViewService::fusionner).toList());
    }

    private static <T> Map<String, Long> compter(List<T> elements, Function<T, String> direction) {
        return elements.stream()
                .map(direction)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
