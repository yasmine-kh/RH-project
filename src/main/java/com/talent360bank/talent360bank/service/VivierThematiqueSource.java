package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.service.enums.VivierThematique;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rattachement des directions aux viviers thematiques (10_TALENTS : Reseau
 * Retail et Corporate Banking au vivier Commercial, RH au vivier Management...).
 *
 * <p>Les noms de direction sont une donnee, pas une regle : ils ne sont pas
 * codes en dur dans le moteur. Contrat cote moteur, sans dependance a la
 * couche de persistance : le depot du futur referentiel direction / vivier
 * l'implemente ou s'y adapte. Tant qu'aucune implementation n'est declaree,
 * {@link VivierThematiqueService} ne classe personne.
 */
public interface VivierThematiqueSource {

    /** Vivier de la direction, vide si elle n'est rattachee a aucun. */
    Optional<VivierThematique> vivierPourDirection(String direction);

    /**
     * Vivier de chacune de ces directions, par direction telle que donnee ; une
     * direction sans vivier est absente de la map. Par defaut, une lecture par
     * direction ; une implementation en base peut tout lire en une requete.
     */
    default Map<String, VivierThematique> viviersParDirection(Collection<String> directions) {
        Map<String, VivierThematique> viviers = new HashMap<>();
        for (String direction : directions) {
            vivierPourDirection(direction).ifPresent(vivier -> viviers.put(direction, vivier));
        }
        return viviers;
    }
}
