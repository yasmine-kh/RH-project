package com.talent360bank.talent360bank.excel;

import org.apache.poi.ss.usermodel.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LecteurXlsx {

    private final Map<String, Map<String, Map<String, String>>> feuilles = new HashMap<>();

    public LecteurXlsx(Path cheminFichier) throws Exception {
        try (InputStream is = Files.newInputStream(cheminFichier);
             Workbook workbook = WorkbookFactory.create(is)) {

            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : workbook) {
                String nomFeuille = sheet.getSheetName();
                Map<String, Map<String, String>> lignesFeuille = new LinkedHashMap<>();

                Row entete = sheet.getRow(0);
                if (entete == null) continue;

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    Cell cellCle = row.getCell(0); // Colonne A
                    if (cellCle == null) continue;

                    String cle = formatter.formatCellValue(cellCle).trim();
                    if (cle.isEmpty()) continue;

                    Map<String, String> valeursLigne = new HashMap<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        String lettreColonne = convertirIndiceEnLettre(c);
                        Cell cell = row.getCell(c);
                        valeursLigne.put(lettreColonne, cell != null ? formatter.formatCellValue(cell).trim() : "");
                    }
                    lignesFeuille.put(cle, valeursLigne);
                }
                feuilles.put(nomFeuille, lignesFeuille);
            }
        }
    }

    public Map<String, Map<String, String>> feuille(String nomFeuille) {
        return feuilles.getOrDefault(nomFeuille, Collections.emptyMap());
    }

    private static String convertirIndiceEnLettre(int colIndex) {
        StringBuilder result = new StringBuilder();
        while (colIndex >= 0) {
            result.insert(0, (char) ('A' + (colIndex % 26)));
            colIndex = (colIndex / 26) - 1;
        }
        return result.toString();
    }

    public static class Feuille {
        // Classe interne de support
    }
}