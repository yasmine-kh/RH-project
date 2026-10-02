package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.ui.model.Choix;
import com.talent360bank.talent360bank.ui.model.ProfilActif;
import com.talent360bank.talent360bank.ui.model.Profils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Options du selecteur de profil : une lecture par liste (collaborateurs,
 * managers), soit deux requetes par page, quel que soit le nombre de
 * personnes. Calcule une fois par requete HTTP (ProfilsAdvice).
 */
@Service
public class ProfilsService {

    private final CollaborateurRepository collaborateurRepository;
    private final ManagerRepository managerRepository;

    public ProfilsService(CollaborateurRepository collaborateurRepository, ManagerRepository managerRepository) {
        this.collaborateurRepository = collaborateurRepository;
        this.managerRepository = managerRepository;
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
        return new Profils(collaborateurs, managers);
    }

    /**
     * Le profil d'un collaborateur ou d'un manager choisi dans le selecteur : une
     * lecture, au moment du choix seulement (ensuite, la session suffit).
     *
     * @return vide si le matricule est inconnu, ou n'est pas manager pour MANAGER
     */
    public Optional<ProfilActif> profil(String type, String matricule) {
        if ("MANAGER".equals(type)) {
            return managerRepository.findByMatriculeAvecEntite(matricule)
                    .map(m -> actif(type, m.getCollaborateur()));
        }
        return collaborateurRepository.findAllByIdAvecEntite(List.of(matricule)).stream().findFirst()
                .map(c -> actif(type, c));
    }

    private static ProfilActif actif(String type, Collaborateur c) {
        return new ProfilActif(type, c.getIdCollaborateur(), c.getNom() + " " + c.getPrenom(),
                c.getEntite() == null ? null : c.getEntite().getCode());
    }
}
