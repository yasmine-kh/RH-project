package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.service.resultat.PlusGrandGap;
import com.talent360bank.talent360bank.service.resultat.ResultatMatching;
import com.talent360bank.talent360bank.service.resultat.ResultatPosteCible;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Poste cible d'un collaborateur : le poste critique ou son matching est le
 * meilleur. Poste aplati, comme dans {@link CouverturePosteResponse}.
 *
 * @param successeurIdentifie vrai si le RH l'a designe successeur de ce poste (09_SUCCESSION)
 * @param plusGrandGap        competence la plus eloignee du niveau requis ; {@code ecart} = 0 sans ecart
 */
public record PosteCibleResponse(CollaborateurResume collaborateur, String posteId, String nomPoste,
                                 String direction, String criticite, BigDecimal scoreMatching,
                                 String readiness, String readinessLibelle, boolean successeurIdentifie,
                                 PlusGrandGap plusGrandGap) {

    public static PosteCibleResponse de(ResultatPosteCible cible, LocalDate dateReference) {
        Poste poste = cible.poste();
        ResultatMatching matching = cible.matching();
        return new PosteCibleResponse(CollaborateurResume.de(cible.collaborateur(), dateReference),
                poste.getPosteId(), poste.getNomPoste(), poste.getDirection(), poste.getCriticite(),
                matching.scoreMatching(), matching.readiness().name(), matching.readiness().getLibelle(),
                cible.successeurIdentifie(), matching.plusGrandGap());
    }
}
