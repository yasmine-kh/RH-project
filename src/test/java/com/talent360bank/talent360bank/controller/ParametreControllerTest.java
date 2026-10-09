package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import com.talent360bank.talent360bank.config.ProtectionRequetesFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.talent360bank.talent360bank.controller.dto.ParametreForm;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PonderationSources;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.service.CalculTrimestreService;
import com.talent360bank.talent360bank.service.VerrouCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({SecurityConfig.class, VerrouCalculTrimestre.class})
@WithMockUser(roles = "RH")
@WebMvcTest(ParametreController.class)
class ParametreControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ParametreRepository parametreRepository;
    @MockitoBean
    private com.talent360bank.talent360bank.service.JournalService journalService;
    @MockitoBean
    private ChargeurRessources chargeur;
    @MockitoBean
    private CalculTrimestreService calculTrimestreService;
    @Autowired
    private VerrouCalculTrimestre verrou;

    private Trimestre trimestre;
    private Parametre parametre;

    @BeforeEach
    void init() {
        trimestre = new Trimestre();
        trimestre.setNumero(1);
        trimestre.setAnnee(2026);
        parametre = Parametre.parDefaut(trimestre);
        // Recalcul reussi par defaut : 3 collaborateurs scores, 2 places (1 score incomplet).
        when(calculTrimestreService.calculer(any())).thenReturn(new ResultatCalculTrimestre(
                new ResultatRecalcul(List.of(new Score(), new Score(), new Score()), List.of()),
                new ResultatRecalcul(List.of(new Score(), new Score()), List.of()),
                new ResultatConstitutionVivier(0, List.of(), List.of())));
    }

    private ParametreForm formDepuis(Parametre source) {
        return new ParametreForm("Reglages revus",
                source.getPoidsPerformance(), source.getPoidsPotentiel(),
                source.getPoidsSuccession(), source.getPonderationSources(), source.getSeuilsAutoEvaluation(),
                source.getBaremeExperience(),
                source.getBaremeCompetences(),
                source.getSeuilsNeufBox(), source.getSeuilsNeufBoxPotentiel(),
                source.getSeuilsCategoriePerformance(), source.getSeuilsGapCompetence(),
                source.getSeuilsReadiness(), source.getSeuilsCouverture(),
                source.getSeuilsTalent(), source.getPointsVigilance(), source.getSeuilsVigilance());
    }

    @Test
    void la_lecture_aplatit_le_trimestre_et_rend_tous_les_blocs() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        mockMvc.perform(get("/api/trimestres/2026/1/parametre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annee").value(2026))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.poidsSuccession.poidsCompetences").value(25))
                .andExpect(jsonPath("$.baremeExperience.pointsParAnnee").value(8))
                .andExpect(jsonPath("$.baremeExperience.plafond").value(100))
                .andExpect(jsonPath("$.baremeCompetences.pointsParNiveauManquant").value(20))
                .andExpect(jsonPath("$.baremeCompetences.niveauParDefaut").value(3))
                .andExpect(jsonPath("$.seuilsReadiness.seuilReadyNow").value(90))
                .andExpect(jsonPath("$.seuilsCouverture.nbMinSuccesseurs").value(1))
                .andExpect(jsonPath("$.seuilsTalent.seuilHautPotentielPotentiel").value(85))
                .andExpect(jsonPath("$.seuilsTalent.seuilHautPotentielPerformance").value(75))
                .andExpect(jsonPath("$.seuilsVigilance.seuilEngagementFaible").value(60))
                .andExpect(jsonPath("$.pointsVigilance.pointEngagementFaible").value(25))
                .andExpect(jsonPath("$.seuilsNeufBox.seuilEleve").value(85))
                .andExpect(jsonPath("$.seuilsNeufBoxPotentiel.seuilEleve").value(85))
                .andExpect(jsonPath("$.seuilsNeufBoxPotentiel.seuilMoyen").value(70))
                .andExpect(jsonPath("$.seuilsCategoriePerformance.seuilExceptionnelle").value(90))
                .andExpect(jsonPath("$.seuilsCategoriePerformance.seuilARenforcer").value(60))
                .andExpect(jsonPath("$.seuilsGapCompetence.seuilPrioritaire").value(2))
                .andExpect(jsonPath("$.ponderationSources.poidsManager").value(100))
                .andExpect(jsonPath("$.ponderationSources.poidsAuto").value(0))
                .andExpect(jsonPath("$.ponderationSources.sommeValide").value(true))
                .andExpect(jsonPath("$.seuilsAutoEvaluation.seuilEcartImportant").value(15));
    }

    @Test
    void un_trimestre_sans_reglages_rend_404() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(3, 2026)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/trimestres/2026/3/parametre"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erreur").value("ressource_introuvable"));
    }

    @Test
    void la_creation_pose_les_valeurs_par_defaut_de_la_specification() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(parametreRepository.existsByTrimestre(trimestre)).thenReturn(false);
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        mockMvc.perform(post("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poidsPerformance.poidsObjectifs").value(40))
                .andExpect(jsonPath("$.seuilsNeufBox.seuilEleve").value(85));
    }

    @Test
    void creer_des_reglages_deja_presents_rend_409() throws Exception {
        when(chargeur.exigerTrimestre(2026, 1)).thenReturn(trimestre);
        when(parametreRepository.existsByTrimestre(trimestre)).thenReturn(true);

        mockMvc.perform(post("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1"))
                .andExpect(status().isConflict());

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void la_mise_a_jour_remplace_les_blocs() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsTalent().setSeuilPerformance(new BigDecimal("80"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Reglages revus"))
                .andExpect(jsonPath("$.seuilsTalent.seuilPerformance").value(80));
    }

    @Test
    void sans_seuil_de_couverture_la_valeur_en_place_est_conservee() throws Exception {
        parametre.getSeuilsCouverture().setNbMinSuccesseurs(2);
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        // Corps d'un client anterieur au bloc : le champ est absent du JSON.
        ObjectNode corps = objectMapper.valueToTree(formDepuis(Parametre.parDefaut(trimestre)));
        corps.remove("seuilsCouverture");

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Reglages revus"))
                .andExpect(jsonPath("$.seuilsCouverture.nbMinSuccesseurs").value(2));
    }

    @Test
    void un_seuil_de_couverture_fourni_remplace_la_valeur_en_place() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsCouverture().setNbMinSuccesseurs(3);

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsCouverture.nbMinSuccesseurs").value(3));
    }

    @Test
    void une_ponderation_des_sources_fournie_remplace_la_valeur_en_place() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.setPonderationSources(new PonderationSources(new BigDecimal("70"), new BigDecimal("30")));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ponderationSources.poidsManager").value(70))
                .andExpect(jsonPath("$.ponderationSources.poidsAuto").value(30));
        // Comme tout changement de reglages, il relance le calcul du trimestre.
        verify(calculTrimestreService).calculer(any());
    }

    @Test
    void sans_ponderation_des_sources_la_valeur_en_place_est_conservee() throws Exception {
        parametre.setPonderationSources(new PonderationSources(new BigDecimal("80"), new BigDecimal("20")));
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        // Corps d'un client anterieur au bloc.
        ObjectNode corps = objectMapper.valueToTree(formDepuis(Parametre.parDefaut(trimestre)));
        corps.remove("ponderationSources");

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ponderationSources.poidsManager").value(80));
    }

    @Test
    void le_seuil_d_ecart_auto_manager_se_modifie_comme_les_autres_reglages() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsAutoEvaluation().setSeuilEcartImportant(new BigDecimal("20"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsAutoEvaluation.seuilEcartImportant").value(20));
    }

    @Test
    void sans_seuil_d_ecart_auto_manager_la_valeur_en_place_est_conservee() throws Exception {
        parametre.getSeuilsAutoEvaluation().setSeuilEcartImportant(new BigDecimal("12"));
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        ObjectNode corps = objectMapper.valueToTree(formDepuis(Parametre.parDefaut(trimestre)));
        corps.remove("seuilsAutoEvaluation");

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsAutoEvaluation.seuilEcartImportant").value(12));
    }

    @Test
    void un_seuil_d_ecart_auto_manager_au_dela_de_cent_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsAutoEvaluation().setSeuilEcartImportant(new BigDecimal("150"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void une_ponderation_des_sources_qui_ne_fait_pas_cent_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.setPonderationSources(new PonderationSources(new BigDecimal("60"), new BigDecimal("30")));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
        verify(calculTrimestreService, never()).calculer(any());
    }

    @Test
    void un_seuil_de_couverture_fourni_mais_invalide_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsCouverture().setNbMinSuccesseurs(0);

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void un_client_qui_n_envoie_que_seuilsNeufBox_ne_change_que_l_axe_performance() throws Exception {
        parametre.getSeuilsNeufBoxPotentiel().setSeuilEleve(new BigDecimal("80"));
        parametre.getSeuilsNeufBoxPotentiel().setSeuilMoyen(new BigDecimal("65"));
        parametre.getSeuilsCategoriePerformance().setSeuilExceptionnelle(new BigDecimal("95"));
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        // Corps d'un client anterieur aux deux blocs.
        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsNeufBox().setSeuilEleve(new BigDecimal("90"));
        voulu.getSeuilsNeufBox().setSeuilMoyen(new BigDecimal("75"));
        ObjectNode corps = objectMapper.valueToTree(formDepuis(voulu));
        corps.remove("seuilsNeufBoxPotentiel");
        corps.remove("seuilsCategoriePerformance");

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsNeufBox.seuilEleve").value(90))
                .andExpect(jsonPath("$.seuilsNeufBox.seuilMoyen").value(75))
                .andExpect(jsonPath("$.seuilsNeufBoxPotentiel.seuilEleve").value(80))
                .andExpect(jsonPath("$.seuilsNeufBoxPotentiel.seuilMoyen").value(65))
                .andExpect(jsonPath("$.seuilsCategoriePerformance.seuilExceptionnelle").value(95));
    }

    @Test
    void les_seuils_potentiel_et_categories_fournis_remplacent_la_valeur_en_place() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsNeufBoxPotentiel().setSeuilEleve(new BigDecimal("88"));
        voulu.getSeuilsCategoriePerformance().setSeuilSolide(new BigDecimal("72"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsNeufBox.seuilEleve").value(85))
                .andExpect(jsonPath("$.seuilsNeufBoxPotentiel.seuilEleve").value(88))
                .andExpect(jsonPath("$.seuilsCategoriePerformance.seuilSolide").value(72));
    }

    @Test
    void des_seuils_potentiel_inverses_rendent_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsNeufBoxPotentiel().setSeuilMoyen(new BigDecimal("90"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void des_categories_de_performance_non_decroissantes_rendent_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsCategoriePerformance().setSeuilSolide(new BigDecimal("85"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
    }

    // ------------------------------------------------------------ recalcul apres enregistrement

    @Test
    void la_mise_a_jour_recalcule_le_trimestre_et_rend_le_bilan() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(Parametre.parDefaut(trimestre)))))
                .andExpect(status().isOk())
                // Les reglages restent au premier niveau, comme avant ; le bilan s'ajoute.
                .andExpect(jsonPath("$.libelle").value("Reglages revus"))
                .andExpect(jsonPath("$.recalcul.recalcule").value(true))
                .andExpect(jsonPath("$.recalcul.nbCollaborateursScores").value(3))
                .andExpect(jsonPath("$.recalcul.nbPlaces9Box").value(2))
                .andExpect(jsonPath("$.recalcul.dureeMs").isNumber())
                .andExpect(jsonPath("$.recalcul.erreur").isEmpty());

        // Enregistrer, puis recalculer : dans cet ordre.
        InOrder ordre = inOrder(parametreRepository, calculTrimestreService);
        ordre.verify(parametreRepository).save(any(Parametre.class));
        ordre.verify(calculTrimestreService).calculer(trimestre);
    }

    @Test
    void un_recalcul_en_echec_laisse_les_reglages_enregistres_et_le_dit() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));
        when(calculTrimestreService.calculer(any()))
                .thenThrow(new DonneesIncompletesException("Les seuils de la matrice 9-box ne sont pas configures"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(Parametre.parDefaut(trimestre)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Reglages revus"))
                .andExpect(jsonPath("$.recalcul.recalcule").value(false))
                .andExpect(jsonPath("$.recalcul.nbCollaborateursScores").isEmpty())
                .andExpect(jsonPath("$.recalcul.erreur").value(
                        "Réglages enregistrés, mais le recalcul du trimestre a échoué : "
                                + "Les seuils de la matrice 9-box ne sont pas configures. "
                                + "Corrigez les réglages puis enregistrez-les de nouveau pour relancer le calcul."));

        verify(parametreRepository).save(any(Parametre.class));
    }

    @Test
    void une_erreur_inattendue_du_recalcul_ne_rend_pas_500() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));
        when(calculTrimestreService.calculer(any())).thenThrow(new IllegalStateException("detail interne"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(Parametre.parDefaut(trimestre)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recalcul.recalcule").value(false))
                // Le detail technique reste dans le journal.
                .andExpect(jsonPath("$.recalcul.erreur").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Vérifiez les réglages et réessayez."),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detail interne")))));
    }

    @Test
    void des_reglages_invalides_ne_sont_ni_enregistres_ni_recalcules() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsVigilance().setSeuilEleve(new BigDecimal("500"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
        verify(calculTrimestreService, never()).calculer(any());
    }

    @Test
    void pendant_un_recalcul_du_meme_trimestre_la_mise_a_jour_rend_409_sans_rien_enregistrer() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        CountDownLatch tenu = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        Thread autreRecalcul = new Thread(() -> verrou.executer(trimestre, () -> {
            tenu.countDown();
            try {
                liberer.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        autreRecalcul.start();
        try {
            assertThat(tenu.await(10, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(formDepuis(Parametre.parDefaut(trimestre)))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.erreur").value("recalcul_en_cours"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                            "Recalcul déjà en cours pour T1 2026")));
        } finally {
            liberer.countDown();
            autreRecalcul.join(10_000);
        }
        verify(parametreRepository, never()).save(any());
        verify(calculTrimestreService, never()).calculer(any());
    }

    @Test
    void un_bloc_de_poids_qui_ne_fait_pas_cent_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getPoidsPerformance().setPoidsObjectifs(new BigDecimal("50"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"));

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void une_regle_inter_blocs_violee_rend_400_avec_le_detail() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        // Chaque bloc reste valide isolement : seul leur croisement ne l'est
        // pas, un seuil eleve a 500 depassant le total des points attribuables.
        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsVigilance().setSeuilEleve(new BigDecimal("500"));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("reglages_invalides"))
                .andExpect(jsonPath("$.details.length()").value(1));

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void un_bloc_absent_rend_400() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"incomplet\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("corps_invalide"));
    }

    @Test
    void un_corps_illisible_rend_400_et_non_500() throws Exception {
        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ceci n'est pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreur").value("requete_mal_formee"))
                // Le detail de Jackson (classes internes) reste dans le journal.
                .andExpect(jsonPath("$.message").value("Corps de la requête illisible (JSON mal formé ou valeur invalide)"));
    }

    @Test
    void sans_seuil_de_gap_la_valeur_en_place_est_conservee() throws Exception {
        parametre.getSeuilsGapCompetence().setSeuilPrioritaire(3);
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        ObjectNode corps = objectMapper.valueToTree(formDepuis(Parametre.parDefaut(trimestre)));
        corps.remove("seuilsGapCompetence");

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsGapCompetence.seuilPrioritaire").value(3));
    }

    @Test
    void un_seuil_de_gap_fourni_remplace_la_valeur_en_place() throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));
        when(parametreRepository.save(any(Parametre.class))).thenAnswer(appel -> appel.getArgument(0));

        Parametre voulu = Parametre.parDefaut(trimestre);
        voulu.getSeuilsGapCompetence().setSeuilPrioritaire(4);

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(voulu))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilsGapCompetence.seuilPrioritaire").value(4));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void un_seuil_de_gap_hors_de_2_a_4_rend_400(int seuil) throws Exception {
        when(parametreRepository.findByNumeroEtAnnee(1, 2026)).thenReturn(Optional.of(parametre));

        Parametre casse = Parametre.parDefaut(trimestre);
        casse.getSeuilsGapCompetence().setSeuilPrioritaire(seuil);

        mockMvc.perform(put("/api/trimestres/2026/1/parametre").header(ProtectionRequetesFilter.EN_TETE_ECRITURE, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(casse))))
                .andExpect(status().isBadRequest());

        verify(parametreRepository, never()).save(any());
    }

    @Test
    void une_ecriture_sans_l_en_tete_de_protection_est_refusee_avant_le_controleur() throws Exception {
        mockMvc.perform(put("/api/trimestres/2026/1/parametre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formDepuis(Parametre.parDefaut(trimestre)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erreur").value("en_tete_manquant"));

        verify(parametreRepository, never()).findByNumeroEtAnnee(any(Integer.class), any(Integer.class));
        verify(parametreRepository, never()).save(any());
    }
}
