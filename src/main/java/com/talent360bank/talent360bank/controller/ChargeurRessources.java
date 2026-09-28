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

    /**
     * Le trimestre demande ou, sans annee ni numero, le plus recent.
     *
     * @throws IllegalArgumentException      si un seul des deux est fourni (400)
     * @throws RessourceIntrouvableException si le trimestre demande n'existe
     *                                       pas, ou s'il n'en existe aucun
     */
    @Transactional(readOnly = true)
    public Trimestre exigerTrimestreOuDernier(Integer annee, Integer numero) {
        if (annee == null && numero == null) {
            return trimestreRepository.findTopByOrderByAnneeDescNumeroDesc()
                    .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre enregistre"));
        }
        if (annee == null || numero == null) {
            throw new IllegalArgumentException(
                    "annee et numero vont ensemble : fournir les deux, ou aucun pour le dernier trimestre");
        }
        return exigerTrimestre(annee, numero);
    }

    /** @throws RessourceIntrouvableException si l'employe n'existe pas */
    @Transactional(readOnly = true)
    public Employe exigerEmploye(String employeeId) {
        return employeRepository.findById(employeeId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun employe " + employeeId));
    }
}
