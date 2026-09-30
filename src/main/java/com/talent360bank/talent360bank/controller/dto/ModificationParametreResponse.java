package com.talent360bank.talent360bank.controller.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Reponse de PUT /api/trimestres/{annee}/{numero}/parametre : les reglages
 * enregistres, au meme niveau qu'avant (un client qui lisait la reponse de
 * PUT n'a rien a changer), plus le bilan du recalcul sous "recalcul".
 */
public record ModificationParametreResponse(@JsonUnwrapped ParametreResponse parametre,
                                            RecalculReglagesResponse recalcul) {
}
