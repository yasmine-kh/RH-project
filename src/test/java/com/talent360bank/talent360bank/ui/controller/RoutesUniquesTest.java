package com.talent360bank.talent360bank.ui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contexte Spring complet demarre (une route en double le ferait echouer :
 * "Ambiguous mapping") et chaque couple (URL, verbe) n'a qu'un seul
 * gestionnaire de l'application. Les ecrans repartis entre plusieurs controleurs (PagesController
 * d'Ima, DashboardController et AlertesController de Jas) ne se marchent pas dessus.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:routes-uniques;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RoutesUniquesTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void aucune_url_n_est_servie_par_deux_gestionnaires() {
        Map<String, List<String>> parRoute = new HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entree : mappings.getHandlerMethods().entrySet()) {
            // /error de Spring Boot a deux gestionnaires distingues par le type rendu (HTML / JSON) : voulu.
            if (!entree.getValue().getBeanType().getPackageName().startsWith("com.talent360bank")) {
                continue;
            }
            Set<RequestMethod> verbes = entree.getKey().getMethodsCondition().getMethods();
            for (String url : entree.getKey().getPatternValues()) {
                for (String verbe : verbes.isEmpty() ? List.of("*") : verbes.stream().map(Enum::name).toList()) {
                    parRoute.computeIfAbsent(verbe + " " + url, cle -> new ArrayList<>())
                            .add(entree.getValue().getShortLogMessage());
                }
            }
        }

        assertThat(parRoute).allSatisfy((route, gestionnaires) ->
                assertThat(gestionnaires).as(route).hasSize(1));
    }

    @Test
    void chaque_ecran_a_son_controleur() {
        Map<String, String> ecrans = new HashMap<>();
        mappings.getHandlerMethods().forEach((info, methode) -> info.getPatternValues().forEach(url ->
                ecrans.put(url, methode.getBeanType().getSimpleName())));

        assertThat(ecrans).containsEntry("/", "DashboardController")
                .containsEntry("/alertes", "AlertesController")
                .containsEntry("/postes-critiques", "PagesController")
                .containsEntry("/9box", "PagesController")
                .containsEntry("/viviers", "PagesController")
                .containsEntry("/comite-talent", "PagesController")
                .containsEntry("/parametres", "PagesController")
                .containsEntry("/collaborateurs", "ModulesPagesController")
                .containsEntry("/dashboard-dg", "DashboardController")
                .containsEntry("/competences", "ModulesPagesController")
                .containsEntry("/notifications", "ModulesPagesController")
                .containsEntry("/campagne", "ModulesPagesController")
                .containsEntry("/carriere-mobilite", "PagesEnDeveloppementController")
                .containsEntry("/engagement", "PagesEnDeveloppementController")
                .containsEntry("/historique", "PagesEnDeveloppementController")
                .containsEntry("/mon-engagement", "PagesEnDeveloppementController")
                .containsEntry("/auto-evaluation", "PagesEnDeveloppementController")
                .containsEntry("/evaluation-manager", "PagesEnDeveloppementController");
    }
}
