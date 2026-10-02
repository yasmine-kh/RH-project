package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.PosteCibleService;
import com.talent360bank.talent360bank.ui.model.Choix;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.PosteCibleLigne;
import com.talent360bank.talent360bank.ui.model.MatriceNeufBox;
import com.talent360bank.talent360bank.ui.model.VueEntite;
import com.talent360bank.talent360bank.ui.model.VueEntite.NoeudEntite;
import com.talent360bank.talent360bank.ui.model.VueManager;
import com.talent360bank.talent360bank.ui.model.VueManager.ManagerResume;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pages Vue manager et Vue entite, et leurs pages de choix. Aucun calcul :
 * {@link VueManagerViewService} et {@link VueEntiteViewService} sont repris tels
 * quels ; s'y ajoutent la matrice commune (depuis leurs effectifs par case), la
 * readiness de chaque membre de l'equipe ({@link PosteCibleService}, comme la
 * liste des collaborateurs) et le fil d'Ariane de l'entite.
 */
@Service
public class ProfilsPagesService {

    private final VueManagerViewService vueManagerViewService;
    private final VueEntiteViewService vueEntiteViewService;
    private final PosteCibleService posteCibleService;

    public ProfilsPagesService(VueManagerViewService vueManagerViewService, VueEntiteViewService vueEntiteViewService,
                               PosteCibleService posteCibleService) {
        this.vueManagerViewService = vueManagerViewService;
        this.vueEntiteViewService = vueEntiteViewService;
        this.posteCibleService = posteCibleService;
    }

    /**
     * @param cibles poste cible (et readiness) de chaque membre de l'equipe qui en a un, par matricule
     */
    public record PageVueManager(VueManager vue, MatriceNeufBox matrice, Map<String, PosteCibleLigne> cibles) {
    }

    /** @param chemin chaque niveau de l'entite, de la direction a elle-meme (code et libelle) */
    public record PageVueEntite(VueEntite vue, MatriceNeufBox matrice, List<Choix> chemin) {
    }

    /** @throws RessourceIntrouvableException si le matricule est inconnu ou n'est pas manager */
    public PageVueManager vueManager(String matricule, Trimestre trimestre) {
        VueManager vue = vueManagerViewService.construire(matricule, trimestre.getAnnee(), trimestre.getNumero());
        String valeur = TrimestreCourantService.valeur(trimestre);

        // Un clic sur une case filtre le tableau de l'equipe de cette page sur la case.
        MatriceNeufBox matrice = MatriceNeufBox.depuisComptes(vue.synthese().neufBox(),
                numero -> LiensPages.vueManager(matricule, valeur) + "&case=" + numero + "#equipe");

        Set<String> equipe = vue.membres().stream().map(VueManager.Membre::matricule).collect(Collectors.toSet());
        Map<String, PosteCibleLigne> cibles = new HashMap<>();
        try {
            posteCibleService.postesCibles(trimestre).stream()
                    .filter(c -> equipe.contains(c.collaborateur().getIdCollaborateur()))
                    .forEach(c -> cibles.put(c.collaborateur().getIdCollaborateur(),
                            ListeCollaborateursViewService.posteCible(c)));
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            // Sans reglages : pas de readiness, la vue le dit deja dans donneesManquantes.
        }
        return new PageVueManager(vue, matrice, Map.copyOf(cibles));
    }

    /** @throws RessourceIntrouvableException si le code d'entite est inconnu */
    public PageVueEntite vueEntite(String code, Trimestre trimestre) {
        VueEntite vue = vueEntiteViewService.construire(code, trimestre.getAnnee(), trimestre.getNumero());
        String valeur = TrimestreCourantService.valeur(trimestre);
        String codeEntite = vue.entite().code();
        MatriceNeufBox matrice = MatriceNeufBox.depuisComptes(vue.synthese().neufBox(),
                numero -> MatriceNeufBox.lienListe(valeur, codeEntite, numero));
        return new PageVueEntite(vue, matrice, LiensPages.chemin(codeEntite, vue.entite().chemin()));
    }

    /** Les managers et la taille de leur equipe, pour la page de choix. */
    public List<ManagerResume> managers(Trimestre trimestre) {
        return vueManagerViewService.listerManagers(trimestre.getAnnee(), trimestre.getNumero());
    }

    /** L'organigramme, pour la page de choix. */
    public List<NoeudEntite> entites() {
        return vueEntiteViewService.listerEntites();
    }
}
