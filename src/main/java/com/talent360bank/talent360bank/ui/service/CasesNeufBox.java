package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.NiveauGrille;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Case 9-box d'un score enregistre, pour la fiche collaborateur et la vue
 * manager : numero deduit des niveaux par la regle du placement
 * ({@link NeufBoxService#niveauxDe}, reglages du trimestre du score), jamais
 * du libelle enregistre ; libelle actuel de la case dans la matrice (un
 * renommage s'y voit), a defaut celui enregistre.
 *
 * <p>Sans acces base : la matrice (9 lignes) est lue une fois par l'appelant.
 */
final class CasesNeufBox {

    private final NeufBoxService neufBoxService;
    /** Libelle actuel de chaque case, par "niveau performance/niveau potentiel". */
    private final Map<String, String> libelles = new HashMap<>();

    CasesNeufBox(NeufBoxService neufBoxService, List<Matrice9Box> matrice) {
        this.neufBoxService = neufBoxService;
        for (Matrice9Box case9Box : matrice) {
            libelles.putIfAbsent(cle(case9Box.getNiveauPerformance(), case9Box.getNiveauPotentiel()),
                    case9Box.getCategorie());
        }
    }

    /**
     * @throws com.talent360bank.talent360bank.exception.DonneesIncompletesException
     *         si un score ou les seuils manquent
     */
    CaseNeufBox caseDe(Score score, Parametre parametre) {
        NiveauGrille[] niveaux = neufBoxService.niveauxDe(score, parametre);
        String libelle = libelles.getOrDefault(cle(niveaux[0].getRang(), niveaux[1].getRang()),
                score.getPositionBox());
        return new CaseNeufBox(NeufBoxService.numeroCase(niveaux[0], niveaux[1]), libelle);
    }

    /** Libelle actuel de la case numero 1 a 9, null si la matrice ne la contient pas. */
    String libelle(int numero) {
        int performance = (numero - 1) / 3 + 1;
        int potentiel = (numero - 1) % 3 + 1;
        return libelles.get(cle(performance, potentiel));
    }

    private static String cle(int niveauPerformance, int niveauPotentiel) {
        return niveauPerformance + "/" + niveauPotentiel;
    }
}
