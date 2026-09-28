package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.NineBoxCell;
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
 * Matrice9BoxRepository (Dou), le placement de chaque employe vient du
 * champ Score.positionBox, deja rempli par NeufBoxService (Jas).
 */
@Service
public class NineBoxService {

    private final Matrice9BoxRepository matriceRepository;
    private final ScoreRepository scoreRepository;
    private final TrimestreRepository trimestreRepository;

    public NineBoxService(Matrice9BoxRepository matriceRepository,
                          ScoreRepository scoreRepository,
                          TrimestreRepository trimestreRepository) {
        this.matriceRepository = matriceRepository;
        this.scoreRepository = scoreRepository;
        this.trimestreRepository = trimestreRepository;
    }

    public List<NineBoxCell> buildGrid() {
        Map<String, List<String>> employesParCategorie = new HashMap<>();

        Optional<Trimestre> dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc();
        if (dernierTrimestre.isPresent()) {
            for (Score score : scoreRepository.findByTrimestreAvecEmploye(dernierTrimestre.get())) {
                if (score.getPositionBox() != null) {
                    Employe employe = score.getEmploye();
                    String nomComplet = employe.getPrenom() + " " + employe.getNom();
                    employesParCategorie
                            .computeIfAbsent(score.getPositionBox(), k -> new ArrayList<>())
                            .add(nomComplet);
                }
            }
        }

        List<NineBoxCell> grille = new ArrayList<>();
        for (Matrice9Box box : matriceRepository.findAll()) {
            List<String> employes = employesParCategorie.getOrDefault(box.getCategorie(), List.of());
            grille.add(new NineBoxCell(box.getCategorie(), box.getNiveauPerformance(), box.getNiveauPotentiel(), employes));
        }

        // Tri pour affichage : performance elevee en haut, potentiel faible a gauche
        grille.sort((a, b) -> {
            int cmp = Integer.compare(b.getNiveauPerformance(), a.getNiveauPerformance());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getNiveauPotentiel(), b.getNiveauPotentiel());
        });

        return grille;
    }
}