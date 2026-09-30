package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisions du Comite Talent lues en base (ValidationComite, rempli par
 * l'import de 10_TALENTS colonne G). Sans ligne, la decision est en attente.
 *
 * <p>{@link Fallback} : une autre implementation declaree passe devant.
 */
@Component
@Fallback
public class ValidationsComiteEnBase implements ValidationComiteSource {

    private final ValidationComiteRepository repository;

    public ValidationsComiteEnBase(ValidationComiteRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public StatutValidationComite statut(String idCollaborateur, Trimestre trimestre) {
        return repository.findStatut(idCollaborateur, trimestre).orElse(StatutValidationComite.EN_ATTENTE);
    }
}
