package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduit les variables d'URL en entites du domaine.
 *
 * <p>Isole dans un composant plutot que recopie dans chaque controleur : les
 * six controleurs resolvent tous un trimestre de la meme facon, et le message
 * d'erreur d'un trimestre inconnu doit etre le meme partout.
 */
@Component
public class ChargeurRessources {

    private final TrimestreRepository trimestreRepository;
    private final EmployeRepository employeRepository;

    public ChargeurRessources(TrimestreRepository trimestreRepository,
                              EmployeRepository employeRepository) {
        this.trimestreRepository = trimestreRepository;
        this.employeRepository = employeRepository;
    }

    /** @throws RessourceIntrouvableException si aucun trimestre ne porte ce couple */
    @Transactional(readOnly = true)
    public Trimestre exigerTrimestre(int annee, int numero) {
        return trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun trimestre T" + numero + " " + annee));
    }

    /** @throws RessourceIntrouvableException si l'employe n'existe pas */
    @Transactional(readOnly = true)
    public Employe exigerEmploye(String employeeId) {
        return employeRepository.findById(employeeId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun employe " + employeeId));
    }
}
