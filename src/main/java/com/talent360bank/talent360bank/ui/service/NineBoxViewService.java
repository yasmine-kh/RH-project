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
     * La matrice de la page 9-Box : effectifs par case et, pour le panneau de
     * detail, tous les membres de chaque case (nom, entite, scores), par nom.
     * Memes deux lectures que {@link #buildGrid}. Un clic sur une case ouvre son
     * detail ({@code /9box?case=N#detail}).
     *
     * @param trimestre        trimestre affiche, null s'il n'en existe aucun : les 9 cases sont alors vides
     * @param caseSelectionnee case dont le detail est ouvert (1 a 9)
     */
    public MatriceNeufBox buildMatrice(Trimestre trimestre, int caseSelectionnee) {
        String valeur = trimestre == null ? null : TrimestreCourantService.valeur(trimestre);
        Map<String, List<Score>> parCategorie = new HashMap<>();
        int nonPlaces = 0;
        if (trimestre != null) {
            for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
                if (score.getPositionBox() == null) {
                    nonPlaces++;
                } else {
                    parCategorie.computeIfAbsent(score.getPositionBox(), k -> new ArrayList<>()).add(score);
                }
            }
        }
        List<MatriceNeufBox.Entree> entrees = new ArrayList<>();
        for (Matrice9Box box : matriceRepository.findAll()) {
            List<MatriceNeufBox.Membre> membres = parCategorie.getOrDefault(box.getCategorie(), List.of()).stream()
                    .sorted(Comparator.comparing((Score s) -> s.getCollaborateur().getNom())
                            .thenComparing(s -> s.getCollaborateur().getPrenom())
                            .thenComparing(s -> s.getCollaborateur().getIdCollaborateur()))
                    .map(s -> {
                        Collaborateur c = s.getCollaborateur();
                        return new MatriceNeufBox.Membre(c.getIdCollaborateur(), c.getPrenom() + " " + c.getNom(),
                                MatriceNeufBox.lienFiche(c.getIdCollaborateur(), valeur),
                                c.getEntite() == null ? null : c.getEntite().getLibelle(),
                                s.getScorePerformance(), s.getScorePotentiel());
                    })
                    .toList();
            entrees.add(new MatriceNeufBox.Entree(box.getNiveauPerformance(), box.getNiveauPotentiel(),
                    box.getCategorie(), membres.size(), membres));
        }
        return MatriceNeufBox.construire(entrees, nonPlaces, numero -> MatriceNeufBox.lienDetail(valeur, numero),
                caseSelectionnee);
    }
}
