package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class TemplateService {

    private static final String MOT_DE_PASSE_PROTECTION = "Talent360Protect!";

    /**
     * Génère un template Excel pour un collaborateur individuel.
     */
    public byte[] genererTemplateCollaborateur(String matricule, String nomCampagne, String version) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // 1. Feuille masquée _META
            creerFeuilleMeta(workbook, "COLLABORATEUR", version, nomCampagne, matricule);

            // 2. Feuille principale d'évaluation
            Sheet sheet = workbook.createSheet("Évaluation");
            sheet.protectSheet(MOT_DE_PASSE_PROTECTION);

            // Styles
            CellStyle styleEntete = creerStyleEntete(workbook);
            CellStyle styleVerrouille = creerStyleVerrouille(workbook);
            CellStyle styleSaisie = creerStyleSaisie(workbook);

            // Entêtes (Ligne 0)
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Matricule", "Catégorie", "Note / Score (0-100)", "Appréciation"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styleEntete);
            }

            // Exemple de ligne pré-remplie avec cellule verrouillée et déverrouillée
            Row dataRow = sheet.createRow(1);

            Cell cellMatricule = dataRow.createCell(0);
            cellMatricule.setCellValue(matricule);
            cellMatricule.setCellStyle(styleVerrouille); // Verrouillé

            Cell cellCat = dataRow.createCell(1);
            cellCat.setCellValue("Engagement & Agilité");
            cellCat.setCellStyle(styleVerrouille); // Verrouillé

            Cell cellNote = dataRow.createCell(2);
            cellNote.setCellStyle(styleSaisie); // Déverrouillé pour la saisie

            Cell cellAppreciation = dataRow.createCell(3);
            cellAppreciation.setCellStyle(styleSaisie); // Déverrouillé

            // 3. Validation de données : Plage 0 - 100 sur la colonne 2 (Notes)
            appliquerValidationNombre(sheet, 0, 100, 1, 100, 2);

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            sheet.autoSizeColumn(3);

            return exporterEnOctets(workbook);
        }
    }

    /**
     * Génère un template Excel pour un Manager avec l'ensemble de ses collaborateurs pré-remplis.
     */
    public byte[] genererTemplateManager(String matriculeManager, String nomCampagne, String version, List<Collaborateur> equipe) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // 1. Feuille masquée _META
            creerFeuilleMeta(workbook, "MANAGER", version, nomCampagne, matriculeManager);

            // 2. Feuille d'évaluation
            Sheet sheet = workbook.createSheet("Évaluation Équipe");
            sheet.protectSheet(MOT_DE_PASSE_PROTECTION);

            CellStyle styleEntete = creerStyleEntete(workbook);
            CellStyle styleVerrouille = creerStyleVerrouille(workbook);
            CellStyle styleSaisie = creerStyleSaisie(workbook);

            // Entêtes
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Matricule Collaborateur", "Nom & Prénom", "Performance (1-5)", "Potentiel (1-5)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styleEntete);
            }

            // Remplissage de l'équipe
            int rowIndex = 1;
            for (Collaborateur collab : equipe) {
                Row row = sheet.createRow(rowIndex++);

                Cell cellMat = row.createCell(0);
                cellMat.setCellValue(collab.getIdCollaborateur());
                cellMat.setCellStyle(styleVerrouille);

                Cell cellNom = row.createCell(1);
                cellNom.setCellValue(collab.getNom() + " " + collab.getPrenom());
                cellNom.setCellStyle(styleVerrouille);

                Cell cellPerf = row.createCell(2);
                cellPerf.setCellStyle(styleSaisie); // À remplir par le manager

                Cell cellPot = row.createCell(3);
                cellPot.setCellStyle(styleSaisie); // À remplir par le manager
            }

            int totalLignes = Math.max(rowIndex, 100);
            // 3. Validation de données : Plage 1 - 5 pour Performance (Col 2) et Potentiel (Col 3)
            appliquerValidationNombre(sheet, 1, 5, 1, totalLignes, 2);
            appliquerValidationNombre(sheet, 1, 5, 1, totalLignes, 3);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            return exporterEnOctets(workbook);
        }
    }

    // --- RÈGLES TECHNIQUES DU CAHIER DES CHARGES ---

    private void creerFeuilleMeta(Workbook workbook, String type, String version, String campagne, String matricule) {
        Sheet metaSheet = workbook.createSheet("_META");

        Row r0 = metaSheet.createRow(0);
        r0.createCell(0).setCellValue("TYPE");
        r0.createCell(1).setCellValue(type);

        Row r1 = metaSheet.createRow(1);
        r1.createCell(0).setCellValue("VERSION");
        r1.createCell(1).setCellValue(version);

        Row r2 = metaSheet.createRow(2);
        r2.createCell(0).setCellValue("CAMPAGNE");
        r2.createCell(1).setCellValue(campagne);

        Row r3 = metaSheet.createRow(3);
        r3.createCell(0).setCellValue("MATRICULE");
        r3.createCell(1).setCellValue(matricule);

        // Masquer la feuille _META
        workbook.setSheetHidden(workbook.getSheetIndex("_META"), true);
    }

    private void appliquerValidationNombre(Sheet sheet, int min, int max, int startRow, int endRow, int col) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createIntegerConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, String.valueOf(min), String.valueOf(max));
        CellRangeAddressList regions = new CellRangeAddressList(startRow, endRow, col, col);
        DataValidation validation = helper.createValidation(constraint, regions);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private CellStyle creerStyleEntete(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setLocked(true);
        return style;
    }

    private CellStyle creerStyleVerrouille(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(true);
        return style;
    }

    private CellStyle creerStyleSaisie(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(false); // Permet la saisie même si la feuille est protégée
        return style;
    }

    private byte[] exporterEnOctets(Workbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        return baos.toByteArray();
    }
}