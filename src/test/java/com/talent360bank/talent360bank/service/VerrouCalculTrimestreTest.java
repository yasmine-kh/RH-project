package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RecalculEnCoursException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un seul recalcul a la fois par trimestre, sans attente, reentrant pour le fil qui le tient. */
class VerrouCalculTrimestreTest {

    private final VerrouCalculTrimestre verrou = new VerrouCalculTrimestre();

    private static Trimestre trimestre(int annee, int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setAnnee(annee);
        trimestre.setNumero(numero);
        return trimestre;
    }

    @Test
    void un_second_appel_sur_le_meme_trimestre_est_refuse_pendant_le_premier() throws Exception {
        Trimestre t3 = trimestre(2026, 3);
        CountDownLatch tenu = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        CompletableFuture<String> premier = CompletableFuture.supplyAsync(() -> verrou.executer(t3, () -> {
            tenu.countDown();
            attendre(liberer);
            return "fait";
        }));
        assertThat(tenu.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(verrou.estEnCours(t3)).isTrue();
        // Un autre objet pour le meme trimestre : la cle est (annee, numero), pas l'instance.
        assertThatThrownBy(() -> verrou.executer(trimestre(2026, 3), () -> "second"))
                .isInstanceOf(RecalculEnCoursException.class)
                .hasMessageStartingWith("Recalcul déjà en cours pour T3 2026");
        // Un autre trimestre n'est pas bloque.
        assertThat(verrou.executer(trimestre(2026, 4), () -> "autre")).isEqualTo("autre");

        liberer.countDown();
        assertThat(premier.get(5, TimeUnit.SECONDS)).isEqualTo("fait");
        assertThat(verrou.estEnCours(t3)).isFalse();
        assertThat(verrou.executer(t3, () -> "apres")).isEqualTo("apres");
    }

    @Test
    void le_fil_qui_tient_le_verrou_peut_le_reprendre() {
        Trimestre t3 = trimestre(2026, 3);
        // Enregistrement des reglages, puis CalculTrimestreService.calculer dans le meme fil.
        assertThat(verrou.executer(t3, () -> verrou.executer(t3, () -> "imbrique"))).isEqualTo("imbrique");
        assertThat(verrou.estEnCours(t3)).isFalse();
    }

    @Test
    void le_verrou_est_libere_meme_si_l_action_echoue() {
        Trimestre t3 = trimestre(2026, 3);
        assertThatThrownBy(() -> verrou.executer(t3, () -> {
            throw new IllegalStateException("echec");
        })).isInstanceOf(IllegalStateException.class);

        AtomicReference<String> resultat = new AtomicReference<>();
        CompletableFuture.runAsync(() -> resultat.set(verrou.executer(t3, () -> "libre"))).join();
        assertThat(resultat.get()).isEqualTo("libre");
    }

    private static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
