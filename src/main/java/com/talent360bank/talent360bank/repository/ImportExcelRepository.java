package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.ImportExcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImportExcelRepository extends JpaRepository<ImportExcel, Integer> {

    /** Journal complet, le plus recent en premier (l'identifiant suit l'ordre des imports). */
    @Query("select i from ImportExcel i left join fetch i.trimestre order by i.idImport desc")
    List<ImportExcel> findAllRecentsDabord();
}
