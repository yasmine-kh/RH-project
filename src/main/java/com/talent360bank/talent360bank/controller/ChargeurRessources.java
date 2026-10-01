package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.service.TrimestreCourantService;
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
    private final CollaborateurRepository collaborateurRepository;
    private final TrimestreCourantService trimestreCourant;

    public ChargeurRessources(TrimestreRepository trimestreRepository,
                              CollaborateurRepository collaborateurRepository,
                              TrimestreCourantService trimestreCourant) {
        this.trimestreRepository = trimestreRepository;
        this.collaborateurRepository = collaborateurRepository;
        this.trimestreCourant = trimestreCourant;
    }

    /** @throws RessourceIntrouvableException si aucun trimestre ne porte ce couple */
    @Transactional(readOnly = true)
    public Trimestre exigerTrimestre(int annee, int numero) {
        return trimestreRepository.findByNumeroAndAnnee(numero, annee)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun trimestre T" + numero + " " + annee));
    }

    /**
     * Le trimestre demande ou, sans annee ni numero, le trimestre courant des
     * ecrans : le plus recent qui a des scores (sinon le plus recent cree),
     * meme regle que {@link TrimestreCourantService}, pour que pages et API
     * montrent toujours le meme trimestre.
     *
     * @throws IllegalArgumentException      si un seul des deux est fourni (400)
     * @throws RessourceIntrouvableException si le trimestre demande n'existe
     *                                       pas, ou s'il n'en existe aucun
     */
    @Transactional(readOnly = true)
    public Trimestre exigerTrimestreOuDernier(Integer annee, Integer numero) {
        if (annee == null && numero == null) {
            return trimestreCourant.resoudre(null)
                    .orElseThrow(() -> new RessourceIntrouvableException("Aucun trimestre enregistre"));
        }
        if (annee == null || numero == null) {
            throw new IllegalArgumentException(
                    "annee et numero vont ensemble : fournir les deux, ou aucun pour le dernier trimestre");
        }
        return exigerTrimestre(annee, numero);
    }

    /**
     * Le collaborateur avec son manager, son entite et ses parents : les
     * reponses (direction, departement...) sont serialisees hors transaction.
     *
     * @throws RessourceIntrouvableException si le collaborateur n'existe pas
     */
    @Transactional(readOnly = true)
    public Collaborateur exigerCollaborateur(String idCollaborateur) {
        return collaborateurRepository.findByIdAvecManager(idCollaborateur)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun collaborateur " + idCollaborateur));
    }
}
