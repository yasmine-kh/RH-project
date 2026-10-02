package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.MatriceNeufBox;
import com.talent360bank.talent360bank.ui.model.NineBoxCell;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public NineBoxViewService(Matrice9BoxRepository matriceRepository,
                              ScoreRepository scoreRepository) {
        this.matriceRepository = matriceRepository;
        this.scoreRepository = scoreRepository;
    }

    /**
     * @param trimestre trimestre affiche (TrimestreCourantService), null s'il n'en existe aucun :
     *                  les 9 cases sont alors vides
     */
    public List<NineBoxCell> buildGrid(Trimestre trimestre) {
        Map<String, List<String>> collaborateursParCategorie = new HashMap<>();

        if (trimestre != null) {
            for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
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

    /**
     * La matrice du composant commun (fragments/matrice9box.html), avec les noms
     * de chaque case, par nom : memes deux lectures que {@link #buildGrid}.
     *
     * @param trimestre trimestre affiche, null s'il n'en existe aucun : les 9 cases sont alors vides
     */
    public MatriceNeufBox buildMatrice(Trimestre trimestre) {
        String valeur = trimestre == null ? null : TrimestreCourantService.valeur(trimestre);
        Map<String, List<Collaborateur>> parCategorie = new HashMap<>();
        int nonPlaces = 0;
        if (trimestre != null) {
            for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
                if (score.getPositionBox() == null) {
                    nonPlaces++;
                } else {
                    parCategorie.computeIfAbsent(score.getPositionBox(), k -> new ArrayList<>())
                            .add(score.getCollaborateur());
                }
            }
        }
        List<MatriceNeufBox.Entree> entrees = new ArrayList<>();
        for (Matrice9Box box : matriceRepository.findAll()) {
            List<MatriceNeufBox.Membre> membres = parCategorie.getOrDefault(box.getCategorie(), List.of()).stream()
                    .sorted(Comparator.comparing(Collaborateur::getNom).thenComparing(Collaborateur::getPrenom)
                            .thenComparing(Collaborateur::getIdCollaborateur))
                    .map(c -> new MatriceNeufBox.Membre(c.getIdCollaborateur(), c.getPrenom() + " " + c.getNom(),
                            MatriceNeufBox.lienFiche(c.getIdCollaborateur(), valeur)))
                    .toList();
            entrees.add(new MatriceNeufBox.Entree(box.getNiveauPerformance(), box.getNiveauPotentiel(),
                    box.getCategorie(), membres.size(), membres));
        }
        return MatriceNeufBox.construire(entrees, nonPlaces, true, valeur);
    }
}
