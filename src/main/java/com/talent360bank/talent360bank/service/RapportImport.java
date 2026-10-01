package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.service.resultat.ResultatImport.BilanFeuille;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compteurs et erreurs d'un import en cours, feuille par feuille. Rempli par
 * {@link ImportClasseurService}, transforme en ResultatImport par
 * {@link ImportService}.
 */
public class RapportImport {

    private static final class Compteur {
        private final boolean presente;
        private int nbLignes;
        private int nbErreurs;
        private int nbRetirees;

        private Compteur(boolean presente) {
            this.presente = presente;
        }
    }

    private final Map<String, Compteur> feuilles = new LinkedHashMap<>();
    private final List<ErreurImport> erreurs = new ArrayList<>();
    private final List<String> desactives = new ArrayList<>();
    private final List<String> reactives = new ArrayList<>();

    void feuilleLue(String feuille) {
        feuilles.put(feuille, new Compteur(true));
    }

    void feuilleAbsente(String feuille, String message) {
        Compteur compteur = new Compteur(false);
        compteur.nbErreurs++;
        feuilles.put(feuille, compteur);
        erreurs.add(new ErreurImport(feuille, null, message));
    }

    void ligneImportee(String feuille) {
        feuilles.get(feuille).nbLignes++;
    }

    /** Erreur sur une ligne, numerotee comme dans Excel. */
    void erreur(String feuille, Row ligne, String message) {
        feuilles.get(feuille).nbErreurs++;
        erreurs.add(new ErreurImport(feuille, ligne.getRowNum() + 1, message));
    }

    /** Erreur sur une feuille lue, sans ligne precise. */
    void erreurFeuille(String feuille, String message) {
        feuilles.get(feuille).nbErreurs++;
        erreurs.add(new ErreurImport(feuille, null, message));
    }

    /** Lignes du trimestre supprimees parce que leur collaborateur a quitte la feuille. */
    void lignesRetirees(String feuille, int nombre) {
        feuilles.get(feuille).nbRetirees += nombre;
    }

    /** Collaborateur absent de 01_COLLABORATEURS, passe INACTIF. */
    void desactive(String idCollaborateur) {
        desactives.add(idCollaborateur);
    }

    /** Collaborateur qui n'etait pas actif et que le fichier remet ACTIF. */
    void reactive(String idCollaborateur) {
        reactives.add(idCollaborateur);
    }

    public List<String> desactives() {
        return List.copyOf(desactives);
    }

    public List<String> reactives() {
        return List.copyOf(reactives);
    }

    public int nbLignes() {
        return feuilles.values().stream().mapToInt(compteur -> compteur.nbLignes).sum();
    }

    public int nbErreurs() {
        return erreurs.size();
    }

    public List<BilanFeuille> bilans() {
        List<BilanFeuille> bilans = new ArrayList<>();
        feuilles.forEach((nom, compteur) ->
                bilans.add(new BilanFeuille(nom, compteur.presente, compteur.nbLignes, compteur.nbErreurs,
                        compteur.nbRetirees)));
        return bilans;
    }

    public List<ErreurImport> erreurs() {
        return List.copyOf(erreurs);
    }
}
