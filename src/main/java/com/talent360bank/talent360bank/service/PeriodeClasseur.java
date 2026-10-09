package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.excel.Cellules;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Periode que le classeur annonce lui-meme, pour refuser un fichier importe
 * sur le mauvais trimestre.
 *
 * <p>Sources lues : le nom du fichier, et les lignes de titre et de
 * commentaire (1 a 3) de chaque feuille. Le jeu de donnees actuel porte sa
 * date en 00_DASHBOARD A2 ("... au 15/09/2026", soit T3 2026). Formes
 * reconnues : "T3 2026", "T3_2026", "Q3-2026", "2026-T3", "2026Q3",
 * "3e trimestre 2026", "trimestre 3 2026", et une date jj/mm/aaaa, rattachee
 * au trimestre qui la contient.
 */
public final class PeriodeClasseur {

    /** Titre et commentaire : les donnees commencent sous l'en-tete, en ligne 4 ou 5. */
    private static final int LIGNES_DE_TITRE = 3;

    private static final String ANNEE = "((?:19|20)\\d{2})";
    private static final Pattern T_ANNEE = Pattern.compile(
            "(?i)(?<![a-z0-9])[TQ]([1-4])[\\s_\\-/]*" + ANNEE + "(?!\\d)");
    private static final Pattern ANNEE_T = Pattern.compile(
            "(?i)(?<!\\d)" + ANNEE + "[\\s_\\-/]*[TQ]([1-4])(?![a-z0-9])");
    private static final Pattern NIEME_TRIMESTRE = Pattern.compile(
            "(?i)(?<!\\d)([1-4])\\s*(?:er|e|eme|ème)?\\s+trimestre\\s+(?:de\\s+)?" + ANNEE + "(?!\\d)");
    private static final Pattern TRIMESTRE_N = Pattern.compile(
            "(?i)trimestre\\s*([1-4])[\\s_\\-/]+" + ANNEE + "(?!\\d)");
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(\\d{1,2})/(\\d{1,2})/" + ANNEE + "(?!\\d)");

    /** Periode trouvee et d'ou elle vient ("nom du fichier", "00_DASHBOARD A2"...). */
    public record Periode(int annee, int numero, String source, String texte) {

        public String libelle() {
            return "T" + numero + " " + annee;
        }
    }

    private PeriodeClasseur() {
    }

    /** Toutes les periodes annoncees, dans l'ordre : nom du fichier, puis feuille par feuille. */
    public static List<Periode> lire(String nomFichier, Workbook classeur) {
        List<Periode> periodes = new ArrayList<>();
        if (nomFichier != null) {
            String sansExtension = nomFichier.replaceFirst("\\.[A-Za-z0-9]+$", "");
            chercher(sansExtension, "nom du fichier", false, periodes);
        }
        for (int i = 0; i < classeur.getNumberOfSheets(); i++) {
            Sheet feuille = classeur.getSheetAt(i);
            for (int r = 0; r < LIGNES_DE_TITRE; r++) {
                Row ligne = feuille.getRow(r);
                if (ligne == null) {
                    continue;
                }
                for (int c = 0; c < Math.max(ligne.getLastCellNum(), 0); c++) {
                    String texte = texteOuNul(ligne, c);
                    if (texte != null) {
                        chercher(texte, feuille.getSheetName() + " " + Cellules.lettre(c) + (r + 1), true, periodes);
                    }
                }
            }
        }
        return periodes;
    }

    /** Feuille et cellule ou le classeur ecrit sa date ("... au 15/09/2026"). */
    public static final String FEUILLE_DATE = "00_DASHBOARD";
    public static final String CELLULE_DATE = "A2";

    /**
     * Date du classeur lu dans un fichier envoye.
     *
     * @param lisible faux si le fichier n'est pas un classeur lisible (l'import le refusera de lui-meme)
     * @param date    date de 00_DASHBOARD A2, null si le classeur n'en ecrit pas (ou une date impossible)
     */
    public record DateClasseur(boolean lisible, LocalDate date) {
    }

    /** Lit la date de 00_DASHBOARD A2 dans le fichier envoye, sans rien importer. */
    public static DateClasseur dateDuFichier(MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream(); Workbook classeur = WorkbookFactory.create(flux)) {
            return new DateClasseur(true, date(classeur));
        } catch (Exception e) {
            return new DateClasseur(false, null);
        }
    }

    /** La date ecrite en 00_DASHBOARD A2 ("au 15/09/2026"), null si absente ou impossible. */
    public static LocalDate date(Workbook classeur) {
        Sheet feuille = classeur.getSheet(FEUILLE_DATE);
        Row ligne = feuille == null ? null : feuille.getRow(1);
        String texte = ligne == null ? null : texteOuNul(ligne, 0);
        Matcher m = texte == null ? null : DATE.matcher(texte);
        if (m == null || !m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Periodes qui ne sont pas le trimestre demande. */
    public static List<Periode> discordantes(List<Periode> periodes, int annee, int numero) {
        return periodes.stream().filter(p -> p.annee() != annee || p.numero() != numero).toList();
    }

    private static void chercher(String texte, String source, boolean dates, List<Periode> periodes) {
        for (Pattern forme : List.of(T_ANNEE, NIEME_TRIMESTRE, TRIMESTRE_N)) {
            Matcher m = forme.matcher(texte);
            while (m.find()) {
                periodes.add(new Periode(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)), source,
                        m.group()));
            }
        }
        Matcher m = ANNEE_T.matcher(texte);
        while (m.find()) {
            periodes.add(new Periode(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), source, m.group()));
        }
        if (dates) {
            m = DATE.matcher(texte);
            while (m.find()) {
                int mois = Integer.parseInt(m.group(2));
                if (mois >= 1 && mois <= 12) {
                    periodes.add(new Periode(Integer.parseInt(m.group(3)), (mois - 1) / 3 + 1, source, m.group()));
                }
            }
        }
    }

    private static String texteOuNul(Row ligne, int colonne) {
        try {
            return Cellules.texte(ligne, colonne);
        } catch (Cellules.ValeurIllisibleException e) {
            return null;
        }
    }
}
