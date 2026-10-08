package com.talent360bank.talent360bank.dataset;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.service.FaitsVigilanceEnBase;
import com.talent360bank.talent360bank.service.ImportClasseurService;
import com.talent360bank.talent360bank.service.ImportService;
import com.talent360bank.talent360bank.service.SuccesseursIdentifiesEnBase;
import com.talent360bank.talent360bank.service.TrimestreService;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.BilanFeuille;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static com.talent360bank.talent360bank.service.ImportClasseurService.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import du vrai classeur TALENT_360_BANK_Dataset_V1.xlsx : aucune erreur,
 * et le bon nombre de lignes par feuille. Ignore sans le fichier, qui n'est
 * pas versionne (CI, poste d'un collegue).
 *
 * <p>Base en memoire a part, annulee en fin de test : le classeur y est
 * importe en entier sans toucher la base des autres tests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dataset-reel;DB_CLOSE_DELAY=-1;MODE=MySQL")
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
@Import({ImportService.class, com.talent360bank.talent360bank.securite.UtilisateurCourant.class, ImportClasseurService.class, TrimestreService.class,
        SuccesseursIdentifiesEnBase.class, FaitsVigilanceEnBase.class})
@EnabledIf("classeurPresent")
class ImportDatasetReelJpaTest {

    private static final Path FICHIER = Path.of("docs/data/TALENT_360_BANK_Dataset_V1.xlsx");

    @Autowired
    private ImportService importService;
    @Autowired
    private CollaborateurRepository collaborateurRepository;
    @Autowired
    private SuccesseursIdentifiesEnBase successeurs;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private EntiteRepository entiteRepository;
    @Autowired
    private PosteRepository posteRepository;

    static boolean classeurPresent() {
        return Files.exists(FICHIER);
    }

    @Test
    void le_classeur_reel_s_importe_sans_erreur() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", FICHIER.getFileName().toString(), null,
                Files.readAllBytes(FICHIER));

        // Le classeur est date du 15/09/2026 (00_DASHBOARD A2) : refuse pour un autre trimestre.
        ResultatImport autrePeriode = importService.importer(fichier, 2026, 4);
        assertThat(autrePeriode.statut()).isEqualTo(StatutImport.ECHEC);
        assertThat(autrePeriode.message()).contains("T3 2026", "00_DASHBOARD A2");

        ResultatImport resultat = importService.importer(fichier, 2026, 3);

        assertThat(resultat.erreurs()).isEmpty();
        assertThat(resultat.statut()).isEqualTo(StatutImport.SUCCES);
        Map<String, Integer> lignes = resultat.feuilles().stream()
                .collect(Collectors.toMap(BilanFeuille::feuille, BilanFeuille::nbLignes));
        assertThat(lignes).containsExactlyInAnyOrderEntriesOf(Map.of(
                FEUILLE_COLLABORATEURS, 100,
                FEUILLE_COMPETENCES, 25,        // la legende 1-5 sous le referentiel n'est pas importee
                FEUILLE_SKILLS, 2500,
                FEUILLE_POSTES, 18,
                FEUILLE_POSTES_CRITIQUES, 15,
                FEUILLE_PERFORMANCE, 100,
                FEUILLE_POTENTIEL, 100,
                FEUILLE_SUCCESSION, 44,         // la note sur PST13 sous la liste n'est pas importee
                FEUILLE_TALENTS, 100,
                FEUILLE_VIGILANCE, 100));

        assertThat(collaborateurRepository.findAll()).filteredOn(e -> e.getManager() != null).hasSize(81);
        // Un Manager par Manager_ID distinct de la colonne N.
        assertThat(managerRepository.findAll()).hasSize(38);

        // Organigramme : une entite par chemin distinct des colonnes H a K.
        assertThat(entiteRepository.findByType(TypeEntite.DIRECTION)).hasSize(10);
        assertThat(entiteRepository.findByType(TypeEntite.DEPARTEMENT)).hasSize(25);
        assertThat(entiteRepository.findByType(TypeEntite.REGION)).hasSize(41);
        assertThat(entiteRepository.findByType(TypeEntite.AGENCE)).hasSize(56);
        assertThat(collaborateurRepository.findAll()).allSatisfy(collaborateur -> {
            assertThat(collaborateur.getEntite().getType()).isEqualTo(TypeEntite.AGENCE);
            assertThat(collaborateur.getDirection()).isNotNull();
        });
        // Les directions des postes (07 C) sont celles des collaborateurs.
        assertThat(posteRepository.findAll()).allSatisfy(poste ->
                assertThat(poste.getEntite().getType()).isEqualTo(TypeEntite.DIRECTION));
        // PST13 n'a volontairement aucun successeur (cas d'alerte du classeur).
        assertThat(successeurs.successeursIdentifies("PST13")).isEmpty();
        assertThat(successeurs.successeursIdentifies("PST01")).hasSize(4);
    }
}
