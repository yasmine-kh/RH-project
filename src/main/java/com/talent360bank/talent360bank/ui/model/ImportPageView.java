package com.talent360bank.talent360bank.ui.model;

import com.talent360bank.talent360bank.service.resultat.ResultatImport.BilanFeuille;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;

import java.time.LocalDate;
import java.util.List;

/**
 * Page d'import (/import) : le formulaire, le bilan du dernier envoi et le
 * journal des imports.
 *
 * <p>Le bilan ({@link Rapport}) ne depend pas du formulaire qui l'a produit :
 * un futur import d'un dossier de fichiers pourra rendre un rapport par
 * fichier avec le meme modele et le meme fragment de page.
 *
 * @param formulaire  valeurs du formulaire (prefill ou valeurs envoyees)
 * @param rapport     bilan du dernier envoi, nul sur GET
 * @param erreur      envoi refuse avant l'import (fichier, trimestre, date, calcul en cours), nul sinon
 * @param journal     derniers imports, le plus recent en premier
 * @param tailleMax   taille maximale d'un fichier en octets (spring.servlet.multipart.max-file-size), -1 sans limite
 * @param tailleMaxLibelle la meme taille, lisible ("10 Mo")
 */
public record ImportPageView(Formulaire formulaire, Rapport rapport, String erreur, List<LigneJournal> journal,
                             long tailleMax, String tailleMaxLibelle) {

    /**
     * Champs du formulaire, en texte : une valeur mal saisie est renvoyee telle
     * quelle avec le message d'erreur.
     *
     * @param dateReference date de reference du trimestre (aaaa-mm-jj), vide = fin du trimestre
     */
    public record Formulaire(String annee, String numero, boolean simulation, boolean calcul,
                             String dateReference) {
    }

    /**
     * Bilan d'un import, tel que rendu par l'API (ImportResponse), pour la page.
     *
     * @param trimestreValeur  "2026-3", pour le lien vers le tableau de bord
     * @param statut           SUCCES, PARTIEL ou ECHEC
     * @param message          cause d'un echec complet, nul sinon
     * @param calcul           bilan du calcul, nul s'il n'a pas ete lance ou a echoue
     * @param erreurCalcul     cause de l'echec du calcul ; l'import reste enregistre
     * @param dateReference    date de reference posee sur le trimestre par ce formulaire, nulle sinon
     * @param trimestreVide    l'import a echoue apres avoir ouvert un trimestre qui n'existait pas :
     *                         il reste vide (AUDIT_REPORT B5)
     * @param lienTableauDeBord "/?trimestre=AAAA-N" apres un import calcule, nul sinon
     */
    public record Rapport(String nomFichier, String trimestreLibelle, String trimestreValeur, boolean simulation,
                          String statut, int nbLignes, int nbErreurs, List<BilanFeuille> feuilles,
                          List<ErreurImport> erreurs, List<String> desactives, List<String> reactives,
                          String message, Calcul calcul, String erreurCalcul, LocalDate dateReference,
                          boolean trimestreVide, String lienTableauDeBord) {

        public String statutBadge() {
            return switch (statut) {
                case "SUCCES" -> "bg-success";
                case "PARTIEL" -> "bg-warning text-dark";
                default -> "bg-danger";
            };
        }
    }

    /**
     * Bilan du calcul lance apres l'import.
     *
     * @param scores            scores calcules
     * @param scoresIgnores     collaborateurs sans score (notes manquantes...)
     * @param placements        placements 9-Box
     * @param placementsIgnores scores non places
     * @param vivierReleve      membres du vivier de releve
     * @param ignores           motifs des collaborateurs ignores au calcul des scores
     */
    public record Calcul(int scores, int scoresIgnores, int placements, int placementsIgnores, int vivierReleve,
                         List<String> ignores) {
    }

    /**
     * Une entree du journal ImportExcel.
     *
     * @param trimestre   "T3 2026", nul si l'import a echoue avant de l'ouvrir
     * @param utilisateur compte RH qui a lance l'import ; nul tant que l'import ne le renseigne pas
     */
    public record LigneJournal(Integer idImport, LocalDate dateImport, String nomFichier, String trimestre,
                               String statut, Integer nbLignes, Integer nbErreurs, String utilisateur,
                               String message) {
    }
}
