package com.talent360bank.talent360bank.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static com.talent360bank.talent360bank.service.ClasseurDeTest.P1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le parcours de docs/import-donnees.md, par HTTP sur un vrai serveur :
 * ouverture du trimestre, import multipart (annee et numero en champs du
 * formulaire, comme curl -F), calcul, journal.
 *
 * <p>Base en memoire a part : ce test ecrit et calcule sans toucher la base
 * des autres tests d'integration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:import-api;DB_CLOSE_DELAY=-1;MODE=MySQL")
class ImportApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static HttpHeaders ecriture(MediaType type) {
        HttpHeaders entetes = new HttpHeaders();
        entetes.add(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1");
        if (type != null) {
            entetes.setContentType(type);
        }
        return entetes;
    }

    private ResponseEntity<String> importer(byte[] classeur, HttpHeaders entetes) {
        MultiValueMap<String, Object> formulaire = new LinkedMultiValueMap<>();
        formulaire.add("fichier", new ByteArrayResource(classeur) {
            @Override
            public String getFilename() {
                return "TALENT_360_BANK_Dataset_V1.xlsx";
            }
        });
        formulaire.add("annee", "2026");
        formulaire.add("numero", "3");
        return restTemplate.postForEntity("/api/imports", new HttpEntity<>(formulaire, entetes), String.class);
    }

    @Test
    void trimestre_import_calcul_puis_journal() {
        ResponseEntity<String> trimestre = restTemplate.postForEntity("/api/trimestres",
                new HttpEntity<>(Map.of("annee", 2026, "numero", 3), ecriture(MediaType.APPLICATION_JSON)),
                String.class);
        assertThat(trimestre.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> refuse = importer(ClasseurDeTest.complet().octets(), new HttpHeaders());
        assertThat(refuse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> importe = importer(ClasseurDeTest.complet().octets(),
                ecriture(MediaType.MULTIPART_FORM_DATA));
        assertThat(importe.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext bilan = JsonPath.parse(importe.getBody());
        assertThat(bilan.read("$.statut", String.class)).isEqualTo("SUCCES");
        assertThat(bilan.read("$.nbLignes", Integer.class)).isEqualTo(25);
        assertThat(bilan.read("$.nomFichier", String.class)).isEqualTo("TALENT_360_BANK_Dataset_V1.xlsx");

        ResponseEntity<String> calcul = restTemplate.postForEntity("/api/trimestres/2026/3/calcul",
                new HttpEntity<>(null, ecriture(null)), String.class);
        assertThat(calcul.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext etapes = JsonPath.parse(calcul.getBody());
        int scores = etapes.read("$.scores.nombreCalcules", Integer.class);
        assertThat(scores).isPositive();
        assertThat(etapes.read("$.placement9Box.nombreCalcules", Integer.class)).isEqualTo(scores);
        assertThat(etapes.read("$.vivierReleve.nbEcrits", Integer.class)).isNotNull();

        // Les successeurs importes arrivent au moteur par la source en base.
        DocumentContext poste = JsonPath.parse(restTemplate.getForObject(
                "/api/postes-critiques/" + P1 + "?annee=2026&numero=3", String.class));
        int retenus = poste.read("$.nbSuccesseurs", Integer.class);
        List<?> ignores = poste.read("$.ignores");
        assertThat(retenus + ignores.size()).isEqualTo(2);

        DocumentContext journal = JsonPath.parse(restTemplate.getForObject("/api/imports", String.class));
        assertThat(journal.read("$.length()", Integer.class)).isEqualTo(1);
        assertThat(journal.read("$[0].statut", String.class)).isEqualTo("SUCCES");
        assertThat(journal.read("$[0].trimestre", String.class)).isEqualTo("T3 2026");
    }
}
