package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.Trimestre;

import java.time.LocalDate;

/**
 * Entree du journal des imports.
 *
 * @param trimestre "T3 2026", nul si l'import a echoue avant de l'ouvrir
 */
public record ImportExcelResume(Integer idImport, String nomFichier, String source, LocalDate dateImport,
                                String statut, String trimestre, Integer nbLignes, Integer nbErreurs,
                                String message) {

    public static ImportExcelResume de(ImportExcel journal) {
        Trimestre trimestre = journal.getTrimestre();
        return new ImportExcelResume(journal.getIdImport(), journal.getNomFichier(), journal.getSource(),
                journal.getDateImport(), journal.getStatut(),
                trimestre == null ? null : TrimestreResponse.libelle(trimestre),
                journal.getNbLignes(), journal.getNbErreurs(), journal.getMessage());
    }
}
