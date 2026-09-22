package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Matrice9BoxInitializerTest {

    @Mock
    private Matrice9BoxRepository matriceRepository;

    private Matrice9BoxInitializer initializer;

    @BeforeEach
    void init() {
        initializer = new Matrice9BoxInitializer(matriceRepository);
    }

    @Test
    void poseLesNeufCasesSurUneBaseVierge() {
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(any(), any()))
                .thenReturn(Optional.empty());

        initializer.run(null);

        ArgumentCaptor<Matrice9Box> capture = ArgumentCaptor.forClass(Matrice9Box.class);
        verify(matriceRepository, times(9)).save(capture.capture());

        assertThat(capture.getAllValues())
                .extracting(Matrice9Box::getNiveauPerformance,
                        Matrice9Box::getNiveauPotentiel,
                        Matrice9Box::getCategorie)
                .containsExactly(
                        tuple(1, 1, "À surveiller"),
                        tuple(1, 2, "À développer"),
                        tuple(1, 3, "Potentiel à confirmer"),
                        tuple(2, 1, "À accompagner"),
                        tuple(2, 2, "Confirmé"),
                        tuple(2, 3, "Potentiel / Haut potentiel"),
                        tuple(3, 1, "Expert"),
                        tuple(3, 2, "Performant"),
                        tuple(3, 3, "Talent clé"));
    }

    @Test
    void nEcrasePasUnLibelleDejaEnBase() {
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(any(), any()))
                .thenReturn(Optional.of(new Matrice9Box()));

        initializer.run(null);

        verify(matriceRepository, never()).save(any());
    }

    @Test
    void completeUniquementLesCasesManquantes() {
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(any(), any()))
                .thenReturn(Optional.empty());
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(3, 3))
                .thenReturn(Optional.of(new Matrice9Box()));

        initializer.run(null);

        ArgumentCaptor<Matrice9Box> capture = ArgumentCaptor.forClass(Matrice9Box.class);
        verify(matriceRepository, times(8)).save(capture.capture());

        assertThat(capture.getAllValues())
                .extracting(Matrice9Box::getCategorie)
                .doesNotContain("Talent clé");
    }

    @Test
    void couvreLesNeufCombinaisonsSansDoublon() {
        when(matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(any(), any()))
                .thenReturn(Optional.empty());

        initializer.run(null);

        ArgumentCaptor<Matrice9Box> capture = ArgumentCaptor.forClass(Matrice9Box.class);
        verify(matriceRepository, times(9)).save(capture.capture());

        List<Matrice9Box> cases = capture.getAllValues();
        assertThat(cases)
                .extracting(c -> c.getNiveauPerformance() + "-" + c.getNiveauPotentiel())
                .doesNotHaveDuplicates()
                .hasSize(9);
        assertThat(cases).extracting(Matrice9Box::getCategorie).doesNotHaveDuplicates();
    }
}
