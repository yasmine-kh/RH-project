package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.ui.model.PosteCritiqueRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Assemble le tableau de l'ecran Postes critiques.
 * Aucun calcul ici : tout vient de PosteCritiqueService.listerPostesCritiques() (Jas).
 */
@Service
public class PosteCritiqueViewService {

    private final PosteCritiqueService posteCritiqueService;

    public PosteCritiqueViewService(PosteCritiqueService posteCritiqueService) {
        this.posteCritiqueService = posteCritiqueService;
    }

    /**
     * @param trimestre trimestre affiche (TrimestreCourantService), null s'il n'en existe aucun
     */
    public List<PosteCritiqueRow> buildRows(Trimestre trimestre) {
        List<PosteCritiqueRow> lignes = new ArrayList<>();

        if (trimestre == null) {
            return lignes;
        }

        List<CouverturePoste> couvertures;
        try {
            couvertures = posteCritiqueService.listerPostesCritiques(trimestre);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            // Trimestre sans reglages (ouvert mais pas encore importe) : ecran vide plutot qu'une erreur 500.
            return lignes;
        }

        for (CouverturePoste couverture : couvertures) {
            ResultatMatching meilleur = couverture.meilleurSuccesseur();
            String candidatPotentiel = meilleur == null ? "-" : meilleur.candidat().getNomComplet();

            lignes.add(new PosteCritiqueRow(
                    couverture.poste().getPosteId(),
                    couverture.poste().getNomPoste(),
                    couverture.poste().getDirection(),
                    couverture.poste().getCriticite(),
                    couverture.poste().getTitulaireNom(),
                    couverture.nbSuccesseurs(),
                    candidatPotentiel,
                    couverture.meilleurMatching(),
                    couverture.niveau().getLibelle(),
                    couverture.estEnAlerte(),
                    couverture.successeurs().stream().map(PosteCritiqueViewService::successeur).toList()
            ));
        }

        return lignes;
    }

    /** Un successeur tel que CouverturePoste le classe (meilleur matching d'abord). */
    static PosteCritiqueRow.SuccesseurRow successeur(ResultatMatching matching) {
        PlusGrandGap gap = matching.plusGrandGap();
        boolean ecart = gap != null && gap.aUnEcart();
        return new PosteCritiqueRow.SuccesseurRow(matching.candidat().getIdCollaborateur(),
                matching.candidat().getNomComplet(), matching.scoreMatching(), matching.readiness().name(),
                matching.readiness().getLibelle(), ecart ? gap.competence() : null, ecart ? gap.ecart() : 0);
    }
}