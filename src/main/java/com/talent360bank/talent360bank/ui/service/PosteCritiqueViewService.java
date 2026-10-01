package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.PosteCritiqueService;
import com.talent360bank.talent360bank.service.resultat.CouverturePoste;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.ui.model.PosteCritiqueRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Assemble le tableau de l'ecran Postes critiques.
 * Aucun calcul ici : tout vient de PosteCritiqueService.listerPostesCritiques() (Jas).
 */
@Service
public class PosteCritiqueViewService {

    private final PosteCritiqueService posteCritiqueService;
    private final TrimestreRepository trimestreRepository;

    public PosteCritiqueViewService(PosteCritiqueService posteCritiqueService,
                                    TrimestreRepository trimestreRepository) {
        this.posteCritiqueService = posteCritiqueService;
        this.trimestreRepository = trimestreRepository;
    }

    public List<PosteCritiqueRow> buildRows() {
        List<PosteCritiqueRow> lignes = new ArrayList<>();

        Optional<Trimestre> dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        if (dernierTrimestre.isEmpty()) {
            return lignes;
        }

        List<CouverturePoste> couvertures = posteCritiqueService.listerPostesCritiques(dernierTrimestre.get());

        for (CouverturePoste couverture : couvertures) {
            ResultatMatching meilleur = couverture.meilleurSuccesseur();
            String candidatPotentiel = meilleur == null ? "-" : meilleur.candidat().getNomComplet();

            lignes.add(new PosteCritiqueRow(
                    couverture.poste().getNomPoste(),
                    couverture.poste().getDirection(),
                    couverture.poste().getCriticite(),
                    couverture.poste().getTitulaireNom(),
                    couverture.nbSuccesseurs(),
                    candidatPotentiel,
                    couverture.meilleurMatching(),
                    couverture.niveau().getLibelle(),
                    couverture.estEnAlerte()
            ));
        }

        return lignes;
    }
}