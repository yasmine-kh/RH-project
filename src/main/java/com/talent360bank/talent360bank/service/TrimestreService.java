package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatCreationTrimestre;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Ouverture d'un trimestre : le trimestre lui-meme et ses reglages, sans
 * lesquels aucun calcul ne tourne. Les reglages sont ceux du trimestre
 * precedent le plus recent (une nouvelle campagne garde les choix du RH), ou
 * les valeurs par defaut ({@link Parametre#parDefaut}) s'il n'y en a pas.
 * Rejouable : ce qui existe deja n'est pas touche.
 */
@Service
public class TrimestreService {

    /** Bornes de bon sens pour eviter un trimestre ouvert sur une faute de frappe. */
    public static final int ANNEE_MIN = 2000;
    public static final int ANNEE_MAX = 2100;

    /** Trimestres precedents parcourus pour trouver des reglages a recopier. */
    private static final int PRECEDENTS_EXAMINES = 8;

    private final TrimestreRepository trimestreRepository;
    private final ParametreRepository parametreRepository;

    public TrimestreService(TrimestreRepository trimestreRepository,
                            ParametreRepository parametreRepository) {
        this.trimestreRepository = trimestreRepository;
        this.parametreRepository = parametreRepository;
    }

    /**
     * Le trimestre, cree s'il n'existe pas, avec des reglages s'il n'en a
     * pas : copie de ceux du trimestre precedent le plus recent qui en a,
     * sinon valeurs par defaut. Un trimestre existant garde ses reglages.
     *
     * @throws IllegalArgumentException si le numero n'est pas entre 1 et 4 ou
     *                                  l'annee hors de [2000, 2100]
     */
    @Transactional
    public ResultatCreationTrimestre creerSiAbsent(int annee, int numero) {
        return creerSiAbsent(annee, numero, null);
    }

    /**
     * Comme {@link #creerSiAbsent(int, int)}, avec la date de reference d'un
     * trimestre cree ; null = dernier jour du trimestre. Un trimestre existant
     * garde la sienne : pour la changer, {@link #modifierDateReference}.
     */
    @Transactional
    public ResultatCreationTrimestre creerSiAbsent(int annee, int numero, LocalDate dateReference) {
        verifier(annee, numero);

        Optional<Trimestre> existant = trimestreRepository.findByNumeroAndAnnee(numero, annee);
        Trimestre trimestre = existant.orElseGet(() -> {
            Trimestre nouveau = new Trimestre();
            nouveau.setAnnee(annee);
            nouveau.setNumero(numero);
            nouveau.setDateReference(dateReference != null ? dateReference : Trimestre.dernierJour(annee, numero));
            return trimestreRepository.save(nouveau);
        });

        boolean parametreCree = false;
        if (!parametreRepository.existsByTrimestre(trimestre)) {
            parametreRepository.save(reglagesInitiaux(trimestre));
            parametreCree = true;
        }
        return new ResultatCreationTrimestre(trimestre, existant.isEmpty(), parametreCree);
    }

    /**
     * Change la date a laquelle le trimestre est evalue (anciennete). Rien
     * n'est a recalculer : le matching et les fiches la lisent a chaque appel,
     * aucun resultat stocke n'en depend.
     */
    @Transactional
    public Trimestre modifierDateReference(Trimestre trimestre, LocalDate dateReference) {
        Objects.requireNonNull(trimestre, "trimestre");
        Objects.requireNonNull(dateReference, "dateReference");
        trimestre.setDateReference(dateReference);
        return trimestreRepository.save(trimestre);
    }

    /** Copie des reglages du trimestre precedent le plus recent qui en a, sinon les valeurs par defaut. */
    private Parametre reglagesInitiaux(Trimestre trimestre) {
        for (Trimestre precedent : trimestreRepository.findPrecedents(trimestre.getAnnee(), trimestre.getNumero(),
                PageRequest.of(0, PRECEDENTS_EXAMINES))) {
            Optional<Parametre> reglages = parametreRepository.findByTrimestre(precedent);
            if (reglages.isPresent()) {
                Parametre copie = reglages.get().copiePour(trimestre);
                copie.setLibelle("Repris de T" + precedent.getNumero() + " " + precedent.getAnnee());
                // Une ligne ancienne peut manquer de blocs ajoutes depuis : completes sur la copie seule.
                copie.completerBlocsAjoutes();
                return copie;
            }
        }
        return Parametre.parDefaut(trimestre);
    }

    public static void verifier(int annee, int numero) {
        if (numero < 1 || numero > 4) {
            throw new IllegalArgumentException("Le numero de trimestre doit etre entre 1 et 4 (recu " + numero + ")");
        }
        if (annee < ANNEE_MIN || annee > ANNEE_MAX) {
            throw new IllegalArgumentException(
                    "L'annee doit etre entre " + ANNEE_MIN + " et " + ANNEE_MAX + " (recu " + annee + ")");
        }
    }
}
