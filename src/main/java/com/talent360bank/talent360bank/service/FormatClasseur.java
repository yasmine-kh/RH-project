package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.excel.Cellules;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.talent360bank.talent360bank.service.ImportClasseurService.*;

/**
 * Format attendu du classeur TALENT_360_BANK_Dataset : les feuilles lues par
 * l'import et, pour chacune, l'en-tete de chaque colonne lue.
 *
 * <p>Verifie avant toute ecriture : un classeur auquel manque une feuille ou
 * dont une colonne a ete renommee ou deplacee est refuse en entier, avec la
 * liste de ce qui manque, plutot qu'importe a moitie. Les colonnes calculees
 * par le classeur (scores, categories...) ne sont pas controlees : l'import
 * ne les lit pas.
 *
 * <p>La comparaison des en-tetes ignore la casse, les accents et les espaces
 * en trop.
 */
public final class FormatClasseur {

    /** En-tetes attendus par feuille : index de colonne (0 = A) vers libelle. */
    static final Map<String, Map<Integer, String>> ENTETES = new LinkedHashMap<>();

    static {
        attendre(FEUILLE_COLLABORATEURS, "Employee_ID", "Nom", "Prenom", "Sexe", "Date_Naissance", "Date_Entree",
                null, "Direction", "Departement", "Region", "Agence / Site", "Fonction", "Grade", "Manager_ID",
                "Statut");
        attendre(FEUILLE_PERFORMANCE, "Employee_ID", null, null, "Objectifs (/100)", "Competences (/100)",
                "Comportement (/100)", "Contribution (/100)", "Developpement (/100)");
        attendre(FEUILLE_POTENTIEL, "Employee_ID", null, null, "Agilite apprentissage", "Leadership",
                "Adaptabilite", "Gestion complexite", "Mobilite", "Vision strategique", "Autonomie");
        attendre(FEUILLE_COMPETENCES, "Competence_ID", "Nom", "Categorie");
        attendre(FEUILLE_SKILLS, "Cle (lookup)", "Employee_ID", "Competence", null, "Niveau Actuel (1-5)",
                "Niveau Cible (1-5)", "Gap", "Statut Gap");
        attendre(FEUILLE_POSTES, "Poste_ID", "Nom du poste", "Direction", "Grade cible", "Criticite",
                "Competence requise 1", "Niveau 1", "Competence requise 2", "Niveau 2", "Competence requise 3",
                "Niveau 3", "Competence requise 4", "Niveau 4", "Competence requise 5", "Niveau 5",
                "Poste critique ?");
        attendre(FEUILLE_POSTES_CRITIQUES, "Poste_ID", null, null, null, "Titulaire_ID", "Titulaire (Nom)");
        attendre(FEUILLE_SUCCESSION, "Poste_ID", "Employee_ID");
        attendre(FEUILLE_TALENTS, "Employee_ID", null, "Direction", null, null, null, "Validation Comite Talent",
                null, "Vivier thematique");
        attendre(FEUILLE_VIGILANCE, "Employee_ID", null, null, "Engagement (/100)", null, "Aucun mouvement 4 ans",
                "Mobilite non traitee", "Aucun dev. recent", "Baisse performance", "Faible reconnaissance",
                "Formation non realisee");
    }

    private FormatClasseur() {
    }

    private static void attendre(String feuille, String... libelles) {
        Map<Integer, String> colonnes = new LinkedHashMap<>();
        for (int i = 0; i < libelles.length; i++) {
            if (libelles[i] != null) {
                colonnes.put(i, libelles[i]);
            }
        }
        ENTETES.put(feuille, colonnes);
    }

    /**
     * Ecarts entre le classeur et le format attendu, une erreur par feuille
     * absente ou par colonne manquante ; liste vide si le classeur est
     * conforme.
     */
    public static List<ErreurImport> verifier(Workbook classeur) {
        List<ErreurImport> ecarts = new ArrayList<>();
        ENTETES.forEach((nom, colonnes) -> verifierFeuille(classeur, nom, colonnes, ecarts));
        return ecarts;
    }

    private static void verifierFeuille(Workbook classeur, String nom, Map<Integer, String> colonnes,
                                        List<ErreurImport> ecarts) {
        Sheet feuille = classeur.getSheet(nom);
        if (feuille == null) {
            String semblable = feuilleSemblable(classeur, nom);
            ecarts.add(new ErreurImport(nom, null, semblable == null
                    ? "Feuille absente du classeur"
                    : "Feuille absente du classeur (renommée en \"" + semblable + "\" ?)"));
            return;
        }
        String colonneA = colonnes.get(0);
        int entete = Cellules.ligneEntete(feuille, colonneA);
        if (entete < 0) {
            ecarts.add(new ErreurImport(nom, null, "En-tête introuvable : la colonne A de la ligne d'en-tête doit "
                    + "porter \"" + colonneA + "\""));
            return;
        }
        Row ligne = feuille.getRow(entete);
        colonnes.forEach((index, attendu) -> {
            String trouve = libelle(ligne, index);
            if (normaliser(attendu).equals(normaliser(trouve))) {
                return;
            }
            int ailleurs = colonneDe(ligne, attendu);
            String message = "colonne " + Cellules.lettre(index) + " : en-tête \"" + attendu + "\" attendu, "
                    + (trouve == null ? "cellule vide" : "trouvé \"" + trouve + "\"")
                    + (ailleurs >= 0 ? " (colonne déplacée en " + Cellules.lettre(ailleurs) + ")" : "");
            ecarts.add(new ErreurImport(nom, entete + 1, message));
        });
    }

    /** Une feuille dont le nom commence par le meme prefixe numerote (01_, 02_...), s'il y en a une. */
    private static String feuilleSemblable(Workbook classeur, String nom) {
        String prefixe = nom.substring(0, nom.indexOf('_') + 1);
        for (int i = 0; i < classeur.getNumberOfSheets(); i++) {
            String autre = classeur.getSheetName(i);
            if (autre.startsWith(prefixe) || normaliser(autre).equals(normaliser(nom))) {
                return autre;
            }
        }
        return null;
    }

    private static String libelle(Row ligne, int index) {
        try {
            return Cellules.texte(ligne, index);
        } catch (Cellules.ValeurIllisibleException e) {
            return null;
        }
    }

    private static int colonneDe(Row ligne, String attendu) {
        for (int i = 0; i < Math.max(ligne.getLastCellNum(), 0); i++) {
            if (normaliser(attendu).equals(normaliser(libelle(ligne, i)))) {
                return i;
            }
        }
        return -1;
    }

    private static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
