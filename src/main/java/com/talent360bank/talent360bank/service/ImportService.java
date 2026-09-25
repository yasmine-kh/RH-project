package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ImportService {

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private EmployeRepository employeRepository;

    public int importerCollaborateurs(String cheminFichier) throws IOException {
        List<Employe> employes = excelReader.lireCollaborateurs(cheminFichier);
        employeRepository.saveAll(employes);
        return employes.size();
    }
}