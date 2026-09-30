package com.talent360bank.talent360bank.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaque service du moteur recoit la source en base (@Fallback), seule
 * candidate. Verifie sur le vrai contexte, par le champ que le service a
 * rempli au demarrage via ObjectProvider.getIfAvailable.
 */
@SpringBootTest
class SourcesEnBaseContexteTest {

    @Autowired
    private ApplicationContext contexte;

    /** Source retenue par le service : champ lu sur l'objet cible, derriere un eventuel proxy transactionnel. */
    static Object source(ApplicationContext contexte, Class<?> service, String champ) {
        Object cible = AopTestUtils.getUltimateTargetObject(contexte.getBean(service));
        return ReflectionTestUtils.getField(cible, champ);
    }

    @Test
    void les_services_recoivent_les_sources_en_base() {
        assertThat(source(contexte, PosteCritiqueService.class, "successeurIdentifieSource"))
                .isInstanceOf(SuccesseursIdentifiesEnBase.class);
        assertThat(source(contexte, ValidationComiteService.class, "validationComiteSource"))
                .isInstanceOf(ValidationsComiteEnBase.class);
        assertThat(source(contexte, VivierThematiqueService.class, "vivierThematiqueSource"))
                .isInstanceOf(ViviersThematiquesEnBase.class);
        assertThat(source(contexte, VigilanceService.class, "faitsVigilanceSource"))
                .isInstanceOf(FaitsVigilanceEnBase.class);
    }

    @Test
    void chaque_contrat_n_a_qu_une_implementation() {
        assertThat(contexte.getBeansOfType(SuccesseurIdentifieSource.class)).hasSize(1);
        assertThat(contexte.getBeansOfType(ValidationComiteSource.class)).hasSize(1);
        assertThat(contexte.getBeansOfType(VivierThematiqueSource.class)).hasSize(1);
        assertThat(contexte.getBeansOfType(FaitsVigilanceSource.class)).hasSize(1);
    }
}
