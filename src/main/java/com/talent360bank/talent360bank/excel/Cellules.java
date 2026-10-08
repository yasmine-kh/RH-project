package com.talent360bank.talent360bank.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
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
 */
public final class Cellules {

    /** Nombre de lignes parcourues pour trouver l'en-tete d'une feuille. */
    private static final int LIGNES_MAX_AVANT_ENTETE = 10;

    private static final DataFormatter FORMATTER = new DataFormatter();

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
     * Lignes de donnees sous l'en-tete.
     * <p>
     * S'arrete des qu'une ligne entierement vide est rencontree pour eviter
     * de lire les lignes d'exemples ou de bas de page en erreur.
     */
    public static List<Row> lignesDonnees(Sheet feuille, int ligneEntete) {
        List<Row> lignes = new ArrayList<>();
        for (int i = ligneEntete + 1; i <= feuille.getLastRowNum(); i++) {
            Row ligne = feuille.getRow(i);
            if (estLigneVide(ligne)) {
                break; // On s'arrete au premier bloc/ligne vide
            }
            lignes.add(ligne);
        }
        return lignes;
    }

    /**
     * Verifie si une ligne est totalement vide.
     */
    public static boolean estLigneVide(Row ligne) {
        if (ligne == null) {
            return true;
        }
        for (int c = ligne.getFirstCellNum(); c < ligne.getLastCellNum(); c++) {
            if (c < 0) continue;
            Cell cell = ligne.getCell(c);
            if (cell != null && type(cell) != CellType.BLANK && brut(cell) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lecture d'un matricule.
     */
    public static String matricule(Row ligne, int colonne) {
        if (ligne == null) {
            return null;
        }
        Cell cellule = ligne.getCell(colonne);
        if (cellule == null) {
            return null;
        }

        if (estCelluleEnErreur(cellule)) {
            throw new ValeurIllisibleException("colonne " + lettre(colonne) + " : valeur en erreur (#N/A)");
        }

        if (type(cellule) == CellType.STRING) {
            String texte = cellule.getStringCellValue().trim();
            if (texte.isEmpty() || "#N/A".equalsIgnoreCase(texte) || "#N/A!".equalsIgnoreCase(texte)) {
                return null;
            }
            return texte;
        }

        String valeur = FORMATTER.formatCellValue(cellule).trim();
        if (valeur.isEmpty() || "#N/A".equalsIgnoreCase(valeur) || "#N/A!".equalsIgnoreCase(valeur)) {
            return null;
        }
        return valeur;
    }

    /** Texte de la cellule, nul si vide. */
    public static String texte(Row ligne, int colonne) {
        if (ligne == null) {
            return null;
        }
        Cell cellule = ligne.getCell(colonne);
        if (estCelluleEnErreur(cellule)) {
            throw new ValeurIllisibleException("colonne " + lettre(colonne) + " : valeur en erreur (#N/A)");
        }
        return brut(cellule);
    }

    /** Nombre de la cellule, nul si vide. */
    public static BigDecimal nombre(Row ligne, int colonne) {
        if (ligne == null) {
            return null;
        }
        Cell cellule = ligne.getCell(colonne);
        if (cellule == null) {
            return null;
        }
        if (estCelluleEnErreur(cellule)) {
            throw new ValeurIllisibleException("colonne " + lettre(colonne) + " : valeur en erreur (#N/A)");
        }
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

    /** Date de la cellule, nulle si vide. */
    public static LocalDate date(Row ligne, int colonne) {
        if (ligne == null) {
            return null;
        }
        Cell cellule = ligne.getCell(colonne);
        if (cellule == null) {
            return null;
        }
        if (estCelluleEnErreur(cellule)) {
            throw new ValeurIllisibleException("colonne " + lettre(colonne) + " : valeur en erreur (#N/A)");
        }

        CellType type = type(cellule);
        if (type == CellType.NUMERIC) {
            try {
                return DateUtil.getLocalDateTime(cellule.getNumericCellValue()).toLocalDate();
            } catch (Exception e) {
                throw new ValeurIllisibleException("colonne " + lettre(colonne) + " : date invalide");
            }
        }

        String texte = brut(cellule);
        if (texte == null) {
            return null;
        }

        for (DateTimeFormatter format : FORMATS_DATE) {
            try {
                return LocalDate.parse(texte, format);
            } catch (DateTimeParseException e) {
                // essai suivant
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

    /** Lettre de colonne Excel (0 -> A, 1 -> B). */
    public static String lettre(int colonne) {
        StringBuilder lettres = new StringBuilder();
        for (int reste = colonne; reste >= 0; reste = reste / 26 - 1) {
            lettres.insert(0, (char) ('A' + reste % 26));
        }
        return lettres.toString();
    }

    private static boolean estCelluleEnErreur(Cell cellule) {
        if (cellule == null) {
            return false;
        }
        if (cellule.getCellType() == CellType.ERROR) {
            return true;
        }
        return cellule.getCellType() == CellType.FORMULA
                && cellule.getCachedFormulaResultType() == CellType.ERROR;
    }

    private static String brut(Cell cellule) {
        if (cellule == null || estCelluleEnErreur(cellule)) {
            return null;
        }
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

        if (valeur != null) {
            String nettoye = valeur.trim();
            if ("#N/A".equalsIgnoreCase(nettoye) || "#N/A!".equalsIgnoreCase(nettoye) || nettoye.isBlank()) {
                return null;
            }
            return nettoye;
        }
        return null;
    }

    private static CellType type(Cell cellule) {
        if (cellule == null) {
            return CellType.BLANK;
        }
        return cellule.getCellType() == CellType.FORMULA
                ? cellule.getCachedFormulaResultType()
                : cellule.getCellType();
    }
}