package com.talent360bank.talent360bank.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecture des cellules du classeur TALENT 360 BANK pour l'import.
 *
 * <p>Plusieurs colonnes du classeur sont des formules : leur resultat en cache
 * est lu, jamais la formule. Aucune cellule n'est modifiee (pas de
 * setCellType). Une valeur illisible leve {@link ValeurIllisibleException}
 * avec un message pour le RH, que l'import range dans ses erreurs de ligne.
 */
public final class Cellules {

    /** Nombre de lignes parcourues pour trouver l'en-tete d'une feuille. */
    private static final int LIGNES_MAX_AVANT_ENTETE = 10;

    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    private Cellules() {
    }

    /** Valeur d'une cellule que l'import ne sait pas interpreter. */
    public static class ValeurIllisibleException extends RuntimeException {
        public ValeurIllisibleException(String message) {
            super(message);
        }
    }

    /**
     * Index de la ligne d'en-tete : la premiere dont la colonne A porte le
     * libelle attendu (casse et espaces ignores), -1 si aucune.
     */
    public static int ligneEntete(Sheet feuille, String libelleColonneA) {
        int derniere = Math.min(feuille.getLastRowNum(), LIGNES_MAX_AVANT_ENTETE);
        for (int i = feuille.getFirstRowNum(); i <= derniere; i++) {
            Row ligne = feuille.getRow(i);
            if (ligne != null && libelleColonneA.equalsIgnoreCase(brut(ligne.getCell(0)))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Lignes de donnees sous l'en-tete, jusqu'a la premiere ligne dont la
     * colonne A est vide. Ce qui suit (legende de 05, note de 09) n'est pas
     * une donnee.
     */
    public static List<Row> lignesDonnees(Sheet feuille, int ligneEntete) {
        List<Row> lignes = new ArrayList<>();
        for (int i = ligneEntete + 1; i <= feuille.getLastRowNum(); i++) {
            Row ligne = feuille.getRow(i);
            if (ligne == null || brut(ligne.getCell(0)) == null) {
                break;
            }
            lignes.add(ligne);
        }
        return lignes;
    }

    /** Texte de la cellule, nul si vide. Un nombre entier est rendu sans decimales. */
    public static String texte(Row ligne, int colonne) {
        return brut(ligne.getCell(colonne));
    }

    /** Nombre de la cellule, nul si vide. */
    public static BigDecimal nombre(Row ligne, int colonne) {
        Cell cellule = ligne.getCell(colonne);
        CellType type = type(cellule);
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cellule.getNumericCellValue());
        }
        String texte = brut(cellule);
        if (texte == null) {
            return null;
        }
        try {
            return new BigDecimal(texte.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValeurIllisibleException(
                    "colonne " + lettre(colonne) + " : nombre attendu, lu \"" + texte + "\"");
        }
    }

    /** Entier de la cellule, nul si vide. */
    public static Integer entier(Row ligne, int colonne) {
        BigDecimal valeur = nombre(ligne, colonne);
        if (valeur == null) {
            return null;
        }
        try {
            return valeur.intValueExact();
        } catch (ArithmeticException e) {
            throw new ValeurIllisibleException(
                    "colonne " + lettre(colonne) + " : entier attendu, lu " + valeur.toPlainString());
        }
    }

    /** Date de la cellule (date Excel, AAAA-MM-JJ ou JJ/MM/AAAA), nulle si vide. */
    public static LocalDate date(Row ligne, int colonne) {
        Cell cellule = ligne.getCell(colonne);
        if (type(cellule) == CellType.NUMERIC) {
            return DateUtil.getLocalDateTime(cellule.getNumericCellValue()).toLocalDate();
        }
        String texte = brut(cellule);
        if (texte == null) {
            return null;
        }
        for (DateTimeFormatter format : FORMATS_DATE) {
            try {
                return LocalDate.parse(texte, format);
            } catch (DateTimeParseException e) {
                // format suivant
            }
        }
        throw new ValeurIllisibleException(
                "colonne " + lettre(colonne) + " : date attendue, lu \"" + texte + "\"");
    }

    /** Oui / Non de la cellule, nul si vide. */
    public static Boolean ouiNon(Row ligne, int colonne) {
        String texte = texte(ligne, colonne);
        if (texte == null) {
            return null;
        }
        if ("oui".equalsIgnoreCase(texte)) {
            return true;
        }
        if ("non".equalsIgnoreCase(texte)) {
            return false;
        }
        throw new ValeurIllisibleException(
                "colonne " + lettre(colonne) + " : Oui ou Non attendu, lu \"" + texte + "\"");
    }

    /** Lettre de colonne Excel (0 -> A), pour les messages. */
    public static String lettre(int colonne) {
        StringBuilder lettres = new StringBuilder();
        for (int reste = colonne; reste >= 0; reste = reste / 26 - 1) {
            lettres.insert(0, (char) ('A' + reste % 26));
        }
        return lettres.toString();
    }

    private static String brut(Cell cellule) {
        CellType type = type(cellule);
        String valeur = switch (type) {
            case STRING -> cellule.getStringCellValue();
            case NUMERIC -> {
                BigDecimal nombre = BigDecimal.valueOf(cellule.getNumericCellValue()).stripTrailingZeros();
                yield nombre.scale() <= 0 ? nombre.toBigInteger().toString() : nombre.toPlainString();
            }
            case BOOLEAN -> String.valueOf(cellule.getBooleanCellValue()).toLowerCase(Locale.ROOT);
            default -> null;
        };
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    /** Type de la valeur, resultat en cache pour une formule ; BLANK si pas de cellule. */
    private static CellType type(Cell cellule) {
        if (cellule == null) {
            return CellType.BLANK;
        }
        return cellule.getCellType() == CellType.FORMULA
                ? cellule.getCachedFormulaResultType()
                : cellule.getCellType();
    }
}
