package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RecalculEnCoursException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Un seul recalcul a la fois par trimestre.
 *
 * <p>Verrou en memoire : l'application tourne sur un seul poste, dans un seul
 * processus. Il ne resisterait pas a plusieurs instances, qui demanderaient un
 * verrou en base.
 *
 * <p>Pas d'attente : un second appel pendant qu'un recalcul tourne est refuse
 * tout de suite (RecalculEnCoursException, 409), plutot que de rejouer le meme
 * calcul juste apres. Le verrou est reentrant : le fil qui le tient peut
 * rappeler {@link #executer} (l'enregistrement des reglages le prend, puis
 * CalculTrimestreService le reprend).
 */
@Component
public class VerrouCalculTrimestre {

    private final ConcurrentMap<String, ReentrantLock> verrous = new ConcurrentHashMap<>();

    /**
     * Execute l'action en tenant le verrou du trimestre.
     *
     * @throws RecalculEnCoursException si un autre fil tient deja le verrou de ce trimestre
     */
    public <T> T executer(Trimestre trimestre, Supplier<T> action) {
        Objects.requireNonNull(trimestre, "trimestre");
        Objects.requireNonNull(action, "action");
        ReentrantLock verrou = verrous.computeIfAbsent(cle(trimestre), cle -> new ReentrantLock());
        if (!verrou.tryLock()) {
            throw new RecalculEnCoursException("Recalcul déjà en cours pour T" + trimestre.getNumero() + " "
                    + trimestre.getAnnee() + " : réessayer une fois qu'il est terminé");
        }
        try {
            return action.get();
        } finally {
            verrou.unlock();
        }
    }

    /** Vrai si un recalcul du trimestre tourne en ce moment (pour les tests et l'affichage). */
    public boolean estEnCours(Trimestre trimestre) {
        ReentrantLock verrou = verrous.get(cle(trimestre));
        return verrou != null && verrou.isLocked();
    }

    private static String cle(Trimestre trimestre) {
        return trimestre.getAnnee() + "-" + trimestre.getNumero();
    }
}
