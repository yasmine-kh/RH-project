package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * La matrice 9-Box prete pour le composant d'affichage commun
 * (fragments/box9.html) : la page 9-Box, l'accueil, le dashboard DG, la
 * vue manager et la vue entite.
 *
 * <p>Aucun calcul de placement ici : chaque case vient de Matrice9Box et les
 * effectifs des scores deja places par NeufBoxService. Seuls la part de la
 * population, le sens de la case et les liens sont prepares pour l'ecran. Les
 * cases n'affichent jamais de noms : sur la page 9-Box, les membres de chaque
 * case vont dans le panneau de detail.
 *
 * @param cases            les 9 cases ligne par ligne : performance elevee en haut, potentiel faible a gauche
 *                         (case 9 en haut a droite, case 1 en bas a gauche)
 * @param total            collaborateurs places dans la matrice
 * @param nbNonPlaces      scores du trimestre sans case (placement non lance ou scores incomplets)
 * @param caseSelectionnee case dont le detail est ouvert (page 9-Box), null ailleurs
 */
public record MatriceNeufBox(List<CaseMatrice> cases, int total, int nbNonPlaces, Integer caseSelectionnee) {

    /**
     * @param numero      1 a 9, (rang performance - 1) x 3 + rang potentiel, comme 04_9BOX!H
     * @param pourcentage effectif / total x 100, 1 decimale ; null si la matrice est vide
     * @param zone        position dans la matrice (excellent, fort, neutre, attention, risque) ; pas une couleur
     * @param sens        ce que la case veut dire, d'apres la regle de placement (04_9BOX n'a pas de description)
     * @param membres     tous les collaborateurs de la case, par nom (panneau de la page 9-Box) ; vide ailleurs
     * @param lien        ou mene un clic sur la case : son detail sur la page 9-Box, la liste des collaborateurs
     *                    de la case ailleurs (memes filtres) ; null sans lien
     */
    public record CaseMatrice(int numero, String libelle, int niveauPerformance, int niveauPotentiel, int nombre,
                              BigDecimal pourcentage, String zone, String sens, List<Membre> membres, String lien) {
    }

    /**
     * @param lien   la fiche du collaborateur, meme trimestre
     * @param entite libelle de son entite, null si inconnue
     */
    public record Membre(String matricule, String nomComplet, String lien, String entite, BigDecimal performance,
                         BigDecimal potentiel) {
    }

    /** Une case avant mise en forme : niveaux 1 a 3, libelle, effectif et membres (vide pour les seuls effectifs). */
    public record Entree(int niveauPerformance, int niveauPotentiel, String libelle, int nombre, List<Membre> membres) {
    }

    /** La case ouverte, null s'il n'y en a pas. */
    public CaseMatrice selection() {
        return caseSelectionnee == null ? null
                : cases.stream().filter(c -> c.numero() == caseSelectionnee).findFirst().orElse(null);
    }

    /**
     * Matrice a partir des cases ; l'ordre d'affichage est refait ici.
     *
     * @param lien lien du clic sur chaque case (par numero), null pour aucun
     */
    public static MatriceNeufBox construire(List<Entree> entrees, int nbNonPlaces, IntFunction<String> lien,
                                            Integer caseSelectionnee) {
        int total = entrees.stream().mapToInt(Entree::nombre).sum();
        List<CaseMatrice> cases = new ArrayList<>();
        for (Entree entree : entrees.stream()
                .sorted(Comparator.comparing(Entree::niveauPerformance, Comparator.reverseOrder())
                        .thenComparing(Entree::niveauPotentiel))
                .toList()) {
            int numero = (entree.niveauPerformance() - 1) * 3 + entree.niveauPotentiel();
            cases.add(new CaseMatrice(numero, entree.libelle(), entree.niveauPerformance(), entree.niveauPotentiel(),
                    entree.nombre(),
                    total == 0 ? null : BigDecimal.valueOf(entree.nombre() * 100L)
                            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP),
                    zone(entree.niveauPerformance(), entree.niveauPotentiel()),
                    sens(entree.niveauPerformance(), entree.niveauPotentiel()),
                    List.copyOf(entree.membres()), lien == null ? null : lien.apply(numero)));
        }
        return new MatriceNeufBox(List.copyOf(cases), total, nbNonPlaces, caseSelectionnee);
    }

    /**
     * Effectifs seuls d'un groupe (equipe d'un manager, sous-arbre d'une entite) a partir
     * de sa synthese par numero de case ; les niveaux se deduisent du numero.
     *
     * @param lien lien du clic sur une case, null pour aucun
     */
    public static MatriceNeufBox depuisComptes(List<VueManager.CompteCase> comptes, IntFunction<String> lien) {
        return construire(comptes.stream()
                .map(c -> new Entree((c.numero() - 1) / 3 + 1, (c.numero() - 1) % 3 + 1, c.libelle(), c.nombre(),
                        List.of()))
                .toList(), 0, lien, null);
    }

    /**
     * Position de la case selon la somme des deux rangs (2 a 6) : les deux eleves = excellent ;
     * un eleve et un moyen = fort ; au milieu = neutre ; un faible et un moyen = attention ;
     * les deux faibles = risque. Une information, pas une couleur d'affichage.
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

    /** "Performance elevee et potentiel moyen." : la regle de placement de la case, rien de plus. */
    static String sens(int niveauPerformance, int niveauPotentiel) {
        Map<Integer, String> performance = Map.of(1, "faible", 2, "moyenne", 3, "élevée");
        Map<Integer, String> potentiel = Map.of(1, "faible", 2, "moyen", 3, "élevé");
        return "Performance " + performance.get(niveauPerformance) + " et potentiel "
                + potentiel.get(niveauPotentiel) + ".";
    }

    /** La liste des collaborateurs d'une case, eventuellement limitee a une entite (sous-arbre). */
    public static String lienListe(String trimestre, String entite, int numero) {
        UriComponentsBuilder lien = UriComponentsBuilder.fromPath("/collaborateurs").queryParam("trimestre", trimestre);
        if (entite != null) {
            lien.queryParam("entite", entite);
        }
        return lien.queryParam("case", numero).encode().build().toUriString();
    }

    /** Le detail d'une case sur la page 9-Box (sans JavaScript, la page se recharge sur cette case). */
    public static String lienDetail(String trimestre, int numero) {
        return UriComponentsBuilder.fromPath("/9box").queryParam("trimestre", trimestre).queryParam("case", numero)
                .fragment("detail").encode().build().toUriString();
    }

    /** La fiche d'un collaborateur sur un trimestre (page, jamais l'API). */
    public static String lienFiche(String matricule, String trimestre) {
        return UriComponentsBuilder.fromPath("/fiche-collaborateur").queryParam("matricule", matricule)
                .queryParam("trimestre", trimestre).encode().build().toUriString();
    }
}
