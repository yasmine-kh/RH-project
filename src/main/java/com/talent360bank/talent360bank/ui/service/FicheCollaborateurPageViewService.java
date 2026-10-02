package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur;
import org.springframework.stereotype.Service;

/**
 * Prepare la page Fiche collaborateur sur le trimestre le plus recent.
 * Aucun calcul ici : tout vient de FicheCollaborateurViewService.construire().
 */
@Service
public class FicheCollaborateurPageViewService {

    private final FicheCollaborateurViewService ficheCollaborateurViewService;
    private final TrimestreRepository trimestreRepository;

    public FicheCollaborateurPageViewService(FicheCollaborateurViewService ficheCollaborateurViewService,
                                             TrimestreRepository trimestreRepository) {
        this.ficheCollaborateurViewService = ficheCollaborateurViewService;
        this.trimestreRepository = trimestreRepository;
    }

    /**
     * Fiche du collaborateur sur le trimestre le plus recent.
     *
     * @throws RessourceIntrouvableException si aucun trimestre n'existe ou si le matricule est inconnu
     */
    public FicheCollaborateur construire(String matricule) {
        Trimestre dernierTrimestre = trimestreRepository.findTopByOrderByAnneeDescNumeroDesc()
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre en base."));

        return ficheCollaborateurViewService.construire(matricule,
                dernierTrimestre.getAnnee(), dernierTrimestre.getNumero());
    }

    /**
     * Fiche du collaborateur sur un trimestre donne (lien venant d'un autre ecran).
     *
     * @throws RessourceIntrouvableException si le matricule est inconnu
     */
    public FicheCollaborateur construire(String matricule, Trimestre trimestre) {
        return ficheCollaborateurViewService.construire(matricule, trimestre.getAnnee(), trimestre.getNumero());
    }
}
