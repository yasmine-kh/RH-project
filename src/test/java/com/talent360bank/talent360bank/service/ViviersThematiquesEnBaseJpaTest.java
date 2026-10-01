package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.RattachementVivier;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vivier des directions lu en une fois : memes rattachements qu'une lecture par
 * direction, casse et accents ignores comme la collation MySQL.
 */
@DataJpaTest
@Import(ViviersThematiquesEnBase.class)
class ViviersThematiquesEnBaseJpaTest {

    @Autowired
    private ViviersThematiquesEnBase source;
    @Autowired
    private RattachementVivierRepository repository;

    @BeforeEach
    void init() {
        repository.save(new RattachementVivier("Reseau Retail", VivierThematique.COMMERCIAL));
        repository.save(new RattachementVivier("Risques", VivierThematique.RISQUES));
    }

    @Test
    void chaque_direction_recoit_son_vivier_et_une_direction_sans_vivier_est_absente() {
        assertThat(source.viviersParDirection(List.of("Reseau Retail", "Risques", "Audit")))
                .containsOnly(
                        java.util.Map.entry("Reseau Retail", VivierThematique.COMMERCIAL),
                        java.util.Map.entry("Risques", VivierThematique.RISQUES));
    }

    @Test
    void la_casse_les_accents_et_les_espaces_autour_sont_ignores() {
        assertThat(source.viviersParDirection(List.of("Réseau RETAIL", " risques ")))
                .containsEntry("Réseau RETAIL", VivierThematique.COMMERCIAL)
                .containsEntry(" risques ", VivierThematique.RISQUES);
    }

    @Test
    void sans_direction_rien_n_est_rendu() {
        assertThat(source.viviersParDirection(List.of())).isEmpty();
    }
}
