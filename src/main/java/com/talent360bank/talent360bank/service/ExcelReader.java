package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExcelReader {

    public List<Employe> lireCollaborateurs(String cheminFichier) throws IOException {
        List<Employe> employes = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(cheminFichier);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("01_COLLABORATEURS");
            int derniereLigne = sheet.getLastRowNum();

            for (int i = 4; i <= derniereLigne; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String employeeId = getCellString(row, 0);
                if (employeeId == null || employeeId.isBlank()) continue;

                Employe e = new Employe();
                e.setEmployeeId(employeeId);
                e.setNom(getCellString(row, 1));
                e.setPrenom(getCellString(row, 2));
                e.setDateEntree(getCellDate(row, 5));
                e.setDirection(getCellString(row, 7));
                e.setDepartement(getCellString(row, 8));
                e.setRegion(getCellString(row, 9));
                e.setAgence(getCellString(row, 10));
                e.setFonction(getCellString(row, 11));
                e.setGrade(getCellString(row, 12));

                employes.add(e);
            }
        }
        return employes;
    }
    public List<Competence> lireReferentielCompetences(String cheminFichier) throws IOException {
        List<Competence> competences = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(cheminFichier);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("05_REFERENTIEL_COMPETENCES");
            int derniereLigne = sheet.getLastRowNum();

            for (int i = 4; i <= derniereLigne; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String competenceId = getCellString(row, 0);
                if (competenceId == null || competenceId.isBlank()) continue;

                Competence c = new Competence();
                c.setCompetenceId(competenceId);
                c.setNom(getCellString(row, 1));
                c.setCategorie(getCellString(row, 2));
                competences.add(c);
            }
        }
        return competences;
    }

    public List<EmployeeSkill> lireEmployeeSkills(String cheminFichier,
                                                  Map<String, Employe> employesParId, Map<String, Competence> competencesParNom) throws IOException {
        List<EmployeeSkill> skills = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(cheminFichier);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("06_EMPLOYEE_SKILLS");
            int derniereLigne = sheet.getLastRowNum();

            for (int i = 4; i <= derniereLigne; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String employeeId = getCellString(row, 1);
                String competenceNom = getCellString(row, 2);
                if (employeeId == null || competenceNom == null) continue;

                Employe employe = employesParId.get(employeeId);
                Competence competence = competencesParNom.get(competenceNom);
                if (employe == null || competence == null) continue;

                EmployeeSkill es = new EmployeeSkill();
                es.setCleLookup(getCellString(row, 0));
                es.setEmploye(employe);
                es.setCompetence(competence);
                es.setNiveauActuel(getCellInteger(row, 4));
                es.setNiveauCible(getCellInteger(row, 5));
                es.setGap(getCellInteger(row, 6));
                es.setStatutGap(getCellString(row, 7));
                skills.add(es);
            }
        }
        return skills;
    }

    /**
     * @param referentielParNom competences du referentiel indexees par nom : les
     *                          competences requises de 07_POSTES sont des libelles
     */
    public List<Poste> lirePostes(String cheminFichier, Map<String, Competence> referentielParNom)
            throws IOException {
        List<Poste> postes = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(cheminFichier);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("07_POSTES");
            int derniereLigne = sheet.getLastRowNum();

            for (int i = 4; i <= derniereLigne; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String posteId = getCellString(row, 0);
                if (posteId == null || posteId.isBlank()) continue;

                Poste p = new Poste();
                p.setPosteId(posteId);
                p.setNomPoste(getCellString(row, 1));
                p.setDirection(getCellString(row, 2));
                p.setGradeCible(getCellString(row, 3));
                p.setCriticite(getCellString(row, 4));
                p.setCompetenceRequise1(referentielParNom.get(getCellString(row, 5)));
                p.setNiveau1(getCellInteger(row, 6));
                p.setCompetenceRequise2(referentielParNom.get(getCellString(row, 7)));
                p.setNiveau2(getCellInteger(row, 8));
                p.setCompetenceRequise3(referentielParNom.get(getCellString(row, 9)));
                p.setNiveau3(getCellInteger(row, 10));
                p.setCompetenceRequise4(referentielParNom.get(getCellString(row, 11)));
                p.setNiveau4(getCellInteger(row, 12));
                p.setCompetenceRequise5(referentielParNom.get(getCellString(row, 13)));
                p.setNiveau5(getCellInteger(row, 14));
                p.setPosteCritique(getCellString(row, 15));
                postes.add(p);
            }
        }
        return postes;
    }

    public void completerPostesCritiques(String cheminFichier, Map<String, Poste> postesParId) throws IOException {
        try (FileInputStream fis = new FileInputStream(cheminFichier);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("08_POSTES_CRITIQUES");
            int derniereLigne = sheet.getLastRowNum();

            for (int i = 4; i <= derniereLigne; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String posteId = getCellString(row, 0);
                if (posteId == null) continue;

                Poste poste = postesParId.get(posteId);
                if (poste == null) continue;

                poste.setTitulaireId(getCellString(row, 4));
                poste.setTitulaireNom(getCellString(row, 5));
            }
        }
    }

    // TODO(Dou/Jasmine) : a reecrire. Parametre n'a pas de champs section /
    // critere / valeur / commentaire : c'est un jeu de reglages par trimestre,
    // en blocs (PoidsPerformance, SeuilsNeufBox...). La lecture de 00_PARAMETRES
    // doit alimenter ces blocs ; methode desactivee pour que le projet compile.
    // public List<Parametre> lireParametres(String cheminFichier) throws IOException {
    //     List<Parametre> parametres = new ArrayList<>();
    //     try (FileInputStream fis = new FileInputStream(cheminFichier);
    //          Workbook workbook = WorkbookFactory.create(fis)) {
    //
    //         Sheet sheet = workbook.getSheet("00_PARAMETRES");
    //         int derniereLigne = sheet.getLastRowNum();
    //         String sectionCourante = null;
    //
    //         for (int i = 4; i <= derniereLigne; i++) {
    //             Row row = sheet.getRow(i);
    //             if (row == null) continue;
    //
    //             String colA = getCellString(row, 0);
    //             if (colA != null && colA.matches("^\\d+\\..*")) {
    //                 sectionCourante = colA;
    //                 continue;
    //             }
    //             if ("Critere".equalsIgnoreCase(colA)) continue;
    //             if (colA == null || colA.isBlank()) continue;
    //
    //             Double valeur = getCellDouble(row, 1);
    //             if (valeur == null) continue;
    //
    //             Parametre p = new Parametre();
    //             p.setSection(sectionCourante);
    //             p.setCritere(colA);
    //             p.setValeur(valeur);
    //             p.setCommentaire(getCellString(row, 2));
    //             parametres.add(p);
    //         }
    //     }
    //     return parametres;
    // }
    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        String value = cell.getStringCellValue();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDate getCellDate(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        } catch (Exception ex) {
            return null;
        }
    }
    private Integer getCellInteger(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        try {
            return (int) cell.getNumericCellValue();
        } catch (Exception ex) {
            return null;
        }
    }

    private Double getCellDouble(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        try {
            return cell.getNumericCellValue();
        } catch (Exception ex) {
            return null;
        }
    }
}