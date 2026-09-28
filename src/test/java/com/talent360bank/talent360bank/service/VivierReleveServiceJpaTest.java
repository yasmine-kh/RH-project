package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.StatutEmploye;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.Vivier;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.EmployeRepository;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.repository.VivierRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.talent360bank.talent360bank.service.VivierReleveService.CODE_VIVIER_RELEVE;
import static com.talent360bank.talent360bank.service.VivierReleveService.ORIGINE_MOTEUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constitution du vivier de releve sur une vraie base : idempotence, lignes
 * d'une autre origine et autres trimestres preserves.
 */
@DataJpaTest
@Import({VivierReleveService.class, TalentService.class, CalculService.class})
class VivierReleveServiceJpaTest {

    @Autowired
    private VivierReleveService service;
    @Autowired
    private VivierRepository vivierRepository;
    @Autowired
    private AppartenanceVivierRepository appartenanceRepository;
    @Autowired
    private EmployeRepository employeRepository;
    @Autowired
    private ScoreRepository scoreRepository;
    @Autowired
    private TrimestreRepository trimestreRepository;
    @Autowired
    private ParametreRepository parametreRepository;
    @Autowired
    private EntityManager entityManager;

    private Trimestre t2;
    private Trimestre t3;
    private Employe talent;
    private Employe hautPotentiel;
    private Employe horsVivier;

    @BeforeEach
    void init() {
        t2 = trimestre(2);
        t3 = trimestre(3);
        talent = employe("BP001");
        hautPotentiel = employe("BP002");
        horsVivier = employe("BP003");

        // Seuils par defaut : talent = perf >= 85 et pot >= 85 ; HP = pot >= 85 et perf >= 75.
        for (Trimestre trimestre : List.of(t2, t3)) {
            score(talent, trimestre, "90", "90");
            score(hautPotentiel, trimestre, "78", "88");
            score(horsVivier, trimestre, "60", "60");
        }
        synchroniser();
    }

    @Test
    void deux_passages_sur_le_meme_trimestre_rendent_les_memes_lignes() {
        ResultatConstitutionVivier premier = service.constituerViviers(t3);
        synchroniser();
        List<String> apresPremier = membresMoteur(t3);

        ResultatConstitutionVivier second = service.constituerViviers(t3);
        synchroniser();

        assertThat(apresPremier).containsExactlyInAnyOrder("BP001", "BP002");
        assertThat(membresMoteur(t3)).containsExactlyInAnyOrderElementsOf(apresPremier);
        assertThat(appartenanceRepository.count()).isEqualTo(2);
        assertThat(premier.nbRemplaces()).isZero();
        assertThat(second.nbRemplaces()).isEqualTo(2);
        assertThat(second.crees()).hasSize(2);
        assertThat(vivierRepository.findAll()).extracting(Vivier::getCode).containsExactly(CODE_VIVIER_RELEVE);
    }

    @Test
    void un_nouveau_passage_suit_les_scores_du_moment() {
        service.constituerViviers(t3);
        synchroniser();

        Score score = scoreRepository.findByEmployeAndTrimestre(hautPotentiel, t3).orElseThrow();
        score.setScorePotentiel(new BigDecimal("70"));
        synchroniser();

        service.constituerViviers(t3);
        synchroniser();

        assertThat(membresMoteur(t3)).containsExactly("BP001");
    }

    @Test
    void les_lignes_importees_ou_saisies_ne_sont_ni_touchees_ni_doublees() {
        Vivier releve = service.vivierReleve();
        Vivier commercial = vivier("Vivier Commercial");
        AppartenanceVivier importee = appartenance(talent, releve, t3, "IMPORT");
        AppartenanceVivier thematique = appartenance(horsVivier, commercial, t3, "SAISIE_RH");
        synchroniser();

        ResultatConstitutionVivier premier = service.constituerViviers(t3);
        synchroniser();
        service.constituerViviers(t3);
        synchroniser();

        // BP001 est deja dans le vivier par l'import : le moteur ne l'ajoute pas une seconde fois.
        assertThat(premier.dejaPresents()).containsExactly("BP001");
        assertThat(membresMoteur(t3)).containsExactly("BP002");
        assertThat(appartenanceRepository.findById(importee.getIdAppartenance())).get()
                .extracting(AppartenanceVivier::getOrigine).isEqualTo("IMPORT");
        assertThat(appartenanceRepository.findById(thematique.getIdAppartenance())).get()
                .satisfies(ligne -> {
                    assertThat(ligne.getOrigine()).isEqualTo("SAISIE_RH");
                    assertThat(ligne.getVivier().getNomCategorie()).isEqualTo("Vivier Commercial");
                });
        assertThat(appartenanceRepository.count()).isEqualTo(3);
    }

    @Test
    void les_autres_trimestres_ne_sont_pas_touches() {
        service.constituerViviers(t2);
        synchroniser();
        List<Integer> idsT2 = appartenanceRepository.findByTrimestre(t2).stream()
                .map(AppartenanceVivier::getIdAppartenance).toList();

        service.constituerViviers(t3);
        synchroniser();
        service.constituerViviers(t3);
        synchroniser();

        assertThat(appartenanceRepository.findByTrimestre(t2)).extracting(AppartenanceVivier::getIdAppartenance)
                .containsExactlyInAnyOrderElementsOf(idsT2);
        assertThat(membresMoteur(t2)).containsExactlyInAnyOrder("BP001", "BP002");
        assertThat(membresMoteur(t3)).containsExactlyInAnyOrder("BP001", "BP002");
    }

    @Test
    void le_vivier_est_retrouve_par_son_code_meme_renomme() {
        Vivier releve = service.vivierReleve();
        releve.setNomCategorie("Releve 2026");
        synchroniser();

        service.constituerViviers(t3);
        synchroniser();

        assertThat(vivierRepository.count()).isEqualTo(1);
        assertThat(vivierRepository.findByCode(CODE_VIVIER_RELEVE)).get()
                .extracting(Vivier::getNomCategorie).isEqualTo("Releve 2026");
        assertThat(appartenanceRepository.findByTrimestre(t3))
                .allSatisfy(ligne -> assertThat(ligne.getVivier().getCode()).isEqualTo(CODE_VIVIER_RELEVE));
    }

    @Test
    void sans_reglages_rien_n_est_supprime() {
        service.constituerViviers(t3);
        synchroniser();
        parametreRepository.delete(parametreRepository.findByTrimestre(t3).orElseThrow());
        synchroniser();

        assertThatThrownBy(() -> service.constituerViviers(t3)).isInstanceOf(RessourceIntrouvableException.class);
        synchroniser();

        assertThat(membresMoteur(t3)).containsExactlyInAnyOrder("BP001", "BP002");
    }

    // --- outils --------------------------------------------------------------

    private List<String> membresMoteur(Trimestre trimestre) {
        Vivier releve = vivierRepository.findByCode(CODE_VIVIER_RELEVE).orElseThrow();
        return appartenanceRepository.findByTrimestreEtVivier(trimestre, releve).stream()
                .filter(ligne -> ORIGINE_MOTEUR.equals(ligne.getOrigine()))
                .map(ligne -> ligne.getEmploye().getEmployeeId())
                .sorted()
                .toList();
    }

    private void synchroniser() {
        entityManager.flush();
        entityManager.clear();
    }

    private Trimestre trimestre(int numero) {
        Trimestre trimestre = new Trimestre();
        trimestre.setNumero(numero);
        trimestre.setAnnee(2026);
        trimestreRepository.save(trimestre);
        parametreRepository.save(Parametre.parDefaut(trimestre));
        return trimestre;
    }

    private Employe employe(String employeeId) {
        Employe employe = new Employe();
        employe.setEmployeeId(employeeId);
        employe.setNom("Nom" + employeeId);
        employe.setPrenom("Prenom" + employeeId);
        employe.setDateEntree(LocalDate.of(2015, 1, 1));
        employe.setStatut(StatutEmploye.ACTIF);
        return employeRepository.save(employe);
    }

    private void score(Employe employe, Trimestre trimestre, String performance, String potentiel) {
        Score score = new Score();
        score.setEmploye(employe);
        score.setTrimestre(trimestre);
        score.setScorePerformance(new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal(potentiel));
        scoreRepository.save(score);
    }

    private Vivier vivier(String nom) {
        Vivier vivier = new Vivier();
        vivier.setNomCategorie(nom);
        return vivierRepository.save(vivier);
    }

    private AppartenanceVivier appartenance(Employe employe, Vivier vivier, Trimestre trimestre, String origine) {
        AppartenanceVivier appartenance = new AppartenanceVivier();
        appartenance.setEmploye(employe);
        appartenance.setVivier(vivier);
        appartenance.setTrimestre(trimestre);
        appartenance.setOrigine(origine);
        return appartenanceRepository.save(appartenance);
    }
}
