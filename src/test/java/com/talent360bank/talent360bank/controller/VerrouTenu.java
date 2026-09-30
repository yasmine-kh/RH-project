package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simule un calcul du trimestre en cours : un autre fil tient le verrou du
 * trimestre jusqu'a {@link #close()}. Pour verifier qu'une ecriture de calcul
 * est refusee (409) pendant ce temps.
 */
final class VerrouTenu implements AutoCloseable {

    private final CountDownLatch liberer = new CountDownLatch(1);
    private final Thread fil;

    private VerrouTenu(VerrouCalculTrimestre verrou, Trimestre trimestre) throws InterruptedException {
        CountDownLatch tenu = new CountDownLatch(1);
        fil = new Thread(() -> verrou.executer(trimestre, () -> {
            tenu.countDown();
            try {
                liberer.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }), "calcul-en-cours");
        fil.start();
        if (!tenu.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Le verrou du trimestre n'a pas pu etre pris");
        }
    }

    static VerrouTenu tenir(VerrouCalculTrimestre verrou, Trimestre trimestre) throws InterruptedException {
        return new VerrouTenu(verrou, trimestre);
    }

    @Override
    public void close() throws InterruptedException {
        liberer.countDown();
        fil.join(10_000);
    }
}
