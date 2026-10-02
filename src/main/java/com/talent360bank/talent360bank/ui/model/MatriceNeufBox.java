package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * La matrice 9-Box prete pour le composant d'affichage commun
 * (fragments/matrice9box.html) : la page 9-Box, l'accueil et le dashboard DG.
 *
 * <p>Aucun calcul de placement ici : chaque case vient de Matrice9Box et les
 * effectifs des scores deja places par NeufBoxService. Seuls la part de la
 * population, la zone de couleur et les liens sont prepares pour l'ecran.
 *
 * @param cases       les 9 cases ligne par ligne : performance elevee en haut, potentiel faible a gauche
 *                    (case 9 en haut a droite, case 1 en bas a gauche)
 * @param total       collaborateurs places dans la matrice
 * @param nbNonPlaces scores du trimestre sans case (placement non lance ou scores incomplets)
 * @param avecNoms    vrai si les cases portent les noms (page 9-Box), faux pour les seuls effectifs
 */
public record MatriceNeufBox(List<CaseMatrice> cases, int total, int nbNonPlaces, boolean avecNoms) {

    /** Noms affiches au plus par case ; les autres sont dans la liste des collaborateurs de la case. */
    public static final int NOMS_MAX = 6;

    /**
     * @param numero      1 a 9, (rang performance - 1) x 3 + rang potentiel, comme 04_9BOX!H
     * @param pourcentage effectif / total x 100, 1 decimale ; null si la matrice est vide
     * @param zone        classe de couleur : excellent, fort, neutre, attention, risque
     * @param membres     au plus {@link #NOMS_MAX} collaborateurs, par nom ; vide sans noms
     * @param nbAutres    collaborateurs de la case non listes
     * @param lienListe   la liste des collaborateurs de cette case, meme trimestre
     */
    public record CaseMatrice(int numero, String libelle, int niveauPerformance, int niveauPotentiel, int nombre,
                              BigDecimal pourcentage, String zone, List<Membre> membres, int nbAutres,
                              String lienListe) {
    }

    /** @param lien la fiche du collaborateur, meme trimestre */
    public record Membre(String matricule, String nomComplet, String lien) {
    }

    /** Une case avant mise en forme : niveaux 1 a 3, libelle et membres (tous). */
    public record Entree(int niveauPerformance, int niveauPotentiel, String libelle, int nombre, List<Membre> membres) {
    }

    /** Matrice a partir des cases ; l'ordre d'affichage est refait ici. */
    public static MatriceNeufBox construire(List<Entree> entrees, int nbNonPlaces, boolean avecNoms, String trimestre) {
        int total = entrees.stream().mapToInt(Entree::nombre).sum();
        List<CaseMatrice> cases = new ArrayList<>();
        for (Entree entree : entrees.stream()
                .sorted(Comparator.comparing(Entree::niveauPerformance, Comparator.reverseOrder())
                        .thenComparing(Entree::niveauPotentiel))
                .toList()) {
            int numero = (entree.niveauPerformance() - 1) * 3 + entree.niveauPotentiel();
            List<Membre> affiches = avecNoms
                    ? entree.membres().stream().limit(NOMS_MAX).toList()
                    : List.of();
            cases.add(new CaseMatrice(numero, entree.libelle(), entree.niveauPerformance(), entree.niveauPotentiel(),
                    entree.nombre(),
                    total == 0 ? null : BigDecimal.valueOf(entree.nombre() * 100L)
                            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP),
                    zone(entree.niveauPerformance(), entree.niveauPotentiel()), affiches,
                    avecNoms ? entree.nombre() - affiches.size() : 0, lienListe(trimestre, numero)));
        }
        return new MatriceNeufBox(List.copyOf(cases), total, nbNonPlaces, avecNoms);
    }

    /** Effectifs seuls, depuis le tableau de bord (aucune requete de plus). */
    public static MatriceNeufBox depuis(List<CaseTableau> cases, int nbNonPlaces, String trimestre) {
        return construire(cases.stream()
                .map(c -> new Entree(c.niveauPerformance(), c.niveauPotentiel(), c.libelle(), c.nombre(), List.of()))
                .toList(), nbNonPlaces, false, trimestre);
    }

    /**
     * Zone de couleur selon la somme des deux rangs (2 a 6) : les deux eleves = excellent ;
     * un eleve et un moyen = fort ; au milieu = neutre ; un faible et un moyen = attention ;
     * les deux faibles = risque. Comme le prototype (talent / high / neutre / watch).
     */
    static String zone(int niveauPerformance, int niveauPotentiel) {
        return switch (niveauPerformance + niveauPotentiel) {
            case 6 -> "excellent";
            case 5 -> "fort";
            case 4 -> "neutre";
            case 3 -> "attention";
            default -> "risque";
        };
    }

    private static String lienListe(String trimestre, int numero) {
        return UriComponentsBuilder.fromPath("/collaborateurs").queryParam("trimestre", trimestre)
                .queryParam("case", numero).encode().build().toUriString();
    }

    /** La fiche d'un collaborateur sur un trimestre (page, jamais l'API). */
    public static String lienFiche(String matricule, String trimestre) {
        return UriComponentsBuilder.fromPath("/fiche-collaborateur").queryParam("matricule", matricule)
                .queryParam("trimestre", trimestre).encode().build().toUriString();
    }
}
