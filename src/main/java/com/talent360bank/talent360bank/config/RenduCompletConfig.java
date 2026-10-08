package com.talent360bank.talent360bank.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

/**
 * Aucune page envoyee a moitie : Thymeleaf rend toute la page en memoire avant d'ecrire la reponse.
 *
 * <p>Par defaut, Thymeleaf ecrit la page au fil du rendu : une exception au milieu du template
 * arrive apres l'envoi du statut 200 et du debut du HTML, et le navigateur recoit une page tronquee
 * (net::ERR_INCOMPLETE_CHUNKED_ENCODING). Ici, une erreur de rendu survient avant toute ecriture :
 * la page d'erreur est rendue a la place, entiere. Cout : la page est gardee en memoire le temps du
 * rendu (environ 200 Ko pour le tableau de bord). Equivalent de la propriete
 * spring.thymeleaf.servlet.produce-partial-output-while-processing=false, mais applique dans tous les
 * profils et tous les tests (RenduCompletTest).
 */
@Configuration
public class RenduCompletConfig {

    @Bean
    static BeanPostProcessor renduCompletThymeleaf() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nom) {
                if (bean instanceof ThymeleafViewResolver resolveur) {
                    resolveur.setProducePartialOutputWhileProcessing(false);
                }
                return bean;
            }
        };
    }
}
