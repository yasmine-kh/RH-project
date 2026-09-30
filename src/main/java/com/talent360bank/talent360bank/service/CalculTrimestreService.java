package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Enchaine le calcul complet d'un trimestre apres un import : scores, puis
 * placement 9-box (qui lit les scores), puis vivier de releve (qui lit les
 * scores et les cases).
 *
 * <p>Volontairement sans transaction propre : chaque etape garde la sienne,
 * comme quand elle est appelee seule. Si une etape echoue, les precedentes
 * restent enregistrees et l'appel peut etre rejoue sans risque, chaque etape
 * remplacant ce qu'elle avait ecrit.
 */
@Service
public class CalculTrimestreService {

    private final ScoreService scoreService;
    private final NeufBoxService neufBoxService;
    private final VivierReleveService vivierReleveService;

    public CalculTrimestreService(ScoreService scoreService, NeufBoxService neufBoxService,
                                  VivierReleveService vivierReleveService) {
        this.scoreService = scoreService;
        this.neufBoxService = neufBoxService;
        this.vivierReleveService = vivierReleveService;
    }

    public ResultatCalculTrimestre calculer(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        ResultatRecalcul scores = scoreService.recalculerTrimestre(trimestre);
        ResultatRecalcul placements = neufBoxService.placerTrimestre(trimestre);
        ResultatConstitutionVivier releve = vivierReleveService.constituerViviers(trimestre);
        return new ResultatCalculTrimestre(scores, placements, releve);
    }
}
