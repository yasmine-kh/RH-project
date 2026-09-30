package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Les trois etapes du calcul, dans l'ordre ou chacune lit ce que la precedente a ecrit. */
class CalculTrimestreServiceTest {

    private ScoreService scoreService;
    private NeufBoxService neufBoxService;
    private VivierReleveService vivierReleveService;
    private CalculTrimestreService service;
    private Trimestre trimestre;

    @BeforeEach
    void init() {
        scoreService = mock(ScoreService.class);
        neufBoxService = mock(NeufBoxService.class);
        vivierReleveService = mock(VivierReleveService.class);
        service = new CalculTrimestreService(scoreService, neufBoxService, vivierReleveService);
        trimestre = new Trimestre();
        trimestre.setAnnee(2026);
        trimestre.setNumero(3);
    }

    @Test
    void enchaine_scores_placement_puis_vivier_et_rend_les_trois_bilans() {
        ResultatRecalcul scores = new ResultatRecalcul(List.of(),
                List.of(new ResultatRecalcul.CollaborateurIgnore("E1", "Aucune note")));
        ResultatRecalcul placements = new ResultatRecalcul(List.of(), List.of());
        ResultatConstitutionVivier releve = new ResultatConstitutionVivier(2, List.of(), List.of("E9"));
        when(scoreService.recalculerTrimestre(trimestre)).thenReturn(scores);
        when(neufBoxService.placerTrimestre(trimestre)).thenReturn(placements);
        when(vivierReleveService.constituerViviers(trimestre)).thenReturn(releve);

        ResultatCalculTrimestre resultat = service.calculer(trimestre);

        InOrder ordre = inOrder(scoreService, neufBoxService, vivierReleveService);
        ordre.verify(scoreService).recalculerTrimestre(trimestre);
        ordre.verify(neufBoxService).placerTrimestre(trimestre);
        ordre.verify(vivierReleveService).constituerViviers(trimestre);
        assertThat(resultat.scores()).isSameAs(scores);
        assertThat(resultat.placements()).isSameAs(placements);
        assertThat(resultat.vivierReleve()).isSameAs(releve);
    }

    @Test
    void une_etape_en_echec_arrete_la_chaine() {
        when(scoreService.recalculerTrimestre(trimestre))
                .thenThrow(new RessourceIntrouvableException("Aucun parametre configure pour T3 2026"));

        assertThatThrownBy(() -> service.calculer(trimestre)).isInstanceOf(RessourceIntrouvableException.class);
        verifyNoInteractions(neufBoxService, vivierReleveService);
    }
}
