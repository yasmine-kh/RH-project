package com.talent360bank.talent360bank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionRequetesFilterTest {

    private final ProtectionRequetesFilter filtre =
            new ProtectionRequetesFilter(List.of("localhost", "127.0.0.1"), new ObjectMapper());

    private MockHttpServletResponse executer(MockHttpServletRequest requete, MockFilterChain chaine) throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, chaine);
        return reponse;
    }

    private MockHttpServletRequest requete(String methode, String hote) {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, "/api/trimestres/2026/3/scores/recalcul");
        requete.setServerName(hote);
        return requete;
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void une_ecriture_sans_en_tete_est_refusee(String methode) throws Exception {
        MockFilterChain chaine = new MockFilterChain();

        MockHttpServletResponse reponse = executer(requete(methode, "localhost"), chaine);

        assertThat(reponse.getStatus()).isEqualTo(403);
        assertThat(chaine.getRequest()).as("la requete ne doit pas atteindre l'application").isNull();
        assertThat(JsonPath.parse(reponse.getContentAsString()).read("$.erreur", String.class))
                .isEqualTo("en_tete_manquant");
    }

    @Test
    void une_ecriture_avec_en_tete_passe() throws Exception {
        MockHttpServletRequest requete = requete("POST", "localhost");
        requete.addHeader(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1");
        MockFilterChain chaine = new MockFilterChain();

        MockHttpServletResponse reponse = executer(requete, chaine);

        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(chaine.getRequest()).isNotNull();
    }

    @Test
    void un_en_tete_vide_ne_suffit_pas() throws Exception {
        MockHttpServletRequest requete = requete("POST", "localhost");
        requete.addHeader(ProtectionRequetesFilter.EN_TETE_ECRITURE, " ");

        assertThat(executer(requete, new MockFilterChain()).getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void une_lecture_n_exige_pas_l_en_tete(String methode) throws Exception {
        MockFilterChain chaine = new MockFilterChain();

        assertThat(executer(requete(methode, "localhost"), chaine).getStatus()).isEqualTo(200);
        assertThat(chaine.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "LOCALHOST"})
    void les_hotes_locaux_sont_acceptes(String hote) throws Exception {
        MockFilterChain chaine = new MockFilterChain();

        assertThat(executer(requete("GET", hote), chaine).getStatus()).isEqualTo(200);
        assertThat(chaine.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"attaquant.example", "192.168.1.20", "talent360.local"})
    void un_autre_hote_est_refuse_meme_en_lecture(String hote) throws Exception {
        MockFilterChain chaine = new MockFilterChain();

        MockHttpServletResponse reponse = executer(requete("GET", hote), chaine);

        assertThat(reponse.getStatus()).isEqualTo(403);
        assertThat(chaine.getRequest()).isNull();
        assertThat(JsonPath.parse(reponse.getContentAsString()).read("$.erreur", String.class))
                .isEqualTo("hote_non_autorise");
    }

    @Test
    void la_liste_des_hotes_se_configure() throws Exception {
        ProtectionRequetesFilter elargi =
                new ProtectionRequetesFilter(List.of(" poste-rh ", "localhost"), new ObjectMapper());
        MockFilterChain chaine = new MockFilterChain();
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        elargi.doFilter(requete("GET", "poste-rh"), reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(chaine.getRequest()).isNotNull();
    }
}
