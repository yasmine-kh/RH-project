package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Vigilance d'un employe. Les signaux accompagnent l'indice : c'est eux qui
 * disent au RH sur quoi agir, l'indice seul ne l'indique pas.
 */
public record VigilanceResponse(EmployeResume employe, BigDecimal indice, String niveau,
                                String niveauLibelle, boolean aRisque,
                                List<SignalResponse> signaux) {

    public record SignalResponse(String code, String libelle, boolean detectable) {
    }

    public static VigilanceResponse de(ResultatVigilance resultat) {
        // Set.copyOf ne garantit aucun ordre d'iteration : on retrie sur
        // l'ordre de declaration pour que la reponse soit stable d'un appel a
        // l'autre, sinon l'UI reordonne les signaux sans raison.
        List<SignalResponse> signaux = resultat.signaux().stream()
                .sorted(Comparator.comparing(SignalVigilance::ordinal))
                .map(signal -> new SignalResponse(signal.name(), signal.getLibelle(),
                        signal.estDetectable()))
                .toList();

        return new VigilanceResponse(
                EmployeResume.de(resultat.employe()),
                resultat.indice(),
                resultat.niveau().name(),
                resultat.niveau().getLibelle(),
                resultat.estARisque(),
                signaux);
    }
}
