package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.NineBoxCell;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assemble la grille 9-Box pour l'affichage.
 *
 * REGLE : aucun calcul ici. Les 9 cases de reference viennent de
 * Matrice9BoxRepository (Dou), le placement de chaque collaborateur vient du
 * champ Score.positionBox, deja rempli par NeufBoxService (Jas).
 */
@Service
public class NineBoxViewService {

    private final Matrice9BoxRepository matriceRepository;
    private final ScoreRepository scoreRepository;
    private final TrimestreRepository trimestreRepository;

    public NineBoxViewService(Matrice9BoxRepository matriceRepository,
                              ScoreRepository scoreRepository,
                              TrimestreRepository trimestreRepository) {
        this.matriceRepository = matriceRepository;
        this.scoreRepository = scoreRepository;
        this.trimestreRepository = trimestreRepository;
    }

    public List<NineBoxCell> buildGrid() {
        Map<String, List<String>> collaborateursParCategorie = new HashMap<>();

        Optional<Trimestre> dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        if (dernierTrimestre.isPresent()) {
            for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(dernierTrimestre.get())) {
                if (score.getPositionBox() != null) {
                    Collaborateur collaborateur = score.getCollaborateur();
                    String nomComplet = collaborateur.getPrenom() + " " + collaborateur.getNom();
                    collaborateursParCategorie
                            .computeIfAbsent(score.getPositionBox(), k -> new ArrayList<>())
                            .add(nomComplet);
                }
            }
        }

        List<NineBoxCell> grille = new ArrayList<>();
        for (Matrice9Box box : matriceRepository.findAll()) {
            List<String> collaborateurs = collaborateursParCategorie.getOrDefault(box.getCategorie(), List.of());
            grille.add(new NineBoxCell(box.getCategorie(), box.getNiveauPerformance(), box.getNiveauPotentiel(),
                    collaborateurs));
        }

        grille.sort((a, b) -> {
            int cmp = Integer.compare(b.getNiveauPerformance(), a.getNiveauPerformance());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getNiveauPotentiel(), b.getNiveauPotentiel());
        });

        return grille;
    }
}