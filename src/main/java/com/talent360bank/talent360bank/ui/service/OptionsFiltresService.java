package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.VivierReleveService;
import com.talent360bank.talent360bank.service.enums.NiveauReadiness;
import com.talent360bank.talent360bank.service.enums.NiveauVigilance;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import com.talent360bank.talent360bank.ui.model.Choix;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Les valeurs proposees dans les menus de filtres des ecrans (entites,
 * viviers, cases 9-box, readiness, vigilance, postes critiques). Les codes
 * sont ceux qu'attendent les services de vue ; aucune donnee calculee ici.
 */
@Service
public class OptionsFiltresService {

    private final VueEntiteViewService vueEntiteViewService;
    private final PosteRepository posteRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;

    public OptionsFiltresService(VueEntiteViewService vueEntiteViewService, PosteRepository posteRepository,
                                 Matrice9BoxRepository matrice9BoxRepository) {
        this.vueEntiteViewService = vueEntiteViewService;
        this.posteRepository = posteRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
    }

    /** Toutes les entites de l'organigramme, en arbre (chaque niveau decale), deux requetes. */
    public List<Choix> entites() {
        List<Choix> choix = new ArrayList<>();
        for (NoeudEntite racine : vueEntiteViewService.listerEntites()) {
            ajouter(racine, 0, choix);
        }
        return List.copyOf(choix);
    }

    private static void ajouter(NoeudEntite noeud, int profondeur, List<Choix> choix) {
        choix.add(new Choix(noeud.code(), "· ".repeat(profondeur) + noeud.libelle()));
        for (NoeudEntite enfant : noeud.enfants()) {
            ajouter(enfant, profondeur + 1, choix);
        }
    }

    /** Les cinq viviers thematiques puis le vivier de releve. */
    public static List<Choix> viviers() {
        List<Choix> choix = new ArrayList<>(Arrays.stream(VivierThematique.values())
                .map(v -> new Choix(v.getCode(), v.getLibelle())).toList());
        choix.add(new Choix(VivierReleveService.CODE_VIVIER_RELEVE, VivierReleveService.NOM_VIVIER_RELEVE));
        return List.copyOf(choix);
    }

    /** Les neuf cases, de 9 (performance et potentiel eleves) a 1, avec leur libelle actuel ; une requete. */
    public List<Choix> casesNeufBox() {
        return matrice9BoxRepository.findAll().stream()
                .sorted(Comparator.comparing(OptionsFiltresService::numero).reversed())
                .map(c -> new Choix(String.valueOf(NeufBoxService.numeroCase(c)),
                        NeufBoxService.numeroCase(c) + " - " + c.getCategorie()))
                .toList();
    }

    public static List<Choix> readiness() {
        return Arrays.stream(NiveauReadiness.values()).map(n -> new Choix(n.name(), n.getLibelle())).toList();
    }

    public static List<Choix> vigilance() {
        return Arrays.stream(NiveauVigilance.values()).map(n -> new Choix(n.name(), n.getLibelle())).toList();
    }

    /** Les postes critiques, par Poste_ID ; une requete. */
    public List<Choix> postesCritiques() {
        return posteRepository.findAllAvecCompetences().stream()
                .filter(PosteCritiqueService::estCritique)
                .sorted(Comparator.comparing(com.talent360bank.talent360bank.entity.Poste::getPosteId))
                .map(p -> new Choix(p.getPosteId(), p.getPosteId() + " - " + p.getNomPoste()))
                .toList();
    }

    private static int numero(Matrice9Box c) {
        return NeufBoxService.numeroCase(c);
    }
}
