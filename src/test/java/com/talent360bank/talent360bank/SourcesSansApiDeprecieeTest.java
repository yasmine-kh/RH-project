package com.talent360bank.talent360bank;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les tests utilisent @MockitoBean / @MockitoSpyBean (Spring Framework 6.2) : @MockBean et @SpyBean de
 * Spring Boot sont deprecies et seront retires.
 */
class SourcesSansApiDeprecieeTest {

    @Test
    void aucun_test_n_utilise_mock_bean_ou_spy_bean_de_spring_boot() throws IOException {
        try (Stream<Path> fichiers = Files.walk(Path.of("src/test/java"))) {
            List<String> fautifs = fichiers.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals("SourcesSansApiDeprecieeTest.java"))
                    .filter(f -> {
                        try {
                            return Files.readString(f).contains("org.springframework.boot.test.mock.mockito");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertThat(fautifs).isEmpty();
        }
    }
}
