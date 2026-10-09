package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.RattachementVivier;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Rattachement direction / vivier thematique lu en base (RattachementVivier,
 * rempli par l'import de 10_TALENTS colonnes C et I).
 *
 * <p>{@link Fallback} : une autre implementation declaree passe devant.
 */
@Component
@Fallback
public class ViviersThematiquesEnBase implements VivierThematiqueSource {

    private final RattachementVivierRepository repository;

    public ViviersThematiquesEnBase(RattachementVivierRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VivierThematique> vivierPourDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Optional.empty();
        }
        return repository.findByDirectionLibelle(direction.trim()).map(RattachementVivier::getVivier);
    }

    /**
     * Toutes les directions en une requete (la table compte une ligne par
     * direction). La comparaison ignore la casse et les accents, comme celle
     * de MySQL (collation utf8mb4 par defaut) dans {@link #vivierPourDirection}.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, VivierThematique> viviersParDirection(Collection<String> directions) {
        Map<String, VivierThematique> parCle = new HashMap<>();
        for (RattachementVivier rattachement : repository.findAllAvecDirection()) {
            if (rattachement.getDirection() != null && rattachement.getDirection().getLibelle() != null) {
                parCle.put(cle(rattachement.getDirection().getLibelle()), rattachement.getVivier());
            }
        }

        Map<String, VivierThematique> viviers = new HashMap<>();
        for (String direction : directions) {
            if (direction != null && !direction.isBlank()) {
                VivierThematique vivier = parCle.get(cle(direction));
                if (vivier != null) {
                    viviers.put(direction, vivier);
                }
            }
        }
        return viviers;
    }

    private static String cle(String direction) {
        return Normalizer.normalize(direction.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}