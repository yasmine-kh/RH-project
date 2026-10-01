package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.RattachementVivier;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        return repository.findByDirection(direction.trim()).map(RattachementVivier::getVivier);
    }
}
