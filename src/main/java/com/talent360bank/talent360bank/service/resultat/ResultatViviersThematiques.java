package com.talent360bank.talent360bank.service.resultat;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.service.enums.VivierThematique;

import java.util.List;
import java.util.Map;

/**
 * Viviers thematiques d'un trimestre.
 *
 * @param membres    membres de chaque vivier, chaque vivier present (liste
 *                   vide si personne), dans l'ordre de l'enum ; dans un
 *                   vivier, du meilleur au moins bon en performance
 * @param nonClasses collaborateurs dont la direction n'est rattachee a aucun vivier
 */
public record ResultatViviersThematiques(Map<VivierThematique, List<MembreVivierThematique>> membres,
                                         List<Collaborateur> nonClasses) {

    public List<MembreVivierThematique> membresDe(VivierThematique vivier) {
        return membres.getOrDefault(vivier, List.of());
    }
}
