package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.ui.model.Choix;
import com.talent360bank.talent360bank.ui.model.Profils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Options du selecteur de profil : une lecture par liste (collaborateurs,
 * managers, entites), soit trois requetes par page, quel que soit le nombre de
 * personnes. Calcule une fois par requete HTTP (ProfilsAdvice).
 */
@Service
public class ProfilsService {

    private final CollaborateurRepository collaborateurRepository;
    private final ManagerRepository managerRepository;
    private final EntiteRepository entiteRepository;

    public ProfilsService(CollaborateurRepository collaborateurRepository, ManagerRepository managerRepository,
                          EntiteRepository entiteRepository) {
        this.collaborateurRepository = collaborateurRepository;
        this.managerRepository = managerRepository;
        this.entiteRepository = entiteRepository;
    }

    public Profils options() {
        List<Choix> collaborateurs = collaborateurRepository.findAll(Sort.by("nom", "prenom", "idCollaborateur"))
                .stream()
                .filter(c -> c.getStatut() != StatutCollaborateur.ARCHIVE)
                .map(c -> new Choix(c.getIdCollaborateur(), c.getNom() + " " + c.getPrenom()))
                .toList();
        List<Choix> managers = managerRepository.findAllAvecEntite().stream()
                .map(m -> m.getCollaborateur())
                .sorted(Comparator.comparing(Collaborateur::getNom).thenComparing(Collaborateur::getPrenom)
                        .thenComparing(Collaborateur::getIdCollaborateur))
                .map(c -> new Choix(c.getIdCollaborateur(), c.getNom() + " " + c.getPrenom()))
                .toList();
        // Le code d'une entite fille commence par celui de sa mere suivi de "/" : trier par code
        // donne l'ordre de l'organigramme ; le nombre de "/" donne le niveau.
        List<Choix> entites = entiteRepository.findAllAvecParent().stream()
                .sorted(Comparator.comparing(Entite::getCode))
                .map(e -> new Choix(e.getCode(),
                        "· ".repeat((int) e.getCode().chars().filter(ch -> ch == '/').count()) + e.getLibelle()))
                .toList();
        return new Profils(collaborateurs, managers, entites);
    }
}
